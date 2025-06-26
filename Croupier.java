import java.net.*;
import java.io.*;
import java.util.*;

public class Croupier {
    static DatagramSocket socket;
    static int port = 5000;

    // Beispiel: Karten in einem Deck (z.B. 6 Decks)
    static int anzahlDecks = 6;
    static List<String> karten = new ArrayList<>();

    static void initDeck() {
        String[] kartenTypen = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        karten.clear();
        for (int d = 0; d < anzahlDecks; d++) {
            for (String k : kartenTypen) {
                for (int i = 0; i < 4; i++) {  // 4 Karten pro Typ pro Deck
                    karten.add(k);
                }
            }
        }
        Collections.shuffle(karten);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        initDeck();
        socket = new DatagramSocket(port);
        System.out.println("Croupier gestartet auf Port " + port);

        byte[] buffer = new byte[4096];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String empfangeneNachricht = new String(packet.getData(), 0, packet.getLength()).trim();
            InetAddress senderAdresse = packet.getAddress();
            int senderPort = packet.getPort();

            System.out.println("Empfangen: " + empfangeneNachricht + " von " + senderAdresse + ":" + senderPort);

            // Verarbeite Nachricht und sende Antwort falls nötig
            String antwort = verarbeiteNachricht(empfangeneNachricht, senderAdresse, senderPort);
            if (antwort != null) {
                sendMessage(senderAdresse, senderPort, antwort);
                System.out.println("Gesendet: " + antwort + " an " + senderAdresse + ":" + senderPort);
            }
        }
    }

    static String verarbeiteNachricht(String nachricht, InetAddress addr, int port) {
        try {
            if (nachricht.equalsIgnoreCase("decks")) {
                // Antwort mit Anzahl Karten und komplettes Deck
                StringBuilder sb = new StringBuilder();
                sb.append(anzahlDecks * 52).append(" ");
                for (int i = 0; i < karten.size(); i++) {
                    sb.append(karten.get(i));
                    if (i < karten.size() - 1) sb.append(",");
                }
                return sb.toString();
            } else if (nachricht.startsWith("Einsatz: ")) {
                String betStr = nachricht.substring("Einsatz: ".length()).trim();
                int bet = Integer.parseInt(betStr);
                // Hier kannst du Einsatz verarbeiten, speichern etc.
                System.out.println("Einsatz erhalten: " + bet);
                return "Einsatz bestätigt: " + bet;
            } else if (nachricht.equalsIgnoreCase("start")) {
                System.out.println("Neue Runde gestartet.");
                return "Runde gestartet";
            } else if (nachricht.equalsIgnoreCase("stop")) {
                System.out.println("Croupier wird beendet.");
                System.exit(0);
            } else {
                System.out.println("Unbekannter Befehl: " + nachricht);
                return "Unbekannter Befehl: " + nachricht;
            }
        } catch (NumberFormatException e) {
            return "Fehler: Ungültiger Zahlenwert in der Nachricht";
        }
        return null;
    }

    static void sendMessage(InetAddress address, int port, String message) {
        try {
            byte[] daten = message.getBytes();
            DatagramPacket packet = new DatagramPacket(daten, daten.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Fehler beim Senden der Nachricht: " + e.getMessage());
        }
    }
}
