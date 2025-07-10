import socket
import sys
import re

class Stats:
    def __init__(self, anzahl_decks):
        self.anzahl_decks = anzahl_decks
        self.verbleibende_karten = 52 * anzahl_decks
        self.verbleibendes_deck = {
            '2': 4 * anzahl_decks,
            '3': 4 * anzahl_decks,
            '4': 4 * anzahl_decks,
            '5': 4 * anzahl_decks,
            '6': 4 * anzahl_decks,
            '7': 4 * anzahl_decks,
            '8': 4 * anzahl_decks,
            '9': 4 * anzahl_decks,
            '10': 4 * anzahl_decks,
            'J': 4 * anzahl_decks,
            'Q': 4 * anzahl_decks,
            'K': 4 * anzahl_decks,
            'A': 4 * anzahl_decks
        }
        self.benutzte_karten = []
        self.running_count = 0
        self.true_count = 0
        self.anzahl_runden = 0
        self.anzahl_gewinne_spieler = 0
        self.anzahl_blackjacks = 0
        self.haende_spieler = {"0":[]}
        self.hand_croupier = []

    def get_stats(self):
        return (f"Anzahl Decks: {self.anzahl_decks}\n"
                f"Verbleibendes Deck: {self.verbleibendes_deck}"
                f"Gespielte Karten: {self.benutzte_karten}"
                f"Anzahl Runden: {self.anzahl_runden}"
                f"Siege Spieler: {self.anzahl_gewinne_spieler}"
                f"Blackjacks: {self.anzahl_blackjacks}")


def truecount_berechnen(stats):
    return stats.running_count / stats.anzahl_decks


def receive_lines(s_sock):
    while True:
        line, c_address = s_sock.recvfrom(4096)
        line = line.decode().rstrip()
        return line, c_address


def send_lines(socket, to_ip, to_port, message):
    message = message.rstrip()
    socket.sendto(message.encode(), (to_ip, to_port))


def main():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.bind(("0.0.0.0", port))

        if kontakte_bestaetigen:
            send_lines(s, croupier[0], croupier[1], "bestaetigung")
            antwort, address = receive_lines(s)
            if antwort.lower() != "bestaetigung" and address != croupier:
                print("Croupier hat nicht geantwortet!")
                sys.exit()

            send_lines(s, spieler[0], spieler[1], "bestaetigung")
            antwort, address = receive_lines(s)
            if antwort.lower() != "bestaetigung" and address != spieler:
                print("Spieler hat nicht geantwortet!")
                sys.exit()

        send_lines(s, croupier[0], croupier[1], "decks")
        antwort, _ = receive_lines(s)
        anzahl_decks = int(antwort)
        print(antwort)
        stats = Stats(anzahl_decks)

        print("Ab hier funktioniert der Kartenzähler automatisch auf Anfrage. Kein weiterer Nutzerinput ist nötig.")
        while True:
            try:
                message, sender_address = receive_lines(s)
                print(message)

                if message.lower() == "stop":
                    print("Empfangs-Thread beendet.")
                    sys.exit()

                elif message.lower() == "neues spiel:": #gefolgt mit anzahl_decks
                    anzahl_decks = int(message.removeprefix("start:"))
                    stats = Stats(anzahl_decks)

                elif message.lower() == "neue runde:":
                    gewinner = message.removeprefix("neue runde:")
                    if gewinner == "spieler":
                        stats.anzahl_gewinne_spieler += 1
                    stats.anzahl_runden += 1
                    stats.haende_spieler = {}
                    stats.hand_croupier = []

                elif message.lower() == "tipps" and sender_address == spieler:
                    stats.true_count = str(truecount_berechnen(stats))
                    send_lines(s, spieler[0], spieler[1], stats.true_count)

                elif message.lower() == "statistik":
                    statistik = stats.get_stats()
                    send_lines(s, sender_address[0], sender_address[1], statistik)

                elif message.lower().startswith("ausgespielt:"):  #gefolgt mit an:Karte  zB: spieler:rang
                    ausgeteilt_an, rang_karte = message.removeprefix("ausgespielt:").split(":")
                    if ausgeteilt_an == "spieler":
                        stats.haende_spieler[message[-1]].append(rang_karte)

                    if ausgeteilt_an == "croupier":
                        stats.hand_croupier.append(rang_karte)

                    stats.running_count += karten_count_werte[rang_karte]
                    stats.verbleibendes_deck[rang_karte] -= 1
                    stats.verbleibende_karten -= 1
                    stats.benutzte_karten.append(rang_karte)

            except Exception as e:
                print(f"Es ist ein Fehler im Programm aufgetreten:\n{e}")
                break


def start():
    global spieler
    global croupier
    global port
    print(sys.argv)

    if len(sys.argv) != 6:
        name = sys.argv[0]
        print(f"Usage: \"{name} port dealer_ip dealer_port spieler_ip spieler_port")
        sys.exit()

    port = int(sys.argv[1])
    croupier = (sys.argv[2], int(sys.argv[3]))
    spieler = (sys.argv[4], int(sys.argv[5]))

    main()


if __name__ == '__main__':
    croupier = ()  # (ip, port)
    spieler = ()  # (ip, port)
    num_decks = 0
    port = 0

    kontakte_bestaetigen = False

    karten_count_werte = {
        '2': 1,
        '3': 1,
        '4': 1,
        '5': 1,
        '6': 1,
        '7': 0,
        '8': 0,
        '9': 0,
        '10': -1,
        'J': -1,
        'Q': -1,
        'K': -1,
        'A': -1
    }

    commands = ["send to", "me", "player", "send all", "register"]
    contacts = {}
    start()
