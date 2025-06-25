import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class KartenzaehlerUDP {

    private final DatagramSocket socket;
    private final int port = 5001;

    private final String name = "kartenzaehler";

    // Croupier, an den sich der Kartenzähler registriert
    private final String croupierIp;
    private final int croupierPort;

    private int runningCount = 0;
    private int gespielteRunden = 0;
    private int gewonneneRunden = 0;
    private int blackjacks = 0;
    private final List<String> verwendeteKarten = new ArrayList<>();

    public KartenzaehlerUDP(String croupierIp, int croupierPort) throws Exception {
        this.croupierIp = croupierIp;
        this.croupierPort = croupierPort;

        socket = new DatagramSocket(port);
        System.out.println("Kartenzähler läuft auf Port " + port);

        sendRegistration(croupierIp, croupierPort);
        listen();
    }

    private void sendRegistration(String croupierIp, int croupierPort) throws IOException {
        InetAddress croupierAddr = InetAddress.getByName(croupierIp);
        String localIp = InetAddress.getLocalHost().getHostAddress();
        int localPort = socket.getLocalPort();

        // register <name> <ip> <port>
        String message = "register " + name + " " + localIp + " " + localPort;

        sendMessage(message, croupierAddr, croupierPort);
        System.out.println("Registrierung gesendet an " + croupierIp + ":" + croupierPort);
    }

    private void sendMessage(String message, InetAddress addr, int port) throws IOException {
        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, addr, port);
        socket.send(packet);
    }

    private void listen() throws Exception {
        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            System.out.println("Empfangen: " + msg);

            String[] parts = msg.trim().split("\\s+");
            InetAddress senderAddr = packet.getAddress();
            int senderPort = packet.getPort();

            if (parts.length == 0) continue;

            switch (parts[0].toLowerCase()) {
                case "karte": // karte <name> <rang> <typ>
                    if (parts.length >= 4) {
                        String rang = parts[2];
                        verarbeiteKarte(rang);
                        verwendeteKarten.add(rang + "-" + parts[3]);
                        System.out.println("Karte verarbeitet: " + rang + " " + parts[3]);
                    }
                    break;

                case "ergebnis": // ergebnis <name> <status>
                    if (parts.length >= 3) {
                        String status = parts[2].toLowerCase();
                        boolean gewonnen = status.equals("gewonnen") || status.equals("blackjack");
                        boolean blackjack = status.equals("blackjack");
                        updateErgebnis(gewonnen, blackjack);
                        System.out.println("Spielergebnis aktualisiert: " + status);
                    }
                    break;

                case "aktion": // aktion <name>
                    if (parts.length >= 4 && parts[1].equalsIgnoreCase("rat")) {
                        int spielerWert = Integer.parseInt(parts[2]);
                        int croupierWert = verarbeiteKartenWert(parts[3]);
                        String rat = empfehlung(spielerWert, croupierWert);
                        sendMessage("antwort rat " + rat, senderAddr, senderPort);
                    } else if (parts.length >= 2 && parts[1].equalsIgnoreCase("statistik")) {
                        String antwort = String.format("antwort statistik Runden=%d Gewonnen=%d Blackjacks=%d Karten=%d",
                                gespielteRunden, gewonneneRunden, blackjacks, verwendeteKarten.size());
                        sendMessage(antwort, senderAddr, senderPort);
                    }
                    break;

                default:
                    System.out.println("Unbekannter Befehl: " + parts[0]);
            }
        }
    }

    private void verarbeiteKarte(String rang) {
        switch (rang) {
            case "2", "3", "4", "5", "6" -> runningCount++;
            case "10", "Bube", "Dame", "König", "Ass" -> runningCount--;
            // 7,8,9 neutral
        }
        System.out.println("Running Count: " + runningCount);
    }

    private void updateErgebnis(boolean gewonnen, boolean blackjack) {
        gespielteRunden++;
        if (gewonnen) gewonneneRunden++;
        if (blackjack) blackjacks++;
    }


    private String empfehlung(int spielerWert, int croupierWert) {
        if (spielerWert <= 11) return "hit";
        if (spielerWert >= 17) return "stand";
        if (spielerWert == 12 && croupierWert >= 4 && croupierWert <= 6) return "stand";
        if (spielerWert >= 13 && croupierWert <= 16 && croupierWert >= 2 && croupierWert <= 6) return "stand";
        return "hit";
    }

    private int verarbeiteKartenWert(String rang) {
        return switch (rang.toLowerCase()) {
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            case "5" -> 5;
            case "6" -> 6;
            case "7" -> 7;
            case "8" -> 8;
            case "9" -> 9;
            case "10", "bube", "dame", "könig" -> 10;
            case "ass" -> 11;
            default -> 0;
        };
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java KartenzaehlerUDP <croupierIp> <croupierPort>");
            return;
        }
        String croupierIp = args[0];
        int croupierPort = Integer.parseInt(args[1]);

        try {
            new KartenzaehlerUDP(croupierIp, croupierPort);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
