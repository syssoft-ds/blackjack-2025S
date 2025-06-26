import java.util.ArrayList;
import java.util.List;

public class Kartenzaehler {

    private int anzahlDecks;
    private int runningCount = 0;
    private int gespielteRunden = 0;
    private int gewonneneRunden = 0;
    private int blackjacks = 0;
    private List<Karte> verwendeteKarten = new ArrayList<>();

    public Kartenzaehler(int anzahlDecks) {
        this.anzahlDecks = anzahlDecks;
    }

    public void verarbeiteKarte(Karte karte) {
        verwendeteKarten.add(karte);
        String rang = karte.getRang();

        // High-Low System
        switch (rang) {
            case "2":
            case "3":
            case "4":
            case "5":
            case "6":
                runningCount += 1;
                break;
            case "10":
            case "Bube":
            case "Dame":
            case "König":
            case "Ass":
                runningCount -= 1;
                break;
            default:
                // 7,8,9 neutral
        }
    }

    public void rundeGespielt(boolean gewonnen, boolean blackjack) {
        gespielteRunden++;
        if (gewonnen) gewonneneRunden++;
        if (blackjack) blackjacks++;
    }

    public String empfehlungAktion(int spielerWert, Karte croupierOffen) {
        if (spielerWert <= 11) return "hit";
        if (spielerWert >= 17) return "stand";
        if (spielerWert == 12 && croupierOffen.getWert() >= 4 && croupierOffen.getWert() <= 6) return "stand";
        if (spielerWert >= 13 && spielerWert <= 16 && croupierOffen.getWert() >= 2 && croupierOffen.getWert() <= 6) return "stand";
        return "hit";
    }

    public void statistikAnzeigen() {
        System.out.println("\n--- Statistik des Spieles---");
        System.out.println("Gespielte Runden: " + gespielteRunden);
        System.out.println("Gewonnene Runden: " + gewonneneRunden);
        System.out.println("Blackjacks: " + blackjacks);
        System.out.println("Verwendete Karten: " + verwendeteKarten.size());
        System.out.println("----------------------------\n");
    }

    public void zuruecksetzen() {
        runningCount = 0;
        verwendeteKarten.clear();
    }

    public void spielErgebnis(boolean gewonnen, boolean blackjack) {
        rundeGespielt(gewonnen, blackjack);
    }


}
