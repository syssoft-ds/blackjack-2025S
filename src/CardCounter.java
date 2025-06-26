
import java.util.ArrayList;
import java.util.List;

public class CardCounter {
    private List<Card> seenCards = new ArrayList<>();
    private Statistics stats = new Statistics();

    public void processCard(String playerName, Card card) {
        seenCards.add(card);
        stats.addCard(playerName, card);
    }

    public String recommendMove(List<Card> hand) {
        // Dummy: Gib "hit" zurück, wenn unter 17 Punkte, sonst "stand"
        int value = handValue(hand);
        return value < 17 ? "hit" : "stand";
    }

    private int handValue(List<Card> hand) {
        int sum = 0;
        int aces = 0;
        for (Card card : hand) {
            switch (card.rank) {
                case "A":
                    aces++;
                    sum += 11;
                    break;
                case "K":
                case "Q":
                case "J":
                    sum += 10;
                    break;
                default:
                    sum += Integer.parseInt(card.rank);
            }
        }
        while (sum > 21 && aces-- > 0) sum -= 10;
        return sum;
    }

    public Statistics getStatistics() {
        return stats;
    }

    public boolean isBlackjack(List<Card> hand) {
        if (hand.size() != 2) return false;

        boolean hasAce = false;
        boolean hasTen = false;

        for (Card card : hand) {
            if (card.rank.equals("A")) {
                hasAce = true;
            } else if (card.rank.equals("10") || card.rank.equals("J") ||
                    card.rank.equals("Q") || card.rank.equals("K")) {
                hasTen = true;
            }
        }
        return hasAce && hasTen;
    }

}