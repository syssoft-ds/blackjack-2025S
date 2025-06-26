import java.net.*;
import java.util.Scanner;

public class StartGame {

    private DatagramSocket socket;
    private InetAddress targetAddress;
    private int targetPort;
    private CardCounter counter;

    public StartGame() throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Ziel-IP Adresse eingeben: ");
        String ip = scanner.nextLine();
        targetAddress = InetAddress.getByName(ip);

        System.out.print("Ziel-Port eingeben: ");
        targetPort = Integer.parseInt(scanner.nextLine());

        socket = new DatagramSocket(); // zufälliger Port
        counter = new CardCounter();

        System.out.println("Lokaler Socket gestartet auf Port: " + socket.getLocalPort());

        // Test: Nachricht senden
        MessageHandler.sendMessage(socket, targetAddress, targetPort,
                "{ \"type\": \"register\", \"role\": \"Kartenzähler\" }");

        // Empfang starten
        listenForMessages();
    }

    private void listenForMessages() {
        Thread listenerThread = new Thread(() -> {
            byte[] buffer = new byte[2048]; // größerer Puffer für komplexere Nachrichten
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String received = new String(packet.getData(), 0, packet.getLength());
                    InetAddress senderAddress = packet.getAddress();
                    int senderPort = packet.getPort();

                    System.out.println("Empfangen von " + senderAddress + ":" + senderPort);
                    System.out.println("Inhalt: " + received);

                    // Nachricht analysieren & ggf. antworten
                    MessageHandler.messageAnalyzer(received, socket, senderAddress, senderPort, counter);

                } catch (Exception e) {
                    System.err.println("Fehler beim Empfangen: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        listenerThread.start();
    }

    public static void main(String[] args) throws Exception {
        new StartGame();
    }
}
