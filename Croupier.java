import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Croupier extends Spieler {

    private List<Karte> deck;
    private Kartenzaehler zaehler;
    private int decks;
    private int siegeInFolge = 0;

    public Croupier(int decks, Kartenzaehler zaehler) {
        super();
        this.decks = decks;
        this.zaehler = zaehler;
        this.setZaehler(zaehler);
        this.deck = generiereDecks(decks);
        Collections.shuffle(this.deck);
    }

    private List<Karte> generiereDecks(int anzahl) {
        String[] typen = {"Pik", "Kreuz", "Herz", "Karo"};
        String[] rangen = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "Bube", "Dame", "König", "Ass"};
        List<Karte> alleKarten = new ArrayList<>();

        for (int i = 0; i < anzahl; i++) {
            for (String typ : typen) {
                for (String rang : rangen) {
                    alleKarten.add(new Karte(rang, typ));
                }
            }
        }
        return alleKarten;
    }

    @Override
    public void neueRunde() {
        if (deck.size() < 52) {
            System.out.println("Deck zu klein, mische neu.");
            deck = generiereDecks(decks);
            Collections.shuffle(deck);
            zaehler.zuruecksetzen();
        }
        super.neueRunde();
    }

    public void zieheKarteAusDeck() {
        if (!deck.isEmpty()) {
            Karte gezogene = deck.remove(0);
            getHand().add(gezogene);
            System.out.println("Croupier zieht: " + gezogene);
            if (zaehler != null) {
                zaehler.verarbeiteKarte(gezogene);
            }
        } else {
            System.out.println("Keine Karten mehr im Deck.");
        }
    }

    public void spieleZug() {
        System.out.println("Croupier zeigt: " + getHand());
        while (berechneWertDerHand() < 17) {
            zieheKarteAusDeck();
        }
        System.out.println("Croupier steht mit " + berechneWertDerHand() + " Punkten.");
    }

    public List<Karte> getDeck() {
        return deck;
    }

    public void bewerteSpieler(Spieler spieler) {
        int spielerWert = spieler.berechneWertDerHand();
        int dealerWert = berechneWertDerHand();

        boolean spielerBlackjack = spielerWert == 21 && spieler.getHand().size() == 2;
        boolean crupierBlackjack = dealerWert == 21 && getHand().size() == 2;

        if (spielerBlackjack && !crupierBlackjack) {
            System.out.println("Spieler hat BlackJack!");
            spieler.gewinn(true);
            zaehler.spielErgebnis(true, true);
            siegeInFolge++;
        } else if (crupierBlackjack && !spielerBlackjack) {
            System.out.println("Croupier hat BlackJack!");
            spieler.verlust();
            zaehler.spielErgebnis(false, false);
            siegeInFolge = 0;
        } else if (spielerWert > 21) {
            System.out.println("Spieler ist überkauft.");
            spieler.verlust();
            zaehler.spielErgebnis(false, false);
            siegeInFolge = 0;
        } else if (dealerWert > 21 || spielerWert > dealerWert) {
            System.out.println("Spieler gewinnt.");
            spieler.gewinn(false);
            zaehler.spielErgebnis(true, false);
            siegeInFolge++;
        } else if (spielerWert < dealerWert) {
            System.out.println("Croupier gewinnt.");
            spieler.verlust();
            zaehler.spielErgebnis(false, false);
            siegeInFolge = 0;
        } else {
            System.out.println("Unentschieden. Spieler bekommt Einsatz zurück.");
            spieler.setEinsatz(0);
            zaehler.spielErgebnis(false, false);
        }
    }

    public void pruefeObSpielerRausfliegt(Spieler spieler) {
        if (siegeInFolge >= 2) {  // Nur zum testen. Wenn der Spieler zweimal gewonnen hat, bekommt er die Verdächtigungen vom Croupier
            System.out.println("Spieler gewinnt zu oft! Raus aus dem Casino.");
            System.exit(0);
        }
    }

    // Einsatzmethoden deaktivieren
    @Override
    public void setEinsatz(int betrag) {
        // Croupier setzt keinen Einsatz
    }

    @Override
    public int getEinsatz() {
        return 0;
    }
}
