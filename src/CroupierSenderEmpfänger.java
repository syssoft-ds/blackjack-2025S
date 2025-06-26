import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class CroupierSenderEmpfänger {
    private final int empfangsPortSpieler = 5002;
    private final int empfangsPortKartenzähler = 5003;
    private final int sendePortSpieler = 5000;
    private final int sendePortKartenzähler = 5001;

    public void sendeAnCroupier(String nachricht) {
        sendeUDP("Kartenzähler: " + nachricht, sendePortSpieler);
    }

    public void sendeAnSpieler(String nachricht) {
        sendeUDP("Kartenzähler: " + nachricht, sendePortKartenzähler);
    }

    public void empfangeSpieler() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(empfangsPortSpieler)) {
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

    public void empfangeKartenzähler() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(empfangsPortKartenzähler)) {
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
