public class Karte {

    private String rang;
    private String typ;

    public Karte(String rang, String typ) {
        this.rang = rang;
        this.typ = typ;
    }

    public int getWert() {
        switch (rang) {
            case "Bube":
            case "Dame":
            case "König":
                return 10;
            case "Ass":
                return 11;
            default:
                return Integer.parseInt(rang);
        }
    }

    public String getRang() {
        return rang;
    }

    public String getTyp() {
        return typ;
    }

    public String toString() {
        return rang + " " + typ;
    }

}
