import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Statistics {
    private static Map<String, PlayerStats> statsByPlayer = new HashMap<>();

    public static void addCard(String playerName, Card card) {
        getStats(playerName).cardsSeen.add(card);
    }

    public static void incrementGamesPlayed(String playerName) {
        getStats(playerName).gamesPlayed++;
    }

    public static void incrementGamesWon(String playerName) {
        getStats(playerName).gamesWon++;
    }

    public static void incrementBlackjacks(String playerName) {
        getStats(playerName).blackjacks++;
    }

    public static void printStatistics(String playerName) {
        PlayerStats stats = getStats(playerName);
        System.out.println("Statistik für " + playerName);
        System.out.println("  Spiele: " + stats.gamesPlayed);
        System.out.println("  Siege: " + stats.gamesWon);
        System.out.println("  Blackjacks: " + stats.blackjacks);
        System.out.println("  Gesehene Karten:");
        for (Card c : stats.cardsSeen) {
            System.out.println("   - " + c);
        }
    }

    private static PlayerStats getStats(String playerName) {
        return statsByPlayer.computeIfAbsent(playerName, k -> new PlayerStats());
    }

    private static class PlayerStats {
        int gamesPlayed = 0;
        int gamesWon = 0;
        int blackjacks = 0;
        List<Card> cardsSeen = new ArrayList<>();
    }
}
