import socket
import json
import time
import sys 

# Globale Konfiguration (Kommandozeilenargumente)
KARTEZAEHLER_LISTEN_IP = '127.0.0.1'  # Wird überschrieben
KARTEZAEHLER_LISTEN_PORT = 0          # Wird überschrieben

CROUPIER_IP = '127.0.0.1'             # Wird überschrieben
CROUPIER_PORT = 0                     # Wird überschrieben

SPIELER_IP = '127.0.0.1'              # Wird überschrieben
SPIELER_PORT = 0                      # Wird überschrieben

# Kartenumwandlung
def _card_from_str_counter(card_str):
    # Umwandlung von Karten-Strings
    if card_str is None or card_str == "HIDDEN":
        return None 
    
   # Karten mit 10 als 'S10', 'H10', etc.
    if len(card_str) > 2 and card_str[1:3] == '10':
        suit = card_str[0]
        rank = '10'
    else:
        suit = card_str[0]
        rank = card_str[1:]

    # Umwandlung von Abkürzungen
    if rank == 'B': rank = 'J' # Bube
    if rank == 'D': 'Q' # Dame
    if rank == 'K': rank = 'K' # König
    if rank == 'A': rank = 'A' # Ass

    return {'suit': suit, 'rank': rank}


class Kartenzaehler:
    #Klasse implementiert die Logik für einen BlackJack-Kartenzähler
    def __init__(self, listen_ip, listen_port, croupier_ip, croupier_port, spieler_ip, spieler_port):
        # Initialisiert eine neue Instanz des Kartenzählers
        # Zustandsvariablen für das Kartenzählen und die Statistik
        self.anzahl_decks = 0           # Anzahl der Decks initialisiert vom Croupier
        self.laufender_zaehlwert = 0    # Der aktuelle "Running Count" (Summe der HI-LO Werte der gespielten Karten)
        self.anzahl_karten_gespielt = 0 # Gesamtanzahl der Karten
        self.historie_karten = []       # Eine Liste der bisher gespielten Karten
        
        # Statistiken pro Spieler
        self.statistik_gewinne = {}       # Zählt Anzahl der gewonnenen Runden pro Spieler-ID
        self.statistik_blackjacks = {}    # Zählt Anzahl der Blackjacks pro Spieler-ID
        self.statistik_runden_gespielt = {} # Zählt Gesamtanzahl der gespielten Runden pro Spieler-ID

        # Netzwerk-Setup
        self.udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM) # Erstellt einen UDP-Socket
        try:
            self.udp_socket.bind((listen_ip, listen_port)) # Bindet den Socket an die angegebene IP und den Port
            print(f"[Kartenzaehler] Lauscht auf {listen_ip}:{listen_port}")
        except OSError as e:
            # Fehlerbehandlung
            print(f"[Kartenzaehler] Fehler beim Binden des Sockets an {listen_ip}:{listen_port}: {e}")
            sys.exit(1) # Beendet das Programm bei einem kritischen Fehler

        self.croupier_addr = (croupier_ip, croupier_port) # Speichert die Adresse des Croupiers
        self.spieler_addr = (spieler_ip, spieler_port)     # Speichert die Adresse des Spielers

        # HI-LO Zählwerte
        self.HI_LO_WERTE = {
            '2': 1, '3': 1, '4': 1, '5': 1, '6': 1,
            '7': 0, '8': 0, '9': 0,
            '10': -1, 'J': -1, 'Q': -1, 'K': -1, 'A': -1,
            'Bube': -1, 'Dame': -1, 'König': -1, 'Ass': -1 # Redundante Einträge
        }

    def karten_verarbeiten(self, karten_str_liste):
        #Verarbeitet eine Liste von Karten-Strings, aktualisiert den laufenden Zählwert und Anzahl der gespielten Karten
        for karte_str in karten_str_liste:
            if karte_str == "HIDDEN":
                # Versteckte Karten werden nicht gezählt
                continue 
            karte_obj = _card_from_str_counter(karte_str) # String in Kartenobjekt um
            if karte_obj and karte_obj['rank'] in self.HI_LO_WERTE:
                # Addiert HI-LO Wert der Karte zum laufenden Zählwert
                self.laufender_zaehlwert += self.HI_LO_WERTE[karte_obj['rank']]
                self.anzahl_karten_gespielt += 1 # Inkrementiert die Anzahl der gespielten Karten
                self.historie_karten.append(karte_obj) # Fügt die Karte der Historie hinzu
        print(f"[Kartenzaehler] Karten verarbeitet. Laufender Zaehlwert: {self.laufender_zaehlwert}, Gespielte Karten: {self.anzahl_karten_gespielt}")

    def true_count_berechnen(self):
        #Berechnet den "True Count" basierend auf dem laufenden Zählwert
        
        if self.anzahl_decks == 0:
            return 0.0 

        gesamt_karten_im_schuh = self.anzahl_decks * 52 # Gesamtzahl der Karten
        verbleibende_karten = gesamt_karten_im_schuh - self.anzahl_karten_gespielt # Anzahl der noch nicht gespielten Karten

        if verbleibende_karten <= 0:
            return 0.0 # Keine Karten mehr übrig
        
        # Schätzung der verbleibenden Decks
        verbleibende_decks_schaetzung = verbleibende_karten / 52.0
        
        if verbleibende_decks_schaetzung < 0.5: 
            verbleibende_decks_schaetzung = 0.5 

        # Der True Count ist der laufende Zählwert
        return self.laufender_zaehlwert / verbleibende_decks_schaetzung

    def aktion_empfehlen(self, spieler_hand_werte, croupier_offene_karte_wert, true_count):        
        #Empfiehlt die optimale Aktion (Hit, Stand, Double Down, Split, Surrender) basierend auf der Basisstrategie des BlackJack und den "Abweichungen" 
      
        hand_sum = sum(spieler_hand_werte) # Summe der Kartenwerte
        
        
        is_pair = len(spieler_hand_werte) == 2 and spieler_hand_werte[0] == spieler_hand_werte[1]

        
        is_soft = False
        if 11 in spieler_hand_werte: # Wenn ein Ass vorhanden ist
            current_sum = hand_sum
            temp_num_aces = spieler_hand_werte.count(11) # Zählt die Anzahl der Asse
            while current_sum > 21 and temp_num_aces > 0:
                current_sum -= 10 # Wandelt ein Ass von 11 in 1 um
                temp_num_aces -= 1
            if current_sum <= 21 and spieler_hand_werte.count(11) > 0:
                is_soft = True
            
            if is_pair and spieler_hand_werte[0] == 11: 
                is_soft = True 

        # Abweichungen von der Basisstrategie basierend auf dem True Count
        if hand_sum == 16 and croupier_offene_karte_wert == 10 and not is_soft and not is_pair: 
            if true_count >= 0:
                return "STAND" 
        
        if hand_sum == 15 and croupier_offene_karte_wert == 10 and not is_soft and not is_pair:
            if true_count >= 4:
                return "STAND" 

        if hand_sum == 10 and croupier_offene_karte_wert == 10 and not is_soft:
            if true_count >= 4:
                return "DOUBLE_DOWN" 

        if hand_sum == 12 and croupier_offene_karte_wert == 2 and not is_soft:
            if true_count >= 3:
                return "STAND" 

        if hand_sum == 12 and croupier_offene_karte_wert == 3 and not is_soft:
            if true_count >= 2:
                return "STAND" 
        
        if hand_sum == 11 and croupier_offene_karte_wert == 11 and not is_soft: 
            if true_count >= 1:
                return "DOUBLE_DOWN" 

        if hand_sum == 9 and croupier_offene_karte_wert == 7 and not is_soft:
            if true_count >= 3:
                return "HIT" 

        if hand_sum == 19 and is_soft and croupier_offene_karte_wert == 4:
            if true_count <= -2:
                return "HIT" 
        
        # 1. Split-Entscheidungen (Basisstrategie für Paare)
        if is_pair:
            pair_rank_value = spieler_hand_werte[0] 

            if pair_rank_value == 11: 
                return "SPLIT" 
            elif pair_rank_value == 8: 
                return "SPLIT" 
            elif pair_rank_value == 2 or pair_rank_value == 3:
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 7:
                    return "SPLIT"
            elif pair_rank_value == 4: 
                if croupier_offene_karte_wert in [5, 6]:
                    return "SPLIT"
            elif pair_rank_value == 5: 
                pass 
            elif pair_rank_value == 6: 
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 6:
                    return "SPLIT"
            elif pair_rank_value == 7: 
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 7:
                    return "SPLIT"
            elif pair_rank_value == 9: 
                if croupier_offene_karte_wert not in [7, 10, 11]: 
                    return "SPLIT"
            elif pair_rank_value == 10: 
                return "STAND" 

        # 2. Harte Entscheidungen (kein Ass)
        if not is_soft:
            if hand_sum >= 17:
                return "STAND"
            elif hand_sum == 16:
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 6:
                    return "STAND"
                elif croupier_offene_karte_wert == 10 or croupier_offene_karte_wert == 11: 
                    return "SURRENDER" 
                else:
                    return "HIT"
            elif hand_sum == 15:
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 6:
                    return "STAND"
                elif croupier_offene_karte_wert == 10 or croupier_offene_karte_wert == 11: 
                    return "SURRENDER" 
                else:
                    return "HIT"
            elif hand_sum == 14 or hand_sum == 13:
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 6:
                    return "STAND"
                else:
                    return "HIT"
            elif hand_sum == 12:
                if croupier_offene_karte_wert >= 4 and croupier_offene_karte_wert <= 6:
                    return "STAND"
                else:
                    return "HIT"
            elif hand_sum == 11:
                return "DOUBLE_DOWN" 
            elif hand_sum == 10:
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 9:
                    return "DOUBLE_DOWN"
                else: 
                    return "HIT"
            elif hand_sum == 9:
                if croupier_offene_karte_wert >= 3 and croupier_offene_karte_wert <= 6:
                    return "DOUBLE_DOWN"
                else:
                    return "HIT"
            else: 
                return "HIT"

        # 3. Weiche Entscheidungen (Ass zählt als 11)
        if is_soft:
            if hand_sum >= 19: 
                return "STAND"
            elif hand_sum == 18: 
                if croupier_offene_karte_wert >= 2 and croupier_offene_karte_wert <= 8:
                    if croupier_offene_karte_wert in [2,7,8]: 
                        return "STAND"
                    elif croupier_offene_karte_wert in [3,4,5,6]: 
                        return "DOUBLE_DOWN"
                    else: 
                        return "HIT" 
                else: 
                    return "HIT"
            elif hand_sum == 17: 
                if croupier_offene_karte_wert >= 3 and croupier_offene_karte_wert <= 6:
                    return "DOUBLE_DOWN"
                else:
                    return "HIT"
            elif hand_sum == 16: 
                if croupier_offene_karte_wert >= 4 and croupier_offene_karte_wert <= 6:
                    return "DOUBLE_DOWN"
                else:
                    return "HIT"
            elif hand_sum == 15: 
                if croupier_offene_karte_wert >= 4 and croupier_offene_karte_wert <= 6:
                    return "DOUBLE_DOWN"
                else:
                    return "HIT"
            elif hand_sum == 14 or hand_sum == 13: 
                if croupier_offene_karte_wert >= 5 and croupier_offene_karte_wert <= 6:
                    return "DOUBLE_DOWN"
                else:
                    return "HIT"
            else: 
                return "HIT"

        return "STAND" 

    def optimale_einsatzhoehe_empfehlen(self, true_count):
        #Empfiehlt die optimale Einsatzhöhe basierend auf dem True Count
        
        BASE_BET_UNIT = 50 

        if true_count <= 0: 
            return BASE_BET_UNIT 
        elif true_count >= 1 and true_count < 2:
            return BASE_BET_UNIT * 1.5 
        elif true_count >= 2 and true_count < 3:
            return BASE_BET_UNIT * 2 
        elif true_count >= 3 and true_count < 4:
            return BASE_BET_UNIT * 3 
        elif true_count >= 4:
            return BASE_BET_UNIT * 4 

        return BASE_BET_UNIT 

    def statistik_aktualisieren(self, spieler_id, gewonnen, hatte_blackjack):
        #Aktualisiert die Gewinnstatistiken für einen bestimmten Spieler
        self.statistik_runden_gespielt[spieler_id] = self.statistik_runden_gespielt.get(spieler_id, 0) + 1
        if gewonnen:
            self.statistik_gewinne[spieler_id] = self.statistik_gewinne.get(spieler_id, 0) + 1
        if hatte_blackjack:
            self.statistik_blackjacks[spieler_id] = self.statistik_blackjacks.get(spieler_id, 0) + 1

    def gib_statistiken_an_croupier(self, spieler_id=None):
        #Gibt die Statistiken für einen bestimmten Spieler oder für alle Spieler zurück
        if spieler_id:
            gewinne = self.statistik_gewinne.get(spieler_id, 0)
            runden = self.statistik_runden_gespielt.get(spieler_id, 0)
            gewinnrate = (gewinne / runden) * 100 if runden > 0 else 0
            return {
                "player_id": spieler_id,
                "gewinne": gewinne,
                "blackjacks": self.statistik_blackjacks.get(spieler_id, 0),
                "runden": runden,
                "gewinnrate": f"{gewinnrate:.2f}%" 
            }
        else:
            all_stats = {}
            # Iteriert über alle Spieler-IDs, für die Runden gespielt wurden, und ruft deren Statistiken ab
            for p_id in self.statistik_runden_gespielt.keys():
                all_stats[p_id] = self.gib_statistiken_an_croupier(p_id) 
            return all_stats

    def reset_runden_statistik(self):
        #Setzt die Zählwerte
        self.laufender_zaehlwert = 0
        self.anzahl_karten_gespielt = 0
        self.historie_karten = []
        print("[Kartenzaehler] Zaehlwerte zurueckgesetzt. Statistiken bleiben bestehen.")

    def _send_udp_message(self, target_ip, target_port, message_dict):
        #Hilfsfunktion zum Senden einer UDP-Nachricht
        try:
            message_json = json.dumps(message_dict).encode('utf-8') 
            self.udp_socket.sendto(message_json, (target_ip, target_port)) 
            print(f"[Kartenzaehler] -> Gesendet an {target_ip}:{target_port}: Typ='{message_dict.get('type')}' Payload={str(message_dict.get('payload', ''))[:50]}...")
        except Exception as e:
            print(f"[Kartenzaehler] Fehler beim Senden an {target_ip}:{target_port}: {e}")

    def empfangen(self):
       
        try:
            data, addr = self.udp_socket.recvfrom(4096) 
            nachricht = json.loads(data.decode('utf-8')) 
            return nachricht, addr
        except socket.timeout:
           
            return None, None
        except json.JSONDecodeError as e:
            print(f"[Kartenzaehler] Fehler beim Decodieren der JSON-Nachricht von {addr}: {e}, Daten: {data.decode('utf-8', errors='ignore')}")
            return None, None
        except Exception as e:
            print(f"[Kartenzaehler] Fehler beim Empfangen von UDP-Nachricht: {e}")
            return None, None

    def verarbeite_eingehende_nachricht(self, nachricht, sender_adresse):
        #Verarbeitet eine empfangene Nachricht basierend auf ihrem Typ und dem Absender
        msg_type = nachricht.get("type")
        payload = nachricht.get("payload", {})

        print(f"[Kartenzaehler] <- Empfangen von {sender_adresse[0]}:{sender_adresse[1]}: Typ='{msg_type}' Payload={payload}")

        if sender_adresse == self.croupier_addr:
            # Nachrichten vom Croupier
            if msg_type == "cards_dealt_to_counter": 
                ausgegebene_karten_str = payload.get("cards", [])
                self.karten_verarbeiten(ausgegebene_karten_str)
                print(f"[Kartenzaehler] Aktueller True Count: {self.true_count_berechnen():.2f}")

            elif msg_type == "game_start_info": 
                self.anzahl_decks = payload.get("num_decks")
                self.reset_runden_statistik()
                print(f"[Kartenzaehler] Spiel initialisiert: {self.anzahl_decks} Decks.")

            elif msg_type == "round_result_for_counter": 
                spieler_id = payload.get("player_id")
                gewonnen = payload.get("won", False)
                hatte_blackjack = payload.get("had_blackjack", False)
                self.statistik_aktualisieren(spieler_id, gewonnen, hatte_blackjack)
                print(f"[Kartenzaehler] Statistik aktualisiert fuer Spieler {spieler_id}: Gewonnen={gewonnen}, Blackjack={hatte_blackjack}")

            elif msg_type == "statistics_request_from_croupier": 
                # Croupier fordert Statistiken an
                spieler_id_angefragt = payload.get("player_id", None) 
                statistiken = self.gib_statistiken_an_croupier(spieler_id_angefragt)
                # Sendet die angeforderten Statistiken zurück an den Croupier
                self._send_udp_message(self.croupier_addr[0], self.croupier_addr[1], {
                    "type": "statistics_response_to_croupier",
                    "payload": statistiken
                })
            elif msg_type == "new_shoe": 
                self.reset_runden_statistik() 
                self.anzahl_decks = payload.get("num_decks", self.anzahl_decks)

        elif sender_adresse == self.spieler_addr: 
            # Nachrichten vom Spieler
            if msg_type == "recommendation_request":
                spieler_hand_str = payload.get("player_hand", []) 
                
                spieler_hand_werte = []
                for s in spieler_hand_str:
                    card_obj = _card_from_str_counter(s)
                    if card_obj:
                        if card_obj['rank'] in ['J', 'Q', 'K']:
                            spieler_hand_werte.append(10)
                        elif card_obj['rank'] == 'A':
                            spieler_hand_werte.append(11)
                        else:
                            spieler_hand_werte.append(int(card_obj['rank']))
                
                dealer_up_card_str = payload.get("dealer_up_card")
                croupier_offene_karte_wert = 0
                if dealer_up_card_str:
                    dealer_card_obj = _card_from_str_counter(dealer_up_card_str)
                    if dealer_card_obj:
                        if dealer_card_obj['rank'] in ['J', 'Q', 'K']:
                            croupier_offene_karte_wert = 10
                        elif dealer_card_obj['rank'] == 'A':
                            croupier_offene_karte_wert = 11
                        else:
                            croupier_offene_karte_wert = int(dealer_card_obj['rank'])

                true_count = self.true_count_berechnen()
                empfehlung = self.aktion_empfehlen(spieler_hand_werte, croupier_offene_karte_wert, true_count)
                
                expected_value = 0.0

                # Sendet die Aktions-Empfehlung zurück an den Spieler
                self._send_udp_message(self.spieler_addr[0], self.spieler_addr[1], {
                    "type": "action_recommendation",
                    "payload": {
                        "recommended_action": empfehlung,
                        "expected_value": expected_value
                    }
                })

            elif msg_type == "bet_request": 
                # Spieler fordert eine Einsatzhöhe
                true_count = self.true_count_berechnen()
                empfohlener_einsatz = self.optimale_einsatzhoehe_empfehlen(true_count)
                # Sendet die Einsatzempfehlung zurück an den Spieler
                self._send_udp_message(self.spieler_addr[0], self.spieler_addr[1], {
                    "type": "bet_recommendation", 
                    "payload": {
                        "recommended_bet": empfohlener_einsatz 
                    }
                })

            elif msg_type == "statistics_request": 
                # Spieler fordert Statistiken an
                statistiken = self.gib_statistiken_an_croupier(payload.get("requester_id", None))
                # Sendet die angeforderten Statistiken zurück an den Spieler
                self._send_udp_message(self.spieler_addr[0], self.spieler_addr[1], {
                    "type": "statistics_response",
                    "payload": statistiken
                })

        else:
            print(f"[Kartenzaehler] Unbekannter Nachrichtentyp '{msg_type}' oder unerwarteter Sender {sender_adresse}")

    def run(self):
        """
        Die Hauptschleife des Kartenzählers. Empfängt kontinuierlich Nachrichten 
        und verarbeitet diese.
        """
        self.udp_socket.settimeout(1.0)
        while True:
            nachricht, sender_adresse = self.empfangen() # Versucht, eine Nachricht zu empfangen
            if nachricht:
                self.verarbeite_eingehende_nachricht(nachricht, sender_adresse) 
            time.sleep(0.01)


# Maintprogramm-Ausführung
if __name__ == "__main__":
    # Prüfen, ob die richtige Anzahl an Kommandozeilenargumenten übergeben wurde
    # Erwartet: Skriptname + 6 Argumente für IPs/Ports = 7 Elemente in sys.argv
    if len(sys.argv) != 7:
        print(f"Nutzung: python Kartenzaehler.py <LISTEN_IP> <LISTEN_PORT> <CROUPIER_IP> <CROUPIER_PORT> <SPIELER_IP> <SPIELER_PORT>")
        print(f"Beispiel: python Kartenzaehler.py 127.0.0.1 12000 127.0.0.1 12001 127.0.0.1 12002")
        sys.exit(1)

    KARTEZAEHLER_LISTEN_IP = sys.argv[1]
    KARTEZAEHLER_LISTEN_PORT = int(sys.argv[2])
    CROUPIER_IP = sys.argv[3]
    CROUPIER_PORT = int(sys.argv[4])
    SPIELER_IP = sys.argv[5]
    SPIELER_PORT = int(sys.argv[6])

    # Erstelle eine Instanz des Kartenzaehlers mit den übergebenen Einstellungen
    kartenzaehler_programm = Kartenzaehler(
        KARTEZAEHLER_LISTEN_IP, KARTEZAEHLER_LISTEN_PORT,
        CROUPIER_IP, CROUPIER_PORT,
        SPIELER_IP, SPIELER_PORT
    )
    kartenzaehler_programm.run() # Startet die Hauptschleife des Kartenzählers