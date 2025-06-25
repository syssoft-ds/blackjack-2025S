import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Spieler {

    private List<Karte> hand = new ArrayList<>();
    private int kapital;
    private int einsatz;
    private Kartenzaehler zaehler;

    public Spieler(int startKapital) {
        this.kapital = startKapital;
    }

    public Spieler() {}

    public void setZaehler(Kartenzaehler zaehler) {
        this.zaehler = zaehler;
    }

    public void neueRunde() {
        hand.clear();
        einsatz = 0;
    }

    public void zieheKarte(Karte karte) {
        hand.add(karte);
        System.out.println("Spieler zieht: " + karte);
        if (zaehler != null) {
            zaehler.verarbeiteKarte(karte);
        }
    }

    public int berechneWertDerHand() {
        int summe = 0;
        int asse = 0;

        for (Karte karte : hand) {
            summe += karte.getWert();
            if (karte.getRang().equals("Ass")) {
                asse++;
            }
        }

        while (summe > 21 && asse > 0) {
            summe -= 10;
            asse--;
        }

        return summe;
    }

    public void zeigeHand() {
        System.out.println("Spieler Karten: " + hand + " (Wert: " + berechneWertDerHand() + ")");
    }

    public void macheZug(List<Karte> deck, Karte crupierOffen) {
        Scanner scanner = new Scanner(System.in);
        boolean aktiv = true;

        while (aktiv) {
            zeigeHand();
            System.out.println("Aktion: hit, stand, double, surrender, hilfe, rat, statistik");
            String eingabe = scanner.nextLine().toLowerCase();

            switch (eingabe) {

                // ich habe die Aktion "split" nicht implementiert, weil ich diese Regel zu kompliziert finde.
                // Und ich wusste nicht, wie ich sie umsetzen soll, wenn ich sie nicht verstehe.

                case "hit":
                    zieheKarte(deck.remove(0));
                    if (berechneWertDerHand() > 21) {
                        System.out.println("Bust! Spieler hat über 21.");
                        aktiv = false;
                    }
                    break;

                case "stand":
                    aktiv = false;
                    break;

                case "double":
                    if (kapital >= einsatz) {
                        kapital -= einsatz;
                        einsatz *= 2;
                        zieheKarte(deck.remove(0));
                        System.out.println("Double Down gemacht. Neuer Einsatz: " + einsatz);
                        aktiv = false;
                    } else {
                        System.out.println("Nicht genug Kapital zum Verdoppeln.");
                    }
                    break;

                case "surrender":
                    System.out.println("Spieler gibt auf und verliert die Hälfte des Einsatzes.");
                    kapital += einsatz / 2;
                    einsatz = 0;
                    aktiv = false;
                    break;

                case "hilfe":
                    System.out.println("""
                            Regeln:
                            Crupier spielt "harte 17".
                            Starteinsatz beträgt 20 Euro.
                            Startkapital beträgt 100 Euro.
                            Spieler kann in seinem Zug folgende Aktionen wählen:
                            - hit: Ziehe eine weitere Karte
                            - stand: Bleibe bei deinen Karten
                            - double: Verdopple Einsatz und ziehe eine Karte, dann steh
                            - surrender: Aufgabe, du verlierst halben Einsatz
                            - rat: Frage den Kartenzähler nach einem Tipp
                            - statistik: Zeigt die bisherige Statistik des Spieles an 
                            """);
                    break;

                case "rat":
                    if (zaehler != null) {
                        int wert = berechneWertDerHand();
                        String vorschlag = zaehler.empfehlungAktion(wert, crupierOffen);
                        System.out.println("Kartenzähler empfiehlt: " + vorschlag.toUpperCase());
                    } else {
                        System.out.println("Kein Kartenzähler verfügbar.");
                    }
                    break;

                case "statistik":
                    zaehler.statistikAnzeigen();
                    break;

                default:
                    System.out.println("Unbekannter Befehl.");
            }

            System.out.println("Crupier Karten: " + "[verdeckt] [" + crupierOffen + "]");
        }
    }

    public void setEinsatz(int betrag) {
        if (betrag > kapital) {
            System.out.println("Nicht genug Kapital! Maximal möglich: " + kapital);
            betrag = kapital;
        }

        kapital -= betrag;
        einsatz = betrag;
        System.out.println("Einsatz gesetzt: " + einsatz);
    }

    public int getEinsatz() {
        return einsatz;
    }

    public int getKapital() {
        return kapital;
    }

    public void gewinn(boolean blackjack) {
        int gewinn = blackjack ? (int) (einsatz * 2.5) : einsatz * 2;
        kapital += gewinn;
        System.out.println("Spieler gewinnt. Neuer Kontostand: " + kapital);
    }

    public void verlust() {
        System.out.println("Spieler verliert. Kontostand: " + kapital);
    }

    public List<Karte> getHand() {
        return hand;
    }
}
