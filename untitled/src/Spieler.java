import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Spieler {

    private int kapital;
    private int eigenerPort;
    private List<String> hand;
    private InetAddress croupierAdresse;
    private InetAddress zaehlerAdresse;
    private DatagramSocket socket;
    private int croupierPort;
    private int zaehlerPort;

    public Spieler(int startKapital, int eigenerPort, InetAddress croupierAdresse, int croupierPort, InetAddress zaehlerAdresse, int zaehlerPort) throws SocketException {
        this.kapital = startKapital;
        this.eigenerPort = eigenerPort;
        this.hand = new ArrayList<>();
        this.socket = new DatagramSocket(this.eigenerPort); // WICHTIG: Socket an eigenem Port öffnen
        this.croupierAdresse = croupierAdresse;
        this.croupierPort = croupierPort;
        this.zaehlerAdresse = zaehlerAdresse;
        this.zaehlerPort = zaehlerPort;
    }

    public void hit() {
        sendMessageUDP("Hit", croupierAdresse, croupierPort);
    }

    public void stand() {
        sendMessageUDP("Stand", croupierAdresse, croupierPort);
    }

    public void doubleDown() {
        sendMessageUDP("Double-Down", croupierAdresse, croupierPort);
    }

    public void split() {
        sendMessageUDP("Split", croupierAdresse, croupierPort);
    }

    public void surrender() {
        sendMessageUDP("Surrender", croupierAdresse, croupierPort);
    }

    public void setEinsatz(int betrag) {
        if (betrag > kapital || betrag <= 0) {
            System.out.println("Ungültiger Einsatz. Kapital: " + kapital);
            return;
        }

        kapital -= betrag;
        sendMessageUDP("Einsatz: " + betrag, croupierAdresse, croupierPort);
    }


    public void frageKartenzählerNachAktion() {
        sendMessageUDP("Tipps", zaehlerAdresse, zaehlerPort);
    }

    public void berechneEinsatz(double trueCount) {
        int minimumEinsatz = 10;

        if (trueCount < 1) {
            System.out.println("Empfolener Einsatz: "+ minimumEinsatz);
        }

        int berechneterEinsatz = (int) ((trueCount - 1) * minimumEinsatz);

        // Setze 10% des Kapitals als Maximum
        int maxEinsatz = (int) (this.kapital * 0.10);

        System.out.println("Empfolener Einsatz: "+ Math.max(minimumEinsatz, Math.min(berechneterEinsatz, maxEinsatz)));
    }

    private void sendMessageUDP(String message, InetAddress empfaengerAdresse, int empfaengerPort) {
        int maxVersuche = 3;
        int versuch = 0;
        boolean bestaetigt = false;

        int nachrichtenID = (int) (Math.random() * 100000);
        String messageMitID = message.replaceFirst("\\{", "{\"id\": " + nachrichtenID + ", ");

        while (versuch < maxVersuche && !bestaetigt) {
            try {
                versuch++;
                byte[] buffer = messageMitID.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, empfaengerAdresse, empfaengerPort);
                socket.send(packet);
                System.out.println("→ Gesendet (Versuch " + versuch + "): " + messageMitID);

                // Warte auf ACK
                socket.setSoTimeout(1500); // max. 1,5 Sek.
                byte[] ackBuffer = new byte[512];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(ackPacket);

                String antwort = new String(ackPacket.getData(), 0, ackPacket.getLength(), StandardCharsets.UTF_8);

                if (antwort.contains("\"ack\": true") && antwort.contains("\"id\": " + nachrichtenID)) {
                    System.out.println("ACK erhalten für ID " + nachrichtenID);
                    bestaetigt = true;
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Keine Antwort auf Nachricht-ID " + nachrichtenID + ", wiederhole...");
            } catch (IOException e) {
                System.err.println("Fehler beim Senden: " + e.getMessage());
            }
        }

        if (!bestaetigt) {
            System.err.println("Nachricht mit ID " + nachrichtenID + " konnte nicht bestätigt werden.");
        }
    }

    public void empfangeNachrichten() {
        new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    System.out.println("[Empfangen von " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + "]: " + msg);

                    int idStart = msg.indexOf("\"id\":");
                    if (idStart != -1) {
                        int idEnd = msg.indexOf(",", idStart);
                        if (idEnd == -1) idEnd = msg.indexOf("}", idStart);
                        if (idEnd != -1) {
                            String idStr = msg.substring(idStart + 5, idEnd).trim();
                            try {
                                int id = Integer.parseInt(idStr);

                                // Sende ACK zurück
                                String ackMessage = "{\"ack\": true, \"id\": " + id + "}";
                                byte[] ackData = ackMessage.getBytes(StandardCharsets.UTF_8);
                                DatagramPacket ackPacket = new DatagramPacket(
                                        ackData, ackData.length,
                                        packet.getAddress(), packet.getPort()
                                );
                                socket.send(ackPacket);
                                System.out.println("← ACK gesendet für ID " + id);
                            } catch (NumberFormatException e) {
                                System.out.println("Keine gültige ID gefunden.");
                            }
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Fehler beim Empfang: " + e.getMessage());
                }
            }
        }).start();
    }



    public static void main(String[] args) throws Exception {
        // IPs und Ports anpassen, schneller als immer Terminal
        int eigenerPort = 6000;
        InetAddress croupierIP = InetAddress.getByName("127.0.0.1");
        InetAddress zaehlerIP = InetAddress.getByName("127.0.0.1");
        int croupierPort = 5000;
        int zaehlerPort = 7000;

        Spieler spieler = new Spieler(1000, eigenerPort, croupierIP, croupierPort, zaehlerIP, zaehlerPort);

        // Starte Empfangsthread
        spieler.empfangeNachrichten();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- Spieler-Menü ---");
            System.out.println("1 - Hit");
            System.out.println("2 - Stand");
            System.out.println("3 - Double-Down");
            System.out.println("4 - Split");
            System.out.println("5 - Surrender");
            System.out.println("6 - Einsatz setzen");
            System.out.println("7 - Kartenzähler: beste Aktion?");
            System.out.println("8 - Kapital anzeigen");
            System.out.println("9 - Einsatz berechnen, True Count eingeben");
            System.out.print("Wähle Aktion: ");
            String eingabe = scanner.nextLine();

            switch (eingabe) {
                case "1" -> spieler.hit();
                case "2" -> spieler.stand();
                case "3" -> spieler.doubleDown();
                case "4" -> spieler.split();
                case "5" -> spieler.surrender();
                case "6" -> {
                    System.out.print("Einsatzbetrag: ");
                    int betrag = Integer.parseInt(scanner.nextLine());
                    spieler.setEinsatz(betrag);
                }
                case "7" -> spieler.frageKartenzählerNachAktion();
                case "8" -> System.out.println("Aktuelles Kapital: " + spieler.kapital);
                case "9" -> {
                    System.out.println("True Count: ");
                    double tc = Double.parseDouble(scanner.nextLine());
                    spieler.berechneEinsatz(tc);
                }
                default -> System.out.println("Ungültige Eingabe.");
            }
        }
    }
}