import socket
import json
import random
import sys
import uuid
import time 

#addresses
PLAYER_IP = ''
PLAYER_PORT = 0
CROUPIER_IP = ''
CROUPIER_PORT = 0
COUNTER_IP = ''
COUNTER_PORT = 0
INITIAL_CAPITAL = 0

#global variables
player_id = ""
nickname = ""
player_hand = []
player_capital = INITIAL_CAPITAL
current_bet = 0
game_in_progress = False
dealer_up_card = None #Stores the dealer's visible card
recommended_action = None
player_turn_active = False
is_betting_phase = True

#Socket
sock = None #Will be initialized in __main__

class Card:
    def __init__(self, suit, rank):
        #Suits and ranks should match what the Croupier and Counter expect
        #Assuming suits are 'S', 'C', 'H', 'D' and ranks are '2'-'10', 'J', 'Q', 'K', 'A'
        if suit not in ['S', 'C', 'H', 'D', 'Kreuz', 'Pik', 'Herz', 'Karo']: #Allow German suits for initial compatibility
            raise ValueError(f"Ungültiger Kartentyp (Suit): {suit}. Muss 'S', 'C', 'H', 'D' oder Deutsch sein.")
        if rank not in ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A', 'Bube', 'Dame', 'König', 'Ass']: #Allow German ranks
            raise ValueError(f"Ungültiger Kartenrang (Rank): {rank}.")

        self.suit = self._map_suit_to_short(suit)
        self.rank = self._map_rank_to_short(rank)

    def _map_suit_to_short(self, suit):
        #Maps German suit names to single letters if necessary
        mapping = {'Kreuz': 'C', 'Pik': 'S', 'Herz': 'H', 'Karo': 'D'}
        return mapping.get(suit, suit)

    def _map_rank_to_short(self, rank):
        #Maps German rank names to single letters/numbers if necessary
        mapping = {'Bube': 'J', 'Dame': 'Q', 'König': 'K', 'Ass': 'A'}
        return mapping.get(rank, rank)

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

def _card_from_str(card_str):
    if card_str is None or card_str == "HIDDEN":
        return None
    #Assuming card_str format is "SUITRANK" e.g., "S10", "HA"
    suit = card_str[0]
    rank = card_str[1:]
    return Card(suit, rank)

def _card_from_dict(card_dict):
    if not isinstance(card_dict, dict) or 'suit' not in card_dict or 'rank' not in card_dict:
        return None #Or raise an error
    return Card(card_dict['suit'], card_dict['rank'])


#Helpful functions

#calculate the value based on the cards
def calculate_hand_value(hand):
    value = 0
    num_aces = 0
    for card in hand:
        if card is None: #Handle potential HIDDEN cards or None
            continue
        if card.rank == 'A':
            num_aces += 1
            value += 11
        else:
            value += card.get_value()
    
    #correction for aces if value is above 21
    while value > 21 and num_aces > 0:
        value -= 10
        num_aces -= 1
    return value

#General function to send UDP messages
def _send_udp_message(target_ip, target_port, message_dict):
    global sock
    try:
        message_json = json.dumps(message_dict).encode('utf-8')
        sock.sendto(message_json, (target_ip, target_port))
        print(f"-> Sent to {target_ip}:{target_port}: {message_dict.get('type')} {message_dict.get('payload', '')}")
    except Exception as e:
        print(f"Fehler beim Senden der UDP-Nachricht an {target_ip}:{target_port}: {e}")

#requests a recommendation from counter
def request_recommendation_from_counter():
    global recommended_action

    player_hand_str = [c.to_json() for c in player_hand]
    dealer_up_card_str = dealer_up_card.to_json() if dealer_up_card else None

    recommendation_request_message = {
        "type": "recommendation_request",
        "payload": {
            "player_id": player_id,
            "player_hand": player_hand_str,
            "dealer_up_card": dealer_up_card_str,
            "player_listen_addr": [PLAYER_IP, PLAYER_PORT]
        }
    }
    _send_udp_message(COUNTER_IP, COUNTER_PORT, recommendation_request_message)

#calculates the optimal bet amount
def determine_optimal_bet():
    global player_capital
    if player_capital <= 0:
        print("Kein Kapital mehr übrig. Spiel beendet.")
        return 0

    bet_amount = min(player_capital, 50) #bet maximum 50 or (if total is lower) all remaining capital
    print(f"Berechne optimalen Einsatz: {bet_amount}")
    return bet_amount


#Functions handling incoming messages and game flow
def _handle_message(message, sender_addr):
    global player_hand, player_capital, current_bet, game_in_progress, dealer_up_card, recommended_action, player_turn_active, is_betting_phase, nickname

    msg_type = message.get("type")
    payload = message.get("payload", {})

    print(f"<- Received from {sender_addr[0]}:{sender_addr[1]}: {msg_type} {payload}")

    if msg_type == "deal_cards":
        #This is the start of a new round, Ensure it's for this player
        if payload.get("player_id") == player_id:
            #Convert received card strings to Card objects
            player_hand = [_card_from_str(s) for s in payload.get("player_hand", [])]
            dealer_up_card = _card_from_str(payload.get("dealer_up_card"))
            current_bet = payload.get("bet_amount", current_bet) #Croupier might confirm/adjust bet
            game_in_progress = True
            is_betting_phase = False #No longer in betting phase
            player_turn_active = False #Will be set by game_update

            print(f"\n--- Neues Spiel gestartet ---")
            print(f"Deine Hand: {[str(c) for c in player_hand]} (Wert: {calculate_hand_value(player_hand)})")
            print(f"Croupier's sichtbare Karte: {dealer_up_card}")
            print(f"Dein Einsatz: {current_bet}")
            print(f"Dein Kapital: {player_capital}")
            
            #If no blackjack right at the beginning, wait for YOUR_TURN (via game_update)
            if calculate_hand_value(player_hand) == 21 and len(player_hand) == 2:
                print("BLACKJACK! Warte auf Rundenergebnis.")
            elif calculate_hand_value(player_hand) > 21:
                print("Busted at start! Warte auf Rundenergebnis.")


    elif msg_type == "game_update":
        #This message signals whose turn it is and provides updated hands
        if payload.get("player_id") == player_id:
            all_player_hands_str = payload.get("all_player_hands", [])
            if all_player_hands_str and all_player_hands_str[0]:
                player_hand = [_card_from_str(s) for s in all_player_hands_str[0]]
            
            dealer_hand_strings = payload.get("dealer_hand", [])
            dealer_display = []
            for s in dealer_hand_strings:
                if s == "HIDDEN":
                    dealer_display.append("[HIDDEN]")
                else:
                    dealer_display.append(s)
            
            #Update dealer's up card based on the full dealer hand if available, otherwise keep old
            if dealer_hand_strings and dealer_hand_strings[0] != "HIDDEN":
                dealer_up_card = _card_from_str(dealer_hand_strings[0])


            print(f"\n--- Spiel-Update ---")
            print(f"Deine Hand: {[str(c) for c in player_hand]} (Wert: {calculate_hand_value(player_hand)})")
            print(f"Croupier's Hand: {' '.join(dealer_display)}")
            print(f"Dein Kapital: {player_capital}")

            current_turn_player_id = payload.get("current_player_turn_id")
            current_turn_player_nickname = payload.get("current_player_turn_nickname")

            if current_turn_player_id == player_id:
                player_turn_active = True
                print(f"\n--- Dein Zug ({nickname}) ---")
                if calculate_hand_value(player_hand) < 21:
                    request_recommendation_from_counter() #Request recommendation right away
                else:
                    print(f"Handwert ist {calculate_hand_value(player_hand)}. Keine weiteren Aktionen möglich.")
                    send_player_action_to_croupier("STAND") #Automatically stand if 21 or busted
            else:
                player_turn_active = False
                print(f"Es ist der Zug von Spieler: {current_turn_player_nickname}")


    elif msg_type == "action_recommendation":
        recommended_action = payload.get("recommended_action")
        expected_value = payload.get("expected_value")
        print(f"<- Kartenzähler: Empfehlung erhalten: {recommended_action} (EV: {expected_value:.2f})")
        #If it's our turn and we got a recommendation, we can immediately act
        if player_turn_active:
            handle_your_turn_input_prompt(recommended_action)


    elif msg_type == "game_result":
        if payload.get("player_id") == player_id:
            result = payload.get("result")
            payout = payload.get("payout")
            player_hand_final = [_card_from_str(s) for s in payload.get("player_hand", [])]
            dealer_hand_final = [_card_from_str(s) for s in payload.get("dealer_hand", [])]

            player_capital += payout
            print(f"Rundenergebnis für {nickname}: {result.upper()}!")
            print(f"Deine Endhand: {[str(c) for c in player_hand_final]} (Wert: {calculate_hand_value(player_hand_final)})")
            print(f"Croupier's Endhand: {[str(c) for c in dealer_hand_final]} (Wert: {calculate_hand_value(dealer_hand_final)})")
            print(f"Auszahlung: {payout}, Neues Kapital: {player_capital}")

            #Reset
            recommended_action = None
            game_in_progress = False
            player_turn_active = False
            is_betting_phase = True
            player_hand = []
            dealer_up_card = None
            current_bet = 0

            if player_capital <= 0:
                print("Kapital aufgebraucht. Spiel beendet.")
            print(f"--- Spiel beendet. Aktuelles Kapital: {player_capital} ---\n")

    elif msg_type == "round_ended":
        #This message indicates the end of a round and readiness for a new one.
        is_betting_phase = True
        game_in_progress = False
        player_turn_active = False
        player_hand = []
        dealer_up_card = None
        current_bet = 0
        recommended_action = None
        print("\nBereit für neue Runde. Platziere Einsatz (oder drücke 'q' zum Beenden).")

    elif msg_type == "statistics_response":
        print(f"Statistiken vom Kartenzähler erhalten: {json.dumps(payload, indent=2)}")
        if is_betting_phase:
            print("\nBereit für neue Runde. Platziere Einsatz (oder drücke 'q' zum Beenden).")

    elif msg_type == "reject_bet":
        print(f"Croupier hat den Einsatz abgelehnt: {payload.get('reason')}")
        game_in_progress = False
        is_betting_phase = True #Allow placing another bet
        player_capital += current_bet #Return the rejected bet to capital
        current_bet = 0


    else:
        print(f"Unbekannter Nachrichtentyp empfangen: {msg_type}")


def place_bet(amount):
    global current_bet, player_capital, is_betting_phase
    if amount > player_capital:
        print(f"Nicht genug Kapital für Einsatz von {amount}. Verfügbar: {player_capital}")
        return

    current_bet = amount
    bet_message = {
        "type": "bet",
        "payload": {
            "player_id": player_id,
            "player_nickname": nickname,
            "amount": amount,
            "player_listen_addr": [PLAYER_IP, PLAYER_PORT]
        }
    }
    _send_udp_message(CROUPIER_IP, CROUPIER_PORT, bet_message)
    print(f"{nickname} platziert Einsatz von {amount}. Warte auf Start der Runde...")
    is_betting_phase = False #Once bet is placed, not in betting phase anymore


def send_player_action_to_croupier(action):
    global player_turn_active
    action_message = {
        "type": "player_action",
        "payload": {
            "player_id": player_id,
            "action": action
        }
    }
    _send_udp_message(CROUPIER_IP, CROUPIER_PORT, action_message)
    player_turn_active = False #Player's turn ends after sending an action


def request_statistics():
    stats_request_message = {
        "type": "statistics_request",
        "payload": {
            "requester_id": player_id,
            "requester_listen_addr": [PLAYER_IP, PLAYER_PORT]
        }
    }
    _send_udp_message(COUNTER_IP, COUNTER_PORT, stats_request_message)


def handle_your_turn_input_prompt(rec_action=None):
    global recommended_action, player_capital, current_bet

    print(f"\n--- Dein Zug ({nickname}) ---")
    print(f"Deine Hand: {[str(c) for c in player_hand]} (Wert: {calculate_hand_value(player_hand)})")
    if dealer_up_card:
        print(f"Croupier's sichtbare Karte: {dealer_up_card}")
    
    print("Verfügbare Aktionen: H (Hit), S (Stand), D (Double Down), P (Split), SURRENDER, AUTO")
    if rec_action:
        print(f"Empfohlene Aktion vom Kartenzähler: {rec_action}")
    
    action = None
    if rec_action: #If a recommendation is available, suggest using AUTO or the recommendation directly
        user_choice = input(f"Wähle eine Aktion (H/S/D/P/SURRENDER/AUTO). Empfehlung ist '{rec_action}': ").upper()
        if user_choice == "AUTO":
            action = rec_action
        else:
            action = user_choice
    else:
        action = input("Wähle eine Aktion (H/S/D/P/SURRENDER): ").upper()

    if action == "H":
        send_player_action_to_croupier("HIT")
    elif action == "S":
        send_player_action_to_croupier("STAND")
    elif action == "D":
        #Check conditions for Double Down
        if len(player_hand) == 2 and player_capital >= current_bet * 2:
            send_player_action_to_croupier("DOUBLE_DOWN")
        else:
            print("Double Down ist nur mit 2 Karten und ausreichend Kapital möglich.")
            handle_your_turn_input_prompt(rec_action) #Prompt again
    elif action == "P":
        #Check conditions for Split
        if len(player_hand) == 2 and player_hand[0].rank == player_hand[1].rank and player_capital >= current_bet * 2:
            send_player_action_to_croupier("SPLIT")
        else:
            print("Split ist nur mit zwei Karten des gleichen Rangs und ausreichend Kapital möglich.")
            handle_your_turn_input_prompt(rec_action)
    elif action == "SURRENDER":
        send_player_action_to_croupier("SURRENDER")
    else:
        print("Ungültige Aktion. Bitte wähle erneut.")
        handle_your_turn_input_prompt(rec_action)

    recommended_action = None


#Main Program
def main_player_loop():
    global player_capital, current_bet, game_in_progress, player_id, nickname, sock, is_betting_phase, player_turn_active, recommended_action

    #Generate a unique player ID and a default nickname
    player_id = str(uuid.uuid4())
    nickname = f"Spieler-{player_id[:4]}"

    #Prompt for a nickname
    user_nickname = input(f"Gib einen Nickname für diesen Spieler ein (default: {nickname}): ").strip()
    if user_nickname:
        nickname = user_nickname

    print(f"Spieler {nickname} ({player_id}) gestartet mit Kapital: {player_capital}")
    print(f"Lauscht auf {PLAYER_IP}:{PLAYER_PORT}")
    print(f"Croupier: {CROUPIER_IP}:{CROUPIER_PORT}")
    print(f"Kartenzähler: {COUNTER_IP}:{COUNTER_PORT}")

    print("\n--- Spieler-Hilfe ---")
    print("Commands außerhalb des Zugs:")
    print("  bet <Menge>    - Platziert eine Wette für die nächste Runde.")
    print("  stats          - Zeigt Statistiken vom Kartenzähler an.")
    print("  q              - Beendet das Spiel.")
    print("\nCommands während deines Zugs:")
    print("  H              - Hit (Zieh eine weitere Karte)")
    print("  S              - Stand (Bleibe stehen)")
    print("  D              - Double Down (Verdopple Einsatz und zieh eine Karte, dann Stand)")
    print("  P              - Split (Teile deine Hand in zwei neue Hände)")
    print("  SURRENDER      - Surrender (Gib auf, verlier die Hälfte deines Einsatzes)")
    print("  AUTO           - Führe die empfohlene Aktion aus.")
    print("--------------------")
    print(f"\nBeginne, indem du 'bet <Menge>' eingibst, {nickname}.")

    #Start a separate thread for listening to incoming messages
    #This prevents blocking the main thread while waiting for user input
    def listen_thread():
        while True:
            try:
                data, addr = sock.recvfrom(4096)
                message = json.loads(data.decode('utf-8'))
                _handle_message(message, addr)
            except socket.timeout:
                #This timeout is for the socket in the listening thread.
                #The main loop's input() will handle its own blocking.
                pass
            except json.JSONDecodeError:
                print(f"Fehler beim Dekodieren der JSON-Nachricht von {addr}: {data.decode('utf-8', errors='ignore')}")
            except Exception as e:
                print(f"Ein Fehler im Empfangsthread ist aufgetreten: {e}")

    import threading
    listener = threading.Thread(target=listen_thread, daemon=True)
    listener.start()


    while True:
        prompt_text = ""
        if is_betting_phase:
            prompt_text = f"{nickname}'s Kapital: {player_capital}. Bereit für neue Runde. Befehl? (bet <Menge>, stats, q): "
        elif player_turn_active:
            rec_str = f"Empfehlung: {recommended_action}. " if recommended_action else ""
            prompt_text = f"{nickname}'s Hand ist am Zug. {rec_str}Aktion? (H, S, D, P, SURRENDER, AUTO, stats, q): "
        else:
            prompt_text = "Warte auf meinen Zug... (stats, q): "

        try:
            user_input = input(prompt_text).strip().upper()

            #Handle actions of user
            if user_input.startswith('BET '):
                if is_betting_phase:
                    try:
                        amount = int(user_input.split(' ')[1])
                        if amount > 0:
                            place_bet(amount)
                        else:
                            print("Einsatz muss positiv sein.")
                    except (IndexError, ValueError):
                        print("Ungültiger Betrag. Verwende 'bet <Menge>'.")
                else:
                    print("Kann jetzt keine Wette platzieren. Spiel ist im Gange.")

            elif user_input == 'STATS':
                request_statistics()

            elif user_input == 'Q':
                print("Spiel beendet.")
                break
            
            elif player_turn_active and user_input in ['H', 'S', 'D', 'P', 'SURRENDER']:
                print(f"Manuelle Aktion gesendet für {nickname}: {user_input}")
                send_player_action_to_croupier(user_input)
                recommended_action = None #Clear recommendation after manual action

            elif player_turn_active and user_input == 'AUTO':
                if recommended_action:
                    print(f"Führe empfohlene Aktion für {nickname} aus: {recommended_action}")
                    send_player_action_to_croupier(recommended_action)
                    recommended_action = None
                else:
                    print("Keine ausstehende Empfehlung, um 'auto' auszuführen.")

            else:
                print("Ungültige Eingabe oder nicht dein Zug. Bitte beachte die Hilfe oben.")

            time.sleep(0.1) #Small sleep to prevent busy-waiting too much

        except EOFError: #Handles Ctrl+D or similar
            print("\nEingabe beendet. Spieler wird heruntergefahren.")
            break
        except KeyboardInterrupt:
            print(f"\n{nickname} wird heruntergefahren.")
            break
        except Exception as e:
            print(f"Ein Fehler im Hauptloop ist aufgetreten: {e}")




if __name__ == "__main__":
    if len(sys.argv) != 8:
        print(f"Usage: python Spieler.py <PLAYER_IP> <PLAYER_PORT> <CROUPIER_IP> <CROUPIER_PORT> <COUNTER_IP> <COUNTER_PORT> <INITIAL_CAPITAL>")
        sys.exit()
    
    PLAYER_IP = sys.argv[1]
    PLAYER_PORT = int(sys.argv[2])
    CROUPIER_IP = sys.argv[3]
    CROUPIER_PORT = int(sys.argv[4])
    COUNTER_IP = sys.argv[5]
    COUNTER_PORT = int(sys.argv[6])
    INITIAL_CAPITAL = int(sys.argv[7])
    player_capital = INITIAL_CAPITAL

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.bind((PLAYER_IP, PLAYER_PORT))
        sock.settimeout(1) #Short timeout for the recvfrom in the listener thread
    except OSError as e:
        print(f"Fehler: Spieler konnte nicht an {PLAYER_IP}:{PLAYER_PORT} binden. {e}")
        print("Bitte stelle sicher, dass dieser Port nicht bereits von einer anderen Instanz verwendet wird.")
        sys.exit()

    main_player_loop()

    if sock:
        sock.close()