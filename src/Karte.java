public class Karte {
    private static final String[] WERT = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "Bube", "Dame", "König", "Ass"};
    private static final String[] TYP = {"Pik", "Kreuz", "Herz", "Karo"};

    private final String rang;
    private final String typ;

    public Karte(String rang, String typ) {
        this.rang = rang;
        this.typ = typ;
    }

    public String getRang() {
        return rang;
    }

    public String getTyp() {
        return typ;
    }
}