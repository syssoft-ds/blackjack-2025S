import com.google.gson.Gson;
import java.net.*;
    public class Main {

        public static void main(String[] args) throws Exception {
            CardCounter counter = new CardCounter();
            DatagramSocket dummySocket = new DatagramSocket();
            InetAddress localhost = InetAddress.getByName("127.0.0.1");
            int dummyPort = dummySocket.getLocalPort(); // spielt für Tests keine Rolle

            // 1. Test: receive_card Nachricht
            String cardJson = """
        {
            "type": "receive_card",
            "name": "Alice",
            "card": { "rank": "K", "suit": "heart" },
            "handIndex": 0
        }
        """;
            MessageHandler.messageAnalyzer(cardJson, dummySocket, localhost, dummyPort, counter);

            // 2. Test: request_turn mit Blackjack
            String turnJson = """
        {
            "type": "request_turn",
            "name": "Alice",
            "hand": [
                { "rank": "A", "suit": "spade" },
                { "rank": "K", "suit": "club" }
            ]
        }
        """;
            MessageHandler.messageAnalyzer(turnJson, dummySocket, localhost, dummyPort, counter);

            // 3. Test: request_bet
            String betJson = """
        {
            "type": "request_bet",
            "credit": 250
        }
        """;
            MessageHandler.messageAnalyzer(betJson, dummySocket, localhost, dummyPort, counter);

            // 4. Ausgabe der Statistik (zum Beweis)
            counter.getStatistics().printStatistics("Alice");
        }
    }

