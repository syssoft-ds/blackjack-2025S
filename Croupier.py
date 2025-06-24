import socket
import json
import threading
import time
import random
import argparse 

# --- Card class (unchanged) ---
class Card:
    def __init__(self, suit, rank):
        if suit not in ['S', 'C', 'H', 'D']:
            raise ValueError("Ungültiger Kartentyp (Suit). Muss 'S', 'C', 'H' oder 'D' sein.")
        if rank not in ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A']:
            raise ValueError("Ungültiger Kartenrang (Rank).")

        self.suit = suit
        self.rank = rank

    def __str__(self):
        return f"{self.suit}{self.rank}"

    def __repr__(self):
        return f"Card('{self.suit}', '{self.rank}')"

    def get_value(self):
        if self.rank in ['J', 'Q', 'K']:
            return 10
        elif self.rank == 'A':
            return 11
        else:
            return int(self.rank)

    def to_json(self):
        return str(self)

# --- Croupier Klasse ---
class Croupier:
    def __init__(self, listen_ip, listen_port, num_decks=6, min_players_to_start=1): 
        self.listen_ip = listen_ip
        self.listen_port = listen_port
        self.num_decks = num_decks
        self.deck = []
        self._initialize_deck()
        self.shuffle_deck()

        self.min_players_to_start = min_players_to_start 
        print(f"Croupier wird eine Runde mit mindestens {self.min_players_to_start} Spielern starten.")

        # Updated player storage: player_id -> {'addr': (ip, port), 'nickname': '...', 'hands': [...], 'has_bet_this_round': False}
        self.players = {} 
        self.dealer_hand = []
        self.hidden_dealer_card = None

        self.turn_order = [] 
        self.current_turn_index = -1
        self.game_in_progress = False
        self.players_ready_for_next_round_lock = threading.Lock() 
        self.players_ready_for_next_round = set() 

        self.card_counter_addr = None

        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            self.sock.bind((self.listen_ip, self.listen_port))
        except OSError as e:
            print(f"Fehler: Croupier konnte nicht an {self.listen_ip}:{self.listen_port} binden. {e}")
            exit()
        print(f"Croupier gestartet und lauscht auf {self.listen_ip}:{self.listen_port} mit {self.num_decks} Decks...")

        self.receive_thread = threading.Thread(target=self._listen_for_messages)
        self.receive_thread.daemon = True
        self.receive_thread.start()

    def _initialize_deck(self):
        suits = ['S', 'C', 'H', 'D']
        ranks = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A']
        self.deck = [Card(suit, rank) for _ in range(self.num_decks) for suit in suits for rank in ranks]

    def shuffle_deck(self):
        random.shuffle(self.deck)
        print("Deck wurde gemischt.")

    def _listen_for_messages(self):
        while True:
            try:
                data, addr = self.sock.recvfrom(4096)
                message = json.loads(data.decode('utf-8'))
                self._handle_message(message, addr)
            except json.JSONDecodeError:
                print(f"Fehler beim Decodieren der JSON-Nachricht von {addr}: {data.decode('utf-8', errors='ignore')}")
            except Exception as e:
                print(f"Fehler im Croupier-Empfangsthread: {e}")

    def _send_udp_message(self, target_ip, target_port, message_dict):
        send_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            message_json = json.dumps(message_dict).encode('utf-8')
            send_sock.sendto(message_json, (target_ip, target_port))
        except Exception as e:
            print(f"Croupier: Fehler beim Senden der UDP-Nachricht: {e}")
        finally:
            send_sock.close()

# Nachrichenverarbeitung
    def _handle_message(self, message, sender_addr):
        msg_type = message.get("type")
        payload = message.get("payload", {})

        if msg_type == "bet":
            player_id = payload.get("player_id")
            player_nickname = payload.get("player_nickname", "Unbekannt") # NEW: Get nickname
            amount = payload.get("amount")
            player_listen_addr = tuple(payload.get("player_listen_addr"))
            
            if player_id and amount is not None:
                if player_id not in self.players:
                    self.players[player_id] = {
                        'addr': player_listen_addr, 
                        'nickname': player_nickname, # Store nickname
                        'hands': [{'hand': [], 'bet': 0, 'status': 'waiting', 'can_double': True, 'can_split': False}],
                        'has_bet_this_round': False 
                    }
                    print(f"Neuer Spieler {player_nickname} ({player_id}) registriert von {player_listen_addr}")
                else: # Update nickname if player reconnects/re-bets with a different one (less common, but robust)
                    self.players[player_id]['nickname'] = player_nickname
                
                if self.game_in_progress:
                    print(f"Spieler {player_nickname}: Spiel ist bereits im Gange. Keine neue Wette möglich.")
                    return 
                
                self.players[player_id]['hands'][0]['bet'] = amount
                self.players[player_id]['has_bet_this_round'] = True 
                print(f"Spieler {player_nickname} setzt {amount}.")

                with self.players_ready_for_next_round_lock:
                    self.players_ready_for_next_round.add(player_id)
                    
                    active_bettors_count = len([p_id for p_id, p_data in self.players.items() if p_data.get('has_bet_this_round', False)])
                    
                    if active_bettors_count >= self.min_players_to_start:
                        all_registered_players_have_bet = True
                        for p_id in self.players:
                            if self.players[p_id].get('has_bet_this_round', False) == False:
                                all_registered_players_have_bet = False
                                break

                        if all_registered_players_have_bet and len(self.players) >= self.min_players_to_start:
                            print("Alle benötigten Spieler haben gesetzt. Starte neue Runde!")
                            self.game_in_progress = True
                            self._start_round()
                        else:
                            print(f"Warte auf weitere Spieler oder Wetten. Aktuell: {active_bettors_count} aktive Spieler, {len(self.players)} registrierte Spieler, benötigen {self.min_players_to_start} aktive Spieler.")
                    else:
                        print(f"Warte auf weitere Spieler oder Wetten. Aktuell: {active_bettors_count} aktive Spieler, benötigen {self.min_players_to_start}.")

            else:
                print(f"Ungültige Wettnachricht von {sender_addr}.")

        elif msg_type == "player_action":
            player_id = payload.get("player_id")
            action = payload.get("action")
            
            if self.current_turn_index != -1 and self.current_turn_index < len(self.turn_order):
                current_player_id, current_hand_idx = self.turn_order[self.current_turn_index]
                
                if player_id == current_player_id:
                    player_nickname = self.players[player_id]['nickname'] # Get nickname
                    print(f"Spieler {player_nickname} (Hand {current_hand_idx}) wählt Aktion: {action}")
                    self._process_player_action(player_id, current_hand_idx, action)
                else:
                    player_nickname_sender = self.players[player_id]['nickname'] if player_id in self.players else "Unbekannt"
                    current_player_nickname_turn = self.players[current_player_id]['nickname'] if current_player_id in self.players else "Unbekannt"
                    print(f"Croupier: Ignoriere Aktion von Spieler {player_nickname_sender} - nicht seine/ihre Runde (aktuell {current_player_nickname_turn}).")
            else:
                player_nickname_sender = self.players[player_id]['nickname'] if player_id in self.players else "Unbekannt"
                print(f"Croupier: Ignoriere Aktion von Spieler {player_nickname_sender} - kein aktiver Zug.")

        elif msg_type == "deck_info_request":
            requester_id = payload.get("requester_id")
            requester_addr = tuple(payload.get("requester_listen_addr"))
            if requester_id == "card_counter":
                self.card_counter_addr = requester_addr
                self._send_deck_info_response(requester_addr[0], requester_addr[1])
            else:
                print(f"Unbekannter Anfragender für Deck-Info: {requester_id}")

        else:
            print(f"Unbekannter Nachrichtentyp empfangen: {msg_type}")

    def _start_round(self):
        # Reset hands and 'has_bet_this_round' flag for all players in preparation for the round
        players_to_participate = []
        for player_id in list(self.players.keys()):
            if self.players[player_id].get('has_bet_this_round', False):
                initial_bet = self.players[player_id]['hands'][0]['bet']
                self.players[player_id]['hands'] = [{
                    'hand': [], 
                    'bet': initial_bet, 
                    'status': 'playing', 
                    'can_double': True, 
                    'can_split': False
                }]
                players_to_participate.append(player_id)
            else: 
                print(f"Entferne Spieler {self.players[player_id]['nickname']} ({player_id}) für diese Runde, da keine Wette platziert.")
                del self.players[player_id]

        if not players_to_participate:
            print("Keine Spieler für die Runde. Beende Start der Runde.")
            self.game_in_progress = False
            self._end_round_cleanup() 
            return

        self.dealer_hand = []
        self.hidden_dealer_card = None

        if len(self.deck) < (len(players_to_participate) * 2 + 2) * 0.5: 
            print("Wenig Karten übrig, mische neu.")
            self._initialize_deck()
            self.shuffle_deck()
            if self.card_counter_addr: 
                self._send_udp_message(self.card_counter_addr[0], self.card_counter_addr[1], {"type": "reshuffle"})

        # Initial deal - Build turn_order
        self.turn_order = []
        for player_id in players_to_participate: # Use only players who are participating in this round
            self.turn_order.append((player_id, 0)) 

        self.current_turn_index = -1 

        # Deal cards to players and dealer
        for i in range(2): 
            for player_id, hand_idx in self.turn_order:
                # Ensure player still exists (not removed if they left between bet and start)
                if player_id in self.players:
                    card = self._deal_card()
                    self.players[player_id]['hands'][hand_idx]['hand'].append(card)
                    self._send_cards_dealt_update([card]) 

            if i == 0: 
                dealer_up_card = self._deal_card()
                self.dealer_hand.append(dealer_up_card)
                self._send_cards_dealt_update([dealer_up_card]) 
            else: 
                self.hidden_dealer_card = self._deal_card()
                self.dealer_hand.append(self.hidden_dealer_card)

        # Update can_split flag for initial hands
        for player_id in players_to_participate:
            if player_id in self.players and self.players[player_id]['hands']:
                player_hand = self.players[player_id]['hands'][0]['hand']
                if len(player_hand) == 2 and player_hand[0].rank == player_hand[1].rank:
                    self.players[player_id]['hands'][0]['can_split'] = True
            
        # Send initial hands to players
        for player_id in players_to_participate:
            player_data = self.players[player_id]
            self._send_deal_cards_message(player_id, player_data['hands'][0]['hand'], self.dealer_hand[0])
        
        self._check_for_blackjacks()

    def _deal_card(self):
        if not self.deck:
            print("Deck leer, mische neu.")
            self._initialize_deck()
            self.shuffle_deck()
            if self.card_counter_addr: 
                self._send_udp_message(self.card_counter_addr[0], self.card_counter_addr[1], {"type": "reshuffle"})
        return self.deck.pop(0)

    def _calculate_hand_value(self, hand):
        value = 0
        num_aces = 0
        for card in hand:
            if card is None:
                continue
            if card.rank == 'A':
                num_aces += 1
                value += 11
            else:
                value += card.get_value()

        while value > 21 and num_aces > 0:
            value -= 10
            num_aces -= 1
        return value

    def _send_deal_cards_message(self, player_id, player_hand, dealer_up_card):
        player_addr = self.players[player_id]['addr']
        message = {
            "type": "deal_cards",
            "payload": {
                "player_id": player_id,
                "player_hand": [c.to_json() for c in player_hand],
                "dealer_up_card": dealer_up_card.to_json() if dealer_up_card else None
            }
        }
        self._send_udp_message(player_addr[0], player_addr[1], message)

    def _send_game_update_message(self, player_id_recipient, player_hands_data, dealer_hand_display, current_player_turn_id, current_hand_idx=0):
        player_addr = self.players[player_id_recipient]['addr']
        message = {
            "type": "game_update",
            "payload": {
                "player_id": player_id_recipient,
                "player_hand": player_hands_data[current_hand_idx], 
                "all_player_hands": player_hands_data, 
                "dealer_hand": dealer_hand_display,
                "current_player_turn_id": current_player_turn_id, # NEW
                "current_player_turn_nickname": self.players[current_player_turn_id]['nickname'], # NEW
                "current_hand_index": current_hand_idx 
            }
        }
        self._send_udp_message(player_addr[0], player_addr[1], message)

    def _send_cards_dealt_update(self, cards_dealt):
        if self.card_counter_addr:
            card_strings = [c.to_json() for c in cards_dealt]
            message = {
                "type": "cards_dealt_update",
                "payload": {
                    "cards": card_strings
                }
            }
            self._send_udp_message(self.card_counter_addr[0], self.card_counter_addr[1], message)

    def _send_deck_info_response(self, target_ip, target_port):
        message = {
            "type": "deck_info_response",
            "payload": {
                "num_decks": self.num_decks
            }
        }
        self._send_udp_message(target_ip, target_port, message)

    def _check_for_blackjacks(self):
        dealer_blackjack = (self._calculate_hand_value(self.dealer_hand) == 21 and len(self.dealer_hand) == 2)
        
        for player_id in list(self.players.keys()):
            # Important: iterate over a copy of hands list, as it might change due to splits
            for hand_idx in range(len(self.players[player_id]['hands'])):
                player_hand_data = self.players[player_id]['hands'][hand_idx]
                player_hand = player_hand_data['hand']
                
                if player_hand_data['status'] == 'playing':
                    player_blackjack = (self._calculate_hand_value(player_hand) == 21 and len(player_hand) == 2)

                    if player_blackjack and not dealer_blackjack:
                        print(f"Spieler {self.players[player_id]['nickname']} Hand {hand_idx} hat Blackjack! Auszahlung 1.5x Einsatz.")
                        self._send_game_result(player_id, hand_idx, "blackjack", player_hand_data['bet'] * 2.5)
                        self.players[player_id]['hands'][hand_idx]['status'] = 'done' 
                    elif player_blackjack and dealer_blackjack:
                        print(f"Spieler {self.players[player_id]['nickname']} Hand {hand_idx} und Croupier haben beide Blackjack. Push.")
                        self._send_game_result(player_id, hand_idx, "push", player_hand_data['bet'])
                        self.players[player_id]['hands'][hand_idx]['status'] = 'done' 
                    elif not player_blackjack and dealer_blackjack:
                        self.dealer_hand = [self.dealer_hand[0], self.hidden_dealer_card] 
                        self._send_cards_dealt_update([self.hidden_dealer_card]) 
                        self.hidden_dealer_card = None
                        print(f"Croupier hat Blackjack. Spieler {self.players[player_id]['nickname']} Hand {hand_idx} verliert.")
                        self._send_game_result(player_id, hand_idx, "loss", 0)
                        self.players[player_id]['hands'][hand_idx]['status'] = 'done' 
        
        if dealer_blackjack:
            print("Croupier hat initial Blackjack. Runde endet.")
            if self.hidden_dealer_card and self.card_counter_addr:
                self._send_cards_dealt_update([self.hidden_dealer_card])
                self.hidden_dealer_card = None
            self._end_round()
        else:
            self._next_player_turn()

    def _next_player_turn(self):
        self.current_turn_index += 1
        
        while self.current_turn_index < len(self.turn_order):
            player_id, hand_idx = self.turn_order[self.current_turn_index]
            player_data = self.players.get(player_id)
            
            if player_data and hand_idx < len(player_data['hands']) and player_data['hands'][hand_idx]['status'] == 'playing':
                player_nickname = player_data['nickname']
                print(f"\nAn der Reihe: Spieler {player_nickname} (Hand {hand_idx})")
                dealer_hand_display = [self.dealer_hand[0].to_json(), "HIDDEN"]
                player_all_hands_display = [[c.to_json() for c in h['hand']] for h in player_data['hands']]
                
                # Send update to ALL players, but specify whose turn it is
                for p_id_recipient, p_info_recipient in self.players.items(): 
                    self._send_game_update_message(
                        p_id_recipient, 
                        player_all_hands_display, # Send the actual player's hands to them
                        dealer_hand_display, 
                        player_id, # This is the ID of the player whose turn it is
                        hand_idx # This is the hand index of the player whose turn it is
                    )
                return 
            else:
                if player_data and hand_idx < len(player_data['hands']):
                    player_nickname = player_data['nickname']
                    print(f"Spieler {player_nickname} (Hand {hand_idx}) ist nicht mehr aktiv ({player_data['hands'][hand_idx]['status']}).")
                else:
                    print(f"Spieler {player_id} oder Hand {hand_idx} nicht gefunden oder ungültig. Überspringe.")
                self.current_turn_index += 1

        self._croupier_plays()

    def _process_player_action(self, player_id, hand_idx, action):
        player_data = self.players[player_id]
        player_nickname = player_data['nickname'] # Get nickname
        player_hand_data = player_data['hands'][hand_idx]
        player_hand = player_hand_data['hand']
        current_bet = player_hand_data['bet']

        if action == "H": 
            new_card = self._deal_card()
            player_hand.append(new_card)
            self._send_cards_dealt_update([new_card]) 
            print(f"Spieler {player_nickname} (Hand {hand_idx}) nimmt eine Karte: {new_card}. Hand: {[str(c) for c in player_hand]}")
            
            player_hand_data['can_double'] = False 
            player_hand_data['can_split'] = False 

            if self._calculate_hand_value(player_hand) > 21:
                print(f"Spieler {player_nickname} (Hand {hand_idx}) ist bust! Hand: {[str(c) for c in player_hand]} (Wert: {self._calculate_hand_value(player_hand)})")
                self._send_game_result(player_id, hand_idx, "bust", 0)
                player_hand_data['status'] = 'bust'
                self._next_player_turn()
            else:
                dealer_hand_display = [self.dealer_hand[0].to_json(), "HIDDEN"]
                player_all_hands_display = [[c.to_json() for c in h['hand']] for h in player_data['hands']]
                for p_id_recipient, p_info_recipient in self.players.items():
                    self._send_game_update_message(p_id_recipient, player_all_hands_display, dealer_hand_display, player_id, hand_idx)

        elif action == "S": 
            print(f"Spieler {player_nickname} (Hand {hand_idx}) bleibt stehen. Hand: {[str(c) for c in player_hand]} (Wert: {self._calculate_hand_value(player_hand)})")
            player_hand_data['status'] = 'stand'
            self._next_player_turn()
        
        elif action == "D": 
            if player_hand_data['can_double'] and len(player_hand) == 2:
                player_hand_data['bet'] *= 2
                new_card = self._deal_card()
                player_hand.append(new_card)
                self._send_cards_dealt_update([new_card])
                print(f"Spieler {player_nickname} (Hand {hand_idx}) verdoppelt und nimmt eine Karte: {new_card}. Hand: {[str(c) for c in player_hand]} (Neuer Einsatz: {player_hand_data['bet']})")
                
                if self._calculate_hand_value(player_hand) > 21:
                    print(f"Spieler {player_nickname} (Hand {hand_idx}) ist bust nach Double Down!")
                    self._send_game_result(player_id, hand_idx, "bust", 0)
                    player_hand_data['status'] = 'bust'
                else:
                    player_hand_data['status'] = 'stand' 
                
                self._next_player_turn()
            else:
                print(f"Spieler {player_nickname} (Hand {hand_idx}) kann nicht verdoppeln. Ungültiger Zug.")
                dealer_hand_display = [self.dealer_hand[0].to_json(), "HIDDEN"]
                player_all_hands_display = [[c.to_json() for c in h['hand']] for h in player_data['hands']]
                for p_id_recipient, p_info_recipient in self.players.items():
                    self._send_game_update_message(p_id_recipient, player_all_hands_display, dealer_hand_display, player_id, hand_idx)

        elif action == "P": 
            if player_hand_data['can_split'] and len(player_hand) == 2 and player_hand[0].rank == player_hand[1].rank:
                card1 = player_hand.pop(0)
                card2 = player_hand.pop(0)
                
                player_hand_data['hand'] = [card1]
                player_hand_data['can_double'] = True 
                player_hand_data['can_split'] = False 
                player_hand_data['status'] = 'playing'

                new_hand_idx = len(player_data['hands'])
                player_data['hands'].append({
                    'hand': [card2],
                    'bet': current_bet, 
                    'status': 'playing',
                    'can_double': True,
                    'can_split': False 
                })
                print(f"Spieler {player_nickname} teilt Hand {hand_idx} in zwei neue Hände: Hand {hand_idx} mit {card1} und Hand {new_hand_idx} mit {card2}.")
                
                self.turn_order.insert(self.current_turn_index + 1, (player_id, new_hand_idx))

                new_card1 = self._deal_card()
                player_data['hands'][hand_idx]['hand'].append(new_card1)
                self._send_cards_dealt_update([new_card1])

                new_card2 = self._deal_card()
                player_data['hands'][new_hand_idx]['hand'].append(new_card2)
                self._send_cards_dealt_update([new_card2])

                print(f"Spieler {player_nickname} Hand {hand_idx}: {[str(c) for c in player_data['hands'][hand_idx]['hand']]}")
                print(f"Spieler {player_nickname} Hand {new_hand_idx}: {[str(c) for c in player_data['hands'][new_hand_idx]['hand']]}")

                if card1.rank == 'A':
                    player_data['hands'][hand_idx]['status'] = 'stand'
                    player_data['hands'][new_hand_idx]['status'] = 'stand'
                    print(f"Regel: Bei geteilten Assen nur eine Karte pro Hand. Spieler {player_nickname} Hände {hand_idx} und {new_hand_idx} stehen jetzt automatisch.")
                    self._next_player_turn() 
                else:
                    dealer_hand_display = [self.dealer_hand[0].to_json(), "HIDDEN"]
                    player_all_hands_display = [[c.to_json() for c in h['hand']] for h in player_data['hands']]
                    for p_id_recipient, p_info_recipient in self.players.items():
                        self._send_game_update_message(p_id_recipient, player_all_hands_display, dealer_hand_display, player_id, hand_idx)
            else:
                print(f"Spieler {player_nickname} (Hand {hand_idx}) kann nicht teilen. Ungültiger Zug.")
                dealer_hand_display = [self.dealer_hand[0].to_json(), "HIDDEN"]
                player_all_hands_display = [[c.to_json() for c in h['hand']] for h in player_data['hands']]
                for p_id_recipient, p_info_recipient in self.players.items():
                    self._send_game_update_message(p_id_recipient, player_all_hands_display, dealer_hand_display, player_id, hand_idx)

        elif action == "SURRENDER": 
            if len(player_hand) == 2 and player_hand_data['can_double']: 
                print(f"Spieler {player_nickname} (Hand {hand_idx}) gibt auf (Surrender). Er/Sie verliert die Hälfte des Einsatzes.")
                self._send_game_result(player_id, hand_idx, "surrender", current_bet * 0.5)
                player_hand_data['status'] = 'surrendered'
                self._next_player_turn()
            else:
                print(f"Spieler {player_nickname} (Hand {hand_idx}) kann nicht aufgeben. Ungültiger Zug.")
                dealer_hand_display = [self.dealer_hand[0].to_json(), "HIDDEN"]
                player_all_hands_display = [[c.to_json() for c in h['hand']] for h in player_data['hands']]
                for p_id_recipient, p_info_recipient in self.players.items():
                    self._send_game_update_message(p_id_recipient, player_all_hands_display, dealer_hand_display, player_id, hand_idx)

        else:
            print(f"Unbekannte Aktion '{action}' von Spieler {player_nickname} (Hand {hand_idx}). Ignoriere.")
            dealer_hand_display = [self.dealer_hand[0].to_json(), "HIDDEN"]
            player_all_hands_display = [[c.to_json() for c in h['hand']] for h in player_data['hands']]
            for p_id_recipient, p_info_recipient in self.players.items():
                self._send_game_update_message(p_id_recipient, player_all_hands_display, dealer_hand_display, player_id, hand_idx)


    def _croupier_plays(self):
        print("\nCroupier ist am Zug...")
        
        if self.hidden_dealer_card:
            self.dealer_hand = [self.dealer_hand[0], self.hidden_dealer_card] 
            self._send_cards_dealt_update([self.hidden_dealer_card]) 
            self.hidden_dealer_card = None 

        dealer_score = self._calculate_hand_value(self.dealer_hand)
        print(f"Croupier's Hand: {[str(c) for c in self.dealer_hand]} (Wert: {dealer_score})")

        while dealer_score < 17:
            new_card = self._deal_card()
            self.dealer_hand.append(new_card)
            self._send_cards_dealt_update([new_card]) 
            dealer_score = self._calculate_hand_value(self.dealer_hand)
            print(f"Croupier zieht {new_card}. Neue Hand: {[str(c) for c in self.dealer_hand]} (Wert: {dealer_score})")
            time.sleep(1)

        if dealer_score > 21:
            print("Croupier ist bust!")
            for player_id in self.players:
                for hand_idx, player_hand_data in enumerate(self.players[player_id]['hands']):
                    player_nickname = self.players[player_id]['nickname'] # Get nickname
                    if player_hand_data['status'] in ['playing', 'stand']: 
                        self._send_game_result(player_id, hand_idx, "win", player_hand_data['bet'] * 2)
                    elif player_hand_data['status'] == 'bust':
                        self._send_game_result(player_id, hand_idx, "loss", 0) 
                    elif player_hand_data['status'] == 'surrendered':
                        self._send_game_result(player_id, hand_idx, "surrender", player_hand_data['bet'] * 0.5) 
        else:
            print(f"Croupier bleibt stehen bei {dealer_score}.")
            for player_id in self.players:
                for hand_idx, player_hand_data in enumerate(self.players[player_id]['hands']):
                    player_nickname = self.players[player_id]['nickname'] # Get nickname
                    player_score = self._calculate_hand_value(player_hand_data['hand'])

                    if player_hand_data['status'] == 'bust':
                        self._send_game_result(player_id, hand_idx, "loss", 0)
                    elif player_hand_data['status'] == 'surrendered':
                        self._send_game_result(player_id, hand_idx, "surrender", player_hand_data['bet'] * 0.5) 
                    elif player_score > dealer_score:
                        self._send_game_result(player_id, hand_idx, "win", player_hand_data['bet'] * 2)
                    elif player_score < dealer_score:
                        self._send_game_result(player_id, hand_idx, "loss", 0)
                    else: 
                        self._send_game_result(player_id, hand_idx, "push", player_hand_data['bet'])
        
        self._end_round()

    def _send_game_result(self, player_id, hand_idx, result_type, payout_amount):
        if player_id not in self.players: 
            print(f"Warnung: Versuche Ergebnis an nicht existierenden Spieler {player_id} zu senden.")
            return

        player_addr = self.players[player_id]['addr']
        player_nickname = self.players[player_id]['nickname'] 
        message = {
            "type": "game_result",
            "payload": {
                "player_id": player_id,
                "player_nickname": player_nickname,
                "hand_index": hand_idx, 
                "result": result_type,
                "payout": payout_amount,
                "player_hand": [c.to_json() for c in self.players[player_id]['hands'][hand_idx]['hand']],
                "dealer_hand": [c.to_json() for c in self.dealer_hand]
            }
        }
        self._send_udp_message(player_addr[0], player_addr[1], message)

        if self.card_counter_addr:
            card_counter_message = {
                "type": "game_result",
                "payload": {
                    "player_id": player_id,
                    "hand_index": hand_idx, 
                    "result": result_type
                }
            }
            self._send_udp_message(self.card_counter_addr[0], self.card_counter_addr[1], card_counter_message)


    def _end_round(self):
        print("\nRunde beendet. Sende 'round_ended' an alle Spieler.")
        self.game_in_progress = False
        
        for player_id, player_data in list(self.players.items()):
            player_addr = player_data['addr']
            message = {"type": "round_ended", "payload": {"player_id": player_id}}
            self._send_udp_message(player_addr[0], player_addr[1], message)
            self.players[player_id]['has_bet_this_round'] = False 
            self.players[player_id]['hands'] = [{
                'hand': [], 
                'bet': 0, 
                'status': 'waiting',
                'can_double': True, 
                'can_split': False 
            }]

        with self.players_ready_for_next_round_lock:
            self.players_ready_for_next_round.clear()

        self.turn_order = []
        self.current_turn_index = -1
        print("Warte auf neue Wetten von Spielern...")

    def _end_round_cleanup(self):
        self.game_in_progress = False
        with self.players_ready_for_next_round_lock:
            self.players_ready_for_next_round.clear()
        self.turn_order = []
        self.current_turn_index = -1
        for player_id in self.players:
            self.players[player_id]['has_bet_this_round'] = False
            self.players[player_id]['hands'] = [{
                'hand': [], 
                'bet': 0, 
                'status': 'waiting',
                'can_double': True, 
                'can_split': False 
            }]
        print("Croupier-Status zurückgesetzt. Warte auf neue Wetten.")


# --- Hauptprogramm des Croupiers ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Blackjack Croupier")
    parser.add_argument('--listen_ip', type=str, default='127.0.0.1',
                        help='IP-Adresse, auf der der Croupier lauscht.')
    parser.add_argument('--listen_port', type=int, default=12345,
                        help='Port des Croupiers.')
    parser.add_argument('--num_decks', type=int, default=6,
                        help='Anzahl der Decks im Spiel.')
    parser.add_argument('--min_players_to_start', type=int, default=1, 
                        help='Minimale Anzahl von Spielern, die wetten müssen, um eine Runde zu starten.')

    args = parser.parse_args()

    croupier = Croupier(
        args.listen_ip,
        args.listen_port,
        args.num_decks,
        args.min_players_to_start 
    )

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Croupier wird heruntergefahren.")
        croupier.sock.close()