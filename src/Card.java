public class Card {
    public String rank; // z.B. "2", "A", "K"
    public String suit; // z.B. "heart", "spade"

    public Card() {} // Für JSON-Deserialisierung

    public Card(String rank, String suit) {
        this.rank = rank;
        this.suit = suit;
    }

    @Override
    public String toString() {
        return rank + " of " + suit;
    }
}
