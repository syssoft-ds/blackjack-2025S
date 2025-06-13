import socket
import json
import threading
import time
import uuid
import argparse

# --- Card class ---
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

# --- Player Klasse ---
class Player:
    def __init__(self, listen_ip, listen_port, croupier_ip, croupier_port, card_counter_ip, card_counter_port, initial_capital=1000, nickname=None):
        self.player_id = str(uuid.uuid4())
        self.nickname = nickname if nickname else f"Spieler-{self.player_id[:4]}" # Assign nickname
        self.capital = initial_capital
        self.all_player_hands = []
        self.current_hand_index = 0
        self.current_bet = 0
        self.dealer_up_card = None
        self.recommended_action = None
        self.player_turn_active = False
        self.is_betting_phase = True

        self.listen_ip = listen_ip
        self.listen_port = listen_port
        self.croupier_ip = croupier_ip
        self.croupier_port = croupier_port
        self.card_counter_ip = card_counter_ip
        self.card_counter_port = card_counter_port

        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            self.sock.bind((self.listen_ip, self.listen_port))
        except OSError as e:
            print(f"Fehler: Spieler {self.player_id} konnte nicht an {self.listen_ip}:{self.listen_port} binden. {e}")
            print("Bitte stelle sicher, dass dieser Port nicht bereits von einer anderen Spieler-Instanz verwendet wird.")
            exit()

        print(f"Spieler {self.nickname} ({self.player_id}) gestartet mit {self.capital} Kapital und lauscht auf {self.listen_ip}:{self.listen_port}...")

        self.receive_thread = threading.Thread(target=self._listen_for_messages)
        self.receive_thread.daemon = True
        self.receive_thread.start()

    def _listen_for_messages(self):
        while True:
            try:
                data, addr = self.sock.recvfrom(4096)
                message = json.loads(data.decode('utf-8'))
                self._handle_message(message, addr)
            except json.JSONDecodeError:
                print(f"Fehler beim Decodieren der JSON-Nachricht von {addr}: {data.decode('utf-8', errors='ignore')}")
            except Exception as e:
                print(f"Fehler im Spieler-Empfangsthread: {e}")

# Nachrichtenverarbeitung
    def _handle_message(self, message, sender_addr):
        msg_type = message.get("type")
        payload = message.get("payload", {})

        if msg_type == "deal_cards":
            player_id = payload.get("player_id")
            if player_id == self.player_id:
                self.all_player_hands = [[self._card_from_str(s) for s in payload.get("player_hand", [])]]
                self.current_hand_index = 0
                self.dealer_up_card = self._card_from_str(payload.get("dealer_up_card"))
                self.is_betting_phase = False
                print(f"\n--- Neue Runde ---")
                self._display_current_game_state()
                

        elif msg_type == "game_update":
            player_id = payload.get("player_id")
            if player_id == self.player_id:
                all_player_hands_str = payload.get("all_player_hands", [])
                self.all_player_hands = [[self._card_from_str(s) for s in hand_str_list] for hand_str_list in all_player_hands_str]
                self.current_hand_index = payload.get("current_hand_index", 0)

                dealer_hand_strings = payload.get("dealer_hand", [])
                dealer_display = []
                for s in dealer_hand_strings:
                    if s == "HIDDEN":
                        dealer_display.append("[HIDDEN]")
                    else:
                        dealer_display.append(s)

                print(f"\n--- Spiel-Update ---")
                self._display_current_game_state(dealer_display)

                current_turn_player_id = payload.get("current_player_turn_id")
                current_turn_player_nickname = payload.get("current_player_turn_nickname")

                if current_turn_player_id == self.player_id:
                    self.player_turn_active = True
                    if not self.recommended_action:
                        self.request_action_recommendation()
                else:
                    self.player_turn_active = False
                    print(f"Es ist der Zug von Spieler: {current_turn_player_nickname}")


        elif msg_type == "game_result":
            player_id = payload.get("player_id")
            hand_index = payload.get("hand_index", 0)
            if player_id == self.player_id:
                result = payload.get("result")
                payout = payload.get("payout")
                player_hand_final = [self._card_from_str(s) for s in payload.get("player_hand", [])]
                dealer_hand_final = [self._card_from_str(s) for s in payload.get("dealer_hand", [])]

                self.capital += payout
                print(f"Rundenergebnis für {self.nickname} Hand {hand_index}: {result.upper()}!")
                print(f"Deine Endhand {hand_index}: {[str(c) for c in player_hand_final]} (Wert: {self._calculate_hand_value(player_hand_final)})")
                print(f"Croupier's Endhand: {[str(c) for c in dealer_hand_final]} (Wert: {self._calculate_hand_value(dealer_hand_final)})")
                print(f"Auszahlung: {payout}, Neues Kapital: {self.capital}")

                self.recommended_action = None

                if self.capital <= 0:
                    print("Kapital aufgebraucht. Spiel beendet.")

        elif msg_type == "action_recommendation":
            self.recommended_action = payload.get("recommended_action")
            expected_value = payload.get("expected_value")
            print(f"Empfehlung vom Kartenzähler für Hand {self.current_hand_index}: {self.recommended_action} (EV: {expected_value:.2f})")

        elif msg_type == "statistics_response":
            print(f"Statistiken vom Kartenzähler erhalten: {json.dumps(payload, indent=2)}")
            if self.is_betting_phase:
                print("\nBereit für neue Runde. Platziere Einsatz (oder drücke 'q' zum Beenden).")

        elif msg_type == "round_ended":
            self.is_betting_phase = True
            self._reset_for_new_round()
            print("\nBereit für neue Runde. Platziere Einsatz (oder drücke 'q' zum Beenden).")

        else:
            print(f"Unbekannter Nachrichtentyp empfangen: {msg_type}")

    def _send_udp_message(self, target_ip, target_port, message_dict):
        send_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            message_json = json.dumps(message_dict).encode('utf-8')
            send_sock.sendto(message_json, (target_ip, target_port))
        except Exception as e:
            print(f"Spieler {self.nickname}: Fehler beim Senden der UDP-Nachricht: {e}")
        finally:
            send_sock.close()

    def _card_from_str(self, card_str):
        if card_str is None or card_str == "HIDDEN":
            return None
        suit = card_str[0]
        rank = card_str[1:]
        return Card(suit, rank)

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

    def place_bet(self, amount):
        if amount > self.capital:
            print(f"Nicht genug Kapital für Einsatz von {amount}. Verfügbar: {self.capital}")
            return

        self.current_bet = amount
        bet_message = {
            "type": "bet",
            "payload": {
                "player_id": self.player_id,
                "player_nickname": self.nickname,
                "amount": amount,
                "player_listen_addr": [self.listen_ip, self.listen_port]
            }
        }
        self._send_udp_message(self.croupier_ip, self.croupier_port, bet_message)
        print(f"{self.nickname} platziert Einsatz von {amount}. Warte auf Start der Runde...")

    def send_player_action_to_croupier(self, action):
        action_message = {
            "type": "player_action",
            "payload": {
                "player_id": self.player_id,
                "action": action
            }
        }
        self._send_udp_message(self.croupier_ip, self.croupier_port, action_message)
        self.player_turn_active = False


    def request_action_recommendation(self):
        current_hand = self.all_player_hands[self.current_hand_index]
        player_hand_str = [c.to_json() for c in current_hand]
        dealer_up_card_str = self.dealer_up_card.to_json() if self.dealer_up_card else None

        recommendation_request_message = {
            "type": "recommendation_request",
            "payload": {
                "player_id": self.player_id,
                "player_hand": player_hand_str,
                "dealer_up_card": dealer_up_card_str,
                "player_listen_addr": [self.listen_ip, self.listen_port]
            }
        }
        self._send_udp_message(self.card_counter_ip, self.card_counter_port, recommendation_request_message)

    def request_statistics(self, stat_type="all"):
        stats_request_message = {
            "type": "statistics_request",
            "payload": {
                "requester_id": self.player_id,
                "requester_listen_addr": [self.listen_ip, self.listen_port]
            }
        }
        self._send_udp_message(self.card_counter_ip, self.card_counter_port, stats_request_message)

    def _reset_for_new_round(self):
        self.all_player_hands = []
        self.current_hand_index = 0
        self.current_bet = 0
        self.dealer_up_card = None
        self.recommended_action = None
        self.player_turn_active = False
        self.is_betting_phase = True

    def _display_current_game_state(self, dealer_display_override=None):
        print(f"{self.nickname}'s Kapital: {self.capital}")
        if dealer_display_override:
            print(f"Croupier's Hand: {' '.join(dealer_display_override)}")
        elif self.dealer_up_card:
            print(f"Croupier's Hand: {self.dealer_up_card} [HIDDEN]")
        else:
            print(f"Croupier's Hand: N/A")

        for i, hand in enumerate(self.all_player_hands):
            hand_str = [str(card) for card in hand]
            hand_value = self._calculate_hand_value(hand)
            active_marker = " <--" if i == self.current_hand_index and self.player_turn_active else ""
            print(f"Hand {i}{active_marker}: {' '.join(hand_str)} (Wert: {hand_value})")

# --- Hauptprogramm des Spielers ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Blackjack Spieler")
    parser.add_argument('--listen_ip', type=str, default='127.0.0.1',
                        help='IP-Adresse, auf der der Spieler lauscht.')
    parser.add_argument('--listen_port', type=int, default=12346,
                        help='Port, auf dem der Spieler lauscht.')
    parser.add_argument('--croupier_ip', type=str, default='127.0.0.1',
                        help='IP-Adresse des Croupiers.')
    parser.add_argument('--croupier_port', type=int, default=12345,
                        help='Port des Croupiers.')
    parser.add_argument('--card_counter_ip', type=str, default='127.0.0.1',
                        help='IP-Adresse des Kartenzählers.')
    parser.add_argument('--card_counter_port', type=int, default=12347,
                        help='Port des Kartenzählers.')
    parser.add_argument('--initial_capital', type=int, default=1000,
                        help='Startkapital des Spielers.')
    parser.add_argument('--name', type=str, default=None,
                        help='Optionaler Nickname für den Spieler.')

    args = parser.parse_args()

    current_listen_port = args.listen_port
    player = None
    while player is None:
        try:
            player_nickname = args.name
            if player_nickname is None:
                player_nickname = input(f"Gib einen Nickname für diesen Spieler ein (z.B. Player1): ").strip()
                if not player_nickname:
                    player_nickname = f"AnonPlayer-{current_listen_port}"

            player = Player(
                args.listen_ip,
                current_listen_port,
                args.croupier_ip,
                args.croupier_port,
                args.card_counter_ip,
                args.card_counter_port,
                args.initial_capital,
                nickname=player_nickname
            )
            break
        except SystemExit:
            print(f"Port {current_listen_port} ist belegt. Versuche Port {current_listen_port + 1}...")
            current_listen_port += 1
            if current_listen_port > 65535:
                print("Keine freien Ports gefunden. Beende.")
                exit()
            args.listen_port = current_listen_port


    time.sleep(1)

    print("\n--- Spieler-Hilfe ---")
    print("Commands außerhalb des Zugs:")
    print("  bet <Menge>    - Platziert eine Wette für die nächste Runde.")
    print("  stats          - Zeigt Statistiken vom Kartenzähler an.")
    print("  q              - Beendet das Spiel.")
    print("\nCommands während deines Zugs (empfohlen wird automatisch gesendet, aber du kannst überschreiben):")
    print("  H              - Hit (Zieh eine weitere Karte)")
    print("  S              - Stand (Bleibe stehen)")
    print("  D              - Double Down (Verdopple Einsatz und zieh eine Karte, dann Stand)")
    print("  P              - Split (Teile deine Hand in zwei neue Hände)")
    print("  SURRENDER      - Surrender (Gib auf, verlier die Hälfte deines Einsatzes)")
    print("  AUTO           - Führe die empfohlene Aktion aus.")
    print("--------------------")
    print(f"\nBeginne, indem du 'bet <Menge>' eingibst, {player.nickname}.")


    try:
        while True:
            prompt_text = ""
            if player.is_betting_phase:
                prompt_text = f"{player.nickname}'s Kapital: {player.capital}. Bereit für neue Runde. Befehl? (bet <Menge>, stats, q): "
            elif player.player_turn_active:
                rec_str = f"Empfehlung: {player.recommended_action}. " if player.recommended_action else ""
                prompt_text = f"{player.nickname}'s Hand {player.current_hand_index} ist am Zug. {rec_str}Aktion? (H, S, D, P, SURRENDER, AUTO, stats, q): "
            else:
                prompt_text = "Warte auf meinen Zug... (stats, q): "

            user_input = input(prompt_text).strip().upper()

            if user_input.startswith('BET '):
                if player.is_betting_phase:
                    try:
                        amount = int(user_input.split(' ')[1])
                        if amount > 0:
                            player.place_bet(amount)
                        else:
                            print("Einsatz muss positiv sein.")
                    except (IndexError, ValueError):
                        print("Ungültiger Betrag. Verwende 'bet <Menge>'.")
                else:
                    print("Kann jetzt keine Wette platzieren. Spiel ist im Gange.")
            elif user_input == 'STATS':
                player.request_statistics()
            elif user_input == 'Q':
                break
            elif player.player_turn_active and user_input in ['H', 'S', 'D', 'P', 'SURRENDER']:
                print(f"Manuelle Aktion gesendet für {player.nickname} Hand {player.current_hand_index}: {user_input}")
                player.send_player_action_to_croupier(user_input)
                player.recommended_action = None
            elif player.player_turn_active and user_input == 'AUTO':
                if player.recommended_action:
                    print(f"Führe empfohlene Aktion für {player.nickname} Hand {player.current_hand_index} aus: {player.recommended_action}")
                    player.send_player_action_to_croupier(player.recommended_action)
                    player.recommended_action = None
                else:
                    print("Keine ausstehende Empfehlung, um 'auto' auszuführen.")
            else:
                print("Ungültige Eingabe oder nicht dein Zug. Bitte beachte die Hilfe oben.")

            time.sleep(0.1)
    except KeyboardInterrupt:
        print(f"{player.nickname} wird heruntergefahren.")
    finally:
        if player:
            player.sock.close()