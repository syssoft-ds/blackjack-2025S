import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class MessageHandler {
    private static final Gson gson = new Gson();

    public static void sendMessage(DatagramSocket socket, InetAddress address, int port, String messageJson) throws Exception {
        byte[] data = messageJson.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
        System.out.println("Gesendet: " + messageJson);
    }

    public static void messageAnalyzer(String jsonString, DatagramSocket socket, InetAddress address, int port, CardCounter counter) {        JsonObject msg = gson.fromJson(jsonString, JsonObject.class);
        String tag = msg.get("type").getAsString();

        switch (tag) {
            case "receive_card": {
                String playerName = msg.get("name").getAsString();
                int handIndex = msg.get("handIndex").getAsInt();
                JsonObject card = msg.getAsJsonObject("card");
                String rank = card.get("rank").getAsString();
                String suit = card.get("suit").getAsString();

                counter.processCard(playerName, new Card(rank, suit));
                break;
            }

            case "request_turn": {
                // Hand aus JSON extrahieren
                List<Card> hand = new ArrayList<>();
                for (var element : msg.getAsJsonArray("hand")) {
                    JsonObject cardObj = element.getAsJsonObject();
                    String rank = cardObj.get("rank").getAsString();
                    String suit = cardObj.get("suit").getAsString();
                    hand.add(new Card(rank, suit));
                }

                // Spielername extrahieren, falls mitgeschickt
                String playerName = msg.has("name") ? msg.get("name").getAsString() : "unbekannt";

                // Blackjack prüfen
                if (counter.isBlackjack(hand)) {
                    System.out.println("Blackjack erkannt für Spieler " + playerName);
                    Statistics.incrementBlackjacks(playerName);
                }

                String bestMove = counter.recommendMove(hand);

                // Antwort senden
                JsonObject response = new JsonObject();
                response.addProperty("type", "optimal_turn");
                response.addProperty("move", bestMove);
                try {
                    sendMessage(socket, address, port, gson.toJson(response));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }


            case "request_bet": {
                int credit = msg.get("credit").getAsInt();

                int recommendedBet = Math.max(10, credit / 10);

                JsonObject response = new JsonObject();
                response.addProperty("type", "optimal_bet");
                response.addProperty("amount", recommendedBet);
                try {
                    sendMessage(socket, address, port, gson.toJson(response));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }

            default: {
                System.out.println("FEHLER: Folgende Nachricht konnte nicht interpretiert werden:");
                System.out.println(msg);
            }
        }
    }

}
