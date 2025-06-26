import java.net.*;
import java.util.*;

public class Croupier {
    private List<Karte> deck = new ArrayList<>();
    private List<Karte> hand = new ArrayList<>();
    private int anzahlDecks;
    private DatagramSocket socket;

    private InetAddress spielerAdresse;
    private int spielerPort;

    private InetAddress zaehlerAdresse;
    private int zaehlerPort;

    public Croupier(int anzahlDecks, int eigenerPort, String spielerIP, int spielerPort, String zaehlerIP, int zaehlerPort) throws Exception {
        this.anzahlDecks = anzahlDecks;
        this.socket = new DatagramSocket(eigenerPort);

        this.spielerAdresse = InetAddress.getByName(spielerIP);
        this.spielerPort = spielerPort;

        this.zaehlerAdresse = InetAddress.getByName(zaehlerIP);
        this.zaehlerPort = zaehlerPort;

        mischeDecks();
    }

    private void mischeDecks() {
        deck.clear();
        String[] werte = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "Bube", "Dame", "König", "Ass"};
        String[] typen = {"Pik", "Kreuz", "Herz", "Karo"};
        for (int d = 0; d < anzahlDecks; d++) {
            for (String wert : werte) {
                for (String typ : typen) {
                    deck.add(new Karte(wert, typ));
                }
            }
        }
        Collections.shuffle(deck);
    }

    public Karte zieheKarte() {
        if (deck.isEmpty()) {
            mischeDecks();
        }
        return deck.remove(0);
    }

    public void handLeeren() {
        hand.clear();
    }

    private void sendeNachricht(String msg, InetAddress ip, int port) throws Exception {
        System.out.println(ip + ":" + port + " " + msg);
        byte[] data = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
        socket.send(packet);
    }

    private String empfangeNachricht() throws Exception {
        System.out.println("Erwarte Nachricht:");
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        String message = new String(packet.getData());
        System.out.println("Eingegangen: " + message);
        System.out.println(" ");
        return new String(packet.getData(), 0, packet.getLength());
    }

    public int berechneHandWert(List<Karte> hand) {
        int wert = 0, asse = 0;
        for (Karte k : hand) {
            switch (k.getRang()) {
                case "Bube":
                case "Dame":
                case "König":
                    wert += 10; break;
                case "Ass":
                    asse++; wert += 11; break;
                default:
                    wert += Integer.parseInt(k.getRang());
            }
        }
        while (wert > 21 && asse > 0) {
            wert -= 10;
            asse--;
        }
        return wert;
    }

    public boolean istBlackjack(List<Karte> hand) {
        return hand.size() == 2 && berechneHandWert(hand) == 21;
    }

    public int auswerten(List<Karte> spielerHand) {
        int spielerWert = berechneHandWert(spielerHand);
        int croupierWert = berechneHandWert(hand);

        if (spielerWert > 21) return -1;
        if (croupierWert > 21) return 1;
        if (spielerWert > croupierWert) return 1;
        if (spielerWert < croupierWert) return -1;
        return 0;
    }

    public int berechneAuszahlung(List<Karte> spielerHand, int einsatz, int ergebnis) {
        if (ergebnis == 1 && istBlackjack(spielerHand)) {
            return einsatz + einsatz * 3 / 2;
        } else if (ergebnis == 1) {
            return einsatz * 2;
        } else if (ergebnis == 0) {
            return einsatz;
        } else {
            return 0;
        }
    }

    public void sendeDeckStatistik() throws Exception {
        String msg = "DeckSize:" + deck.size();
        sendeNachricht(msg, zaehlerAdresse, zaehlerPort);
    }

    public void sendeDeckAnzahl() throws Exception {
        String msg = String.valueOf(anzahlDecks);
        sendeNachricht(msg, zaehlerAdresse, zaehlerPort);
    }

    public void frageStatistikVomZaehler() throws Exception {
        sendeNachricht("statistik", zaehlerAdresse, zaehlerPort);
        String statistik = empfangeNachricht();
        System.out.println("Statistik vom Kartenzähler: " + statistik);
    }

    public void sendeKarteAnSpieler(Karte karte) throws Exception {
        sendeNachricht(karte.toString(), spielerAdresse, spielerPort);
    }

    public int empfangeEinsatz() throws Exception {
        String msg = empfangeNachricht();
        return Integer.parseInt(msg);
    }

    public void spielSchleife() throws Exception {
        while (true) {
//            System.out.println("Frage nach Statistik.");
//            frageStatistikVomZaehler();

            handLeeren();
            List<Karte> spielerHand = new ArrayList<>();

            sendeNachricht("einsatz", spielerAdresse, spielerPort);
            System.out.println("Warte auf Einsatz");
            int einsatz = empfangeEinsatz();
            System.out.println("Einsatz: " + einsatz);

            Karte k1 = zieheKarte(); spielerHand.add(k1);
            Karte k2 = zieheKarte(); spielerHand.add(k2);
            sendeKarteAnSpieler(k1);
            sendeKarteAnSpieler(k2);

            hand.add(zieheKarte());
            hand.add(zieheKarte());

            while (true) {

                System.out.println("Erwarte aktion des Spielers");
                sendeNachricht("aktion", spielerAdresse, spielerPort);
                String aktion = empfangeNachricht();
                if (aktion.equalsIgnoreCase("Hit")) {
                    System.out.println("Hit wird ausgefuehrt");
                    Karte neu = zieheKarte();
                    spielerHand.add(neu);
                    sendeKarteAnSpieler(neu);
                    if (berechneHandWert(spielerHand) > 21) {
                        break;
                    }
                } else if (aktion.equalsIgnoreCase("Stand")) {
                    break;
                } else if (aktion.equalsIgnoreCase("decks")) {
                    System.out.println("Dem Kartenzaehler wird die Deckanzahl gesendet.");
                    sendeDeckAnzahl();
                }
            }

            // Croupier zieht
            while (berechneHandWert(hand) < 17) {
                hand.add(zieheKarte());
            }

            int ergebnis = auswerten(spielerHand);
            int auszahlung = berechneAuszahlung(spielerHand, einsatz, ergebnis);
            sendeNachricht("Ergebnis:" + ergebnis + ", Auszahlung:" + auszahlung, spielerAdresse, spielerPort);

            sendeDeckStatistik();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.out.println("Usage: java Croupier <Decks> <eigenerPort> <spielerIP> <spielerPort> <zaehlerIP> <zaehlerPort>");
            return;
        }
        try {
            int decks = Integer.parseInt(args[0]);
            int eigenerPort = Integer.parseInt(args[1]);
            String spielerIP = args[2];
            int spielerPort = Integer.parseInt(args[3]);
            String zaehlerIP = args[4];
            int zaehlerPort = Integer.parseInt(args[5]);

            Croupier croupier = new Croupier(decks, eigenerPort, spielerIP, spielerPort, zaehlerIP, zaehlerPort);
            while (true) {
                croupier.spielSchleife();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
