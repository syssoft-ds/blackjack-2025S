import java.util.List;



public class Kartenzähler {

    int running_count = 0;
    double true_count = 0;
    int anzahlDecks = 0;

    int aktiveHand = 0;

    List<Karte> gezogeneKarte;
    List<List<Karte>> händeSpieler;
    List<List<Karte>> händeCroupier;

    String[] handTyp = {"hard","soft","paar"};
    int handTypID = 0;

    public List<Karte> getAktiveHand(){
        int temp = aktiveHand;
        if(temp<händeCroupier.size())
            return händeCroupier.get(temp);
        else
            return händeSpieler.get(temp - händeCroupier.size());
    }

    private int calcHandValue() {
        int value = 0;
        List<Karte> hand = getAktiveHand();
        int anzahlAsseHigh = 0;

        for (Karte karte : hand) {
            value += karte.getValue();
            if (karte.getValue() == 11) {
                anzahlAsseHigh++;
            }
        }

        // Wandle Asse von 11 auf 1 um, falls nötig
        while (value > 21 && anzahlAsseHigh > 0) {
            value -= 10;
            anzahlAsseHigh--;
        }

        // Handtyp bestimmen
        if (hand.size() == 2 && hand.get(0).rankNumber == hand.get(1).rankNumber) {
            handTypID = 2; // Pair
        } else if (anzahlAsseHigh > 0) {
            handTypID = 1; // Soft
        } else {
            handTypID = 0; // Hard
        }

        return value;
    }

    private int getDealerValue(){
        int temp = aktiveHand;
        aktiveHand =0;
        int value = calcHandValue();
        aktiveHand = temp;
        return value;
    }

    public String recommendation(){
        int hand = calcHandValue();
        int dealer = getDealerValue();

        if(hand<=8) return "hit";

        if (hand == 9 && (dealer == 2 || dealer > 6)) return "hit";
        else if(hand == 9) return "Double Down otherwise Hit";

        if(hand == 10 && dealer<10) return "Double Down otherwise Hit";
        else if (hand == 10)  return "hit";

        if(hand == 11) return "Double Down otherwise Hit";

        if(hand == 12 && (dealer < 4 || dealer >6)) return "hit";
        else if (hand == 12) return "stand";

        if ((hand ==11 ||  hand == 12) && dealer < 7) return "stand";

        return "";
    }

    private void updateRunningcount(){
        if(gezogeneKarte.isEmpty()) return;
        int rank = gezogeneKarte.getLast().getValue();
        if(rank >= 2 && rank <=6) running_count++;
        if(rank > 6 && rank <10) return;
        else running_count--;
    }

    private double anzahlVerbleibendeDecks(){
        int anzahlKarten = anzahlDecks * 52;

        return (double)((anzahlKarten - gezogeneKarte.size())/anzahlKarten);

    }

    private double calcTrueCount(){
        updateRunningcount();
        true_count = running_count / anzahlVerbleibendeDecks();
        return true_count;
    }


}
