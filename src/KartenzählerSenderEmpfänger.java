import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class KartenzählerSenderEmpfänger {
    private final int empfangsPortCroupier = 5001;

    private final int sendePortCroupier = 5003;
    private final int sendePortSpieler = 5004;

    public void sendeAnCroupier(String nachricht) {
        sendeUDP("Kartenzähler: " + nachricht, sendePortCroupier);
    }

    public void sendeAnSpieler(String nachricht) {
        sendeUDP("Kartenzähler: " + nachricht, sendePortSpieler);
    }

    public void empfangeCroupier() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(empfangsPortCroupier)) {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String nachricht = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Spieler empfängt von Croupier: " + nachricht);
                    // Hier kannst du die Nachricht an die Spiellogik weitergeben
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void sendeUDP(String nachricht, int zielPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = nachricht.getBytes();
            InetAddress adresse = InetAddress.getByName("localhost");
            DatagramPacket packet = new DatagramPacket(data, data.length, adresse, zielPort);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

