import java.io.IOException;
import java.net.*;
import java.util.*;
import com.google.gson.*;

public class Croupier {
    public static void main(String[] args) {
        Dealer croupier = new Dealer(5000);
        croupier.start();
    }
}

//  croupier
class Dealer {
    private final int port;
    private DatagramSocket socket;
    private final Gson gson = new Gson();
    private final Map<String, PlayerInfo> players = new LinkedHashMap<>();
    private final List<String> cardCounterAddresses = new ArrayList<>();
    private final Deck deck = new Deck(6);

    private volatile boolean roundActive = false;
    private Iterator<Map.Entry<String, PlayerInfo>> playerTurnIterator;
    private String currentPlayerId = null;

    public Dealer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            socket = new DatagramSocket(port);
            System.out.println("Croupier läuft auf Port " + port);

            new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    if (!roundActive) {
                        System.out.print("Eingabe (start): ");
                        String input = scanner.nextLine();
                        if ("start".equalsIgnoreCase(input.trim())) {
                            if (players.size() >= 1 && players.size() <= 3) {
                                startRound();
                            } else {
                                System.out.println("Mindestens 1 und maximal 3 Spieler müssen registriert sein!");
                            }
                        }
                    } else {
                        try { Thread.sleep( 1000); } catch (InterruptedException ignored) {}
                    }
                }
            }).start();

            while (true) {
                byte[] buf = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength());
                JsonObject msg = parseJson(received);

                String type = getString(msg, "type", "msg", "message");
                if (type == null) continue;

                switch (type.toLowerCase()) {
                    case "register":
                        handleRegister(msg, packet.getAddress(), packet.getPort());
                        break;
                    case "bet":
                        handleBet(msg, packet.getAddress(), packet.getPort());
                        break;
                    case "action":
                        handleAction(msg, packet.getAddress(), packet.getPort());
                        break;
                    case "surrender":
                        handleSurrender(msg, packet.getAddress(), packet.getPort());
                        break;
                    case "stats":
                        handleStats(msg, packet.getAddress(), packet.getPort());
                        break;
                    default:
                        sendError("Unknown message type", packet.getAddress(), packet.getPort());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRegister(JsonObject msg, InetAddress addr, int port) {
        String role = getString(msg, "role");
        String name = getString(msg, "name");
        String id = addr.toString() + ":" + port;

        if ("spieler".equalsIgnoreCase(role)) {
            if (players.size() >= 3) {
                sendError("Tisch ist voll (maximal 3 Spieler erlaubt)", addr, port);
                return;
            }
            if (players.containsKey(id)) {
                sendError("Spieler bereits registriert", addr, port);
                return;
            }
            PlayerInfo info = new PlayerInfo(addr, port, name != null ? name : ("Spieler" + (players.size() + 1)));
            players.put(id, info);
            System.out.println("Spieler registriert: " + info.name + " (" + id + ")");
            sendAck(addr, port);
        } else if ("counter".equalsIgnoreCase(role)) {
            if (!cardCounterAddresses.isEmpty()) {
                sendError("Es ist bereits ein Kartenzähler registriert", addr, port);
                return;
            }
            if (cardCounterAddresses.contains(id)) {
                sendError("Kartenzähler bereits registriert", addr, port);
                return;
            }
            cardCounterAddresses.add(id);
            System.out.println("Kartenzähler registriert: " + id);
            sendAck(addr, port);
        } else {
            sendError("Unknown role", addr, port);
        }
    }

    private void sendAck(InetAddress addr, int port) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "ACK");
        sendJson(obj, addr, port);
    }

    // Rundenstart
    private void startRound() {
        if (roundActive) {
            System.out.println("Runde läuft bereits!");
            return;
        }
        System.out.println("Runde wird gestartet!");
        roundActive = true;
        deck.shuffle();
        for (PlayerInfo p : players.values()) {
            p.resetForRound();
        }
        if (!cardCounterAddresses.isEmpty()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "game_started");
            sendToCounter(obj);
        }
        beginPlayerTurns();
    }

    private void beginPlayerTurns() {
        playerTurnIterator = players.entrySet().iterator();
        nextPlayerTurn();
    }

    private void nextPlayerTurn() {
        if (playerTurnIterator != null && playerTurnIterator.hasNext()) {
            Map.Entry<String, PlayerInfo> entry = playerTurnIterator.next();
            currentPlayerId = entry.getKey();
            PlayerInfo player = entry.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "make_bet");
            sendJson(obj, player.addr, player.port);
            System.out.println("Aufforderung an Spieler " + player.name + " Einsatz zu machen.");
        } else {
            dealInitialCards();
        }
    }

    private void handleBet(JsonObject msg, InetAddress addr, int port) {
        String id = addr.toString() + ":" + port;
        PlayerInfo player = players.get(id);
        if (player == null) {
            sendError("Not registered", addr, port);
            return;
        }
        if (!roundActive || !id.equals(currentPlayerId)) {
            sendError("Nicht dein Zug oder keine aktive Runde", addr, port);
            return;
        }
        int bet = msg.has("amount") ? msg.get("amount").getAsInt() : 0;
        player.bet = bet;
        System.out.println(player.name + " setzt " + bet);
        nextPlayerTurn();
    }

    private void dealInitialCards() {
        for (PlayerInfo p : players.values()) {
            p.hands.clear();
            List<Card> hand = new ArrayList<>();
            hand.add(deck.draw());
            hand.add(deck.draw());
            p.hands.add(hand);

            for (int i = 0; i < hand.size(); i++) {
                sendReceiveCard(p, hand.get(i), 0);
                notifyCounterReceiveCard(p, hand.get(i), 0);
            }
        }
        croupierHand = new ArrayList<>();
        croupierHand.add(deck.draw());
        croupierHand.add(deck.draw());
        playerTurnIterator = players.entrySet().iterator();
        playNextPlayerHand();
    }

    private void playNextPlayerHand() {
        if (playerTurnIterator != null && playerTurnIterator.hasNext()) {
            Map.Entry<String, PlayerInfo> entry = playerTurnIterator.next();
            PlayerInfo player = entry.getValue();
            player.currentHandIndex = 0;
            playHand(player);
        } else {
            playCroupierAndSettle();
        }
    }

    private void playHand(PlayerInfo player) {
        if (player.currentHandIndex < player.hands.size()) {
            List<Card> hand = player.hands.get(player.currentHandIndex);
            if (hand.size() == 2) {
                sendOfferSurrender(player, player.currentHandIndex);
            } else {
                sendYourTurn(player, croupierHand.get(0), player.currentHandIndex);
            }
        } else {
            playNextPlayerHand();
        }
    }

    private void handleAction(JsonObject msg, InetAddress addr, int port) {
        String id = addr.toString() + ":" + port;
        PlayerInfo player = players.get(id);
        if (player == null) {
            sendError("Not registered", addr, port);
            return;
        }
        String move = getString(msg, "move", "action");
        int handIndex = msg.has("handIndex") ? msg.get("handIndex").getAsInt() : 0;
        if (handIndex >= player.hands.size()) {
            sendError("Ungültiger handIndex", addr, port);
            return;
        }
        List<Card> hand = player.hands.get(handIndex);

        switch (move.toLowerCase()) {
            case "hit":
                Card card = deck.draw();
                hand.add(card);
                sendReceiveCard(player, card, handIndex);
                notifyCounterReceiveCard(player, card, handIndex);
                if (handValue(hand) > 21) {
                    player.currentHandIndex++;
                    playHand(player);
                } else {
                    sendYourTurn(player, croupierHand.get(0), handIndex);
                }
                break;
            case "stand":
                player.currentHandIndex++;
                playHand(player);
                break;
            case "double_down":
                if (hand.size() == 2) {
                    player.bet *= 2;
                    Card ddCard = deck.draw();
                    hand.add(ddCard);
                    sendReceiveCard(player, ddCard, handIndex);
                    notifyCounterReceiveCard(player, ddCard, handIndex);
                    if (handValue(hand) > 21) {
                        sendResult(player, handIndex, -player.bet, "bust");
                    }
                    player.currentHandIndex++;
                    playHand(player);
                } else {
                    sendError("Double Down nur mit 2 Karten erlaubt", addr, port);
                    sendYourTurn(player, croupierHand.get(0), handIndex);
                }
                break;
            case "split":
                if (hand.size() == 2 && hand.get(0).rank.equals(hand.get(1).rank)) {
                    // split creates two hands for player which will be played separately
                    Card first = hand.get(0), second = hand.get(1);
                    List<Card> newHand1 = new ArrayList<>(Collections.singletonList(first));
                    List<Card> newHand2 = new ArrayList<>(Collections.singletonList(second));
                    newHand1.add(deck.draw());
                    newHand2.add(deck.draw());
                    player.hands.set(handIndex, newHand1);
                    player.hands.add(handIndex + 1, newHand2);
                    // send receive cards for both new hands for split
                    sendReceiveCard(player, newHand1.get(1), handIndex);
                    sendReceiveCard(player, newHand2.get(1), handIndex + 1);
                    notifyCounterReceiveCard(player, newHand1.get(1), handIndex);
                    notifyCounterReceiveCard(player, newHand2.get(1), handIndex + 1);
                    // play each hand individually
                    sendYourTurn(player, croupierHand.get(0), handIndex);
                } else {
                    sendError("Split nur mit zwei gleichen Karten möglich", addr, port);
                }
                break;
            default:
                sendError("Unbekannter Spielzug", addr, port);
        }
    }

    private void handleSurrender(JsonObject msg, InetAddress addr, int port) {
        String id = addr.toString() + ":" + port;
        PlayerInfo player = players.get(id);
        if (player == null) {
            sendError("Not registered", addr, port);
            return;
        }
        int handIndex = msg.has("handIndex") ? msg.get("handIndex").getAsInt() : 0;
        String answer = getString(msg, "answer");
        List<Card> hand = player.hands.get(handIndex);
        
        if ("yes".equalsIgnoreCase(answer)) {
            player.currentHandIndex++;
            playHand(player);
        } else {
            if (hand.size() == 2 && handValue(hand) == 21) {
                player.currentHandIndex++;
                playHand(player);
            } else {
                sendYourTurn(player, croupierHand.get(0), handIndex);
            }
        }
    }

    private List<Card> croupierHand = new ArrayList<>();
    private void playCroupierAndSettle() {
        while (handValue(croupierHand) < 17) {
            croupierHand.add(deck.draw());
        }
        System.out.println("Croupier Hand: " + croupierHand);
        for (PlayerInfo player : players.values()) {
            for (int i = 0; i < player.hands.size(); i++) {
                List<Card> hand = player.hands.get(i);
                int playerVal = handValue(hand);
                int croupierVal = handValue(croupierHand);
                int earnings = 0;
                String msg;
                if (playerVal > 21) {
                    msg = "bust";
                    earnings = -player.bet;
                } else if (croupierVal > 21 || playerVal > croupierVal) {
                    msg = "win";
                    earnings = player.bet;
                } else if (playerVal == croupierVal) {
                    msg = "push";
                    earnings = 0;
                } else {
                    msg = "lose";
                    earnings = -player.bet;
                }
                sendResult(player, i, earnings, msg);
            }
        }
        roundActive = false;
        System.out.println("Runde beendet. Gewinne ausgeschüttet.");
    }


    private void sendReceiveCard(PlayerInfo player, Card card, int handIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "receive_card");
        obj.add("card", gson.toJsonTree(card));
        obj.addProperty("handIndex", handIndex);
        sendJson(obj, player.addr, player.port);
    }

    private void sendYourTurn(PlayerInfo player, Card croupierCard, int handIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "your_turn");
        obj.add("croupier_card", gson.toJsonTree(croupierCard));
        obj.addProperty("handIndex", handIndex);
        sendJson(obj, player.addr, player.port);
    }

    private void sendOfferSurrender(PlayerInfo player, int handIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "offer_surrender");
        obj.addProperty("handIndex", handIndex);
        sendJson(obj, player.addr, player.port);
    }

    private void sendResult(PlayerInfo player, int handIndex, int earnings, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "result");
        obj.addProperty("earnings", earnings);
        obj.addProperty("message", message);
        obj.addProperty("handIndex", handIndex);
        sendJson(obj, player.addr, player.port);
    }

    private void notifyCounterReceiveCard(PlayerInfo player, Card card, int handIndex) {
        for (String id : cardCounterAddresses) {
            String[] parts = id.split(":");
            try {
                InetAddress addr = InetAddress.getByName(parts[0].replace("/", ""));
                int port = Integer.parseInt(parts[1]);
                JsonObject obj = new JsonObject();
                obj.addProperty("type", "receive_card");
                obj.addProperty("name", player.name);
                obj.add("card", gson.toJsonTree(card));
                obj.addProperty("handIndex", handIndex);
                sendJson(obj, addr, port);
            } catch (Exception ignored) {}
        }
    }

    private void sendToCounter(JsonObject obj) {
        for (String id : cardCounterAddresses) {
            String[] parts = id.split(":");
            try {
                InetAddress addr = InetAddress.getByName(parts[0].replace("/", ""));
                int port = Integer.parseInt(parts[1]);
                sendJson(obj, addr, port);
            } catch (Exception ignored) {}
        }
    }

    private void sendJson(JsonObject obj, InetAddress addr, int port) {
        try {
            String json = gson.toJson(obj);
            byte[] data = json.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendError(String msg, InetAddress addr, int port) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "error");
        obj.addProperty("message", msg);
        sendJson(obj, addr, port);
    }

    private JsonObject parseJson(String json) {
        try {
            return gson.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private String getString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key)) return obj.get(key).getAsString();
        }
        return null;
    }

    private void handleStats(JsonObject msg, InetAddress addr, int port) {
        System.out.println("Statistik erhalten: " + msg);
    }


    private static class PlayerInfo {
        InetAddress addr;
        int port;
        String name;
        int bet = 0;
        List<List<Card>> hands = new ArrayList<>();
        int currentHandIndex = 0;
        PlayerInfo(InetAddress addr, int port, String name) {
            this.addr = addr;
            this.port = port;
            this.name = name;
        }
        void resetForRound() {
            bet = 0;
            hands.clear();
            currentHandIndex = 0;
        }
    }

    private static class Deck {
        private final List<Card> cards = new ArrayList<>();
        private int index = 0;
        Deck(int numDecks) {
            for (int d = 0; d < numDecks; d++) {
                for (String suit : new String[]{"heart", "diamond", "club", "spade"}) {
                    for (String rank : new String[]{"2","3","4","5","6","7","8","9","10","J","Q","K","A"}) {
                        cards.add(new Card(rank, suit));
                    }
                }
            }
            shuffle();
        }
        void shuffle() {
            Collections.shuffle(cards);
            index = 0;
        }
        Card draw() {
            if (index >= cards.size()) {
                shuffle();
            }
            return cards.get(index++);
        }
    }

    private static class Card {
        String rank, suit;
        Card(String rank, String suit) {
            this.rank = rank;
            this.suit = suit;
        }
        public String toString() {
            return rank + "-" + suit;
        }
    }

    private int handValue(List<Card> hand) {
        int value = 0, aces = 0;
        for (Card c : hand) {
            switch (c.rank) {
                case "A": aces++; value += 11; break;
                case "K": case "Q": case "J": value += 10; break;
                default: value += Integer.parseInt(c.rank); break;
            }
        }
        while (value > 21 && aces > 0) {
            value -= 10; aces--;
        }
        return value;
    }
}