import java.util.*;

public class Spiel {

    private static final int START_KAPITAL = 100;
    private static final int BASIS_EINSATZ = 20;

    private Spieler spieler;
    private Croupier croupier;
    private Kartenzaehler zaehler;

    public Spiel() {
        int decks = 4;
        zaehler = new Kartenzaehler(decks);
        spieler = new Spieler(START_KAPITAL);
        spieler.setZaehler(zaehler);

        croupier = new Croupier(decks, zaehler);
    }

    public void starte() {
        Scanner scanner = new Scanner(System.in);

        while (spieler.getKapital() > 0) {
            System.out.println("\n**** Neue Runde ****");
            spieler.neueRunde();
            croupier.neueRunde();

            spieler.setEinsatz(BASIS_EINSATZ);

            Karte spielerKarte1 = croupier.getDeck().remove(0);
            Karte spielerKarte2 = croupier.getDeck().remove(0);
            Karte crupierKarte1 = croupier.getDeck().remove(0); // verdeckt
            Karte crupierKarte2 = croupier.getDeck().remove(0); // offen

            spieler.zieheKarte(spielerKarte1);
            spieler.zieheKarte(spielerKarte2);

            croupier.getHand().add(crupierKarte1);
            croupier.getHand().add(crupierKarte2);

            zaehler.verarbeiteKarte(crupierKarte1);
            zaehler.verarbeiteKarte(crupierKarte2);

            System.out.println("Crupier Karten: " + "[verdeckt] [" + crupierKarte2 + "] (Wert: " + crupierKarte2.getWert() + ")" );

            // Spieler spielt
            spieler.macheZug(croupier.getDeck(), crupierKarte2);

            // Spiel bewerten
            if (spieler.berechneWertDerHand() <= 21) {
                croupier.spieleZug();
            }
            croupier.bewerteSpieler(spieler);

            // Prüfen auf Betrug
            croupier.pruefeObSpielerRausfliegt(spieler);
        }

        System.out.println("Spiel beendet. Endkapital: " + spieler.getKapital());

    }

    public static void main(String[] args) {
        Spiel spiel = new Spiel();
        spiel.starte();
    }
}
