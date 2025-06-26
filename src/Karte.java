class Karte {
    static final String[] typ = {"Herz", "Karo", "Pik", "Kreuz"};
    static final String[] rank = {
            "ZWEI", "DREI", "VIER", "FÜNF", "SECHS", "SIEBEN",
            "ACHT", "NEUN", "ZEHN", "BUBE", "DAME", "KÖNIG", "ASS"
    };

    int typNumber;
    int rankNumber;

    public String getName() {
        return typ[typNumber] + " " + rank[rankNumber];
    }

    public Karte(int typ, int rank) {
        typNumber = typ;
        rankNumber = rank;
    }

    public int getValue() {
        if (rankNumber <= 8)
            return rankNumber + 2;

        if (rankNumber != 12)
            return 10;

        else return 11;
    }
}
