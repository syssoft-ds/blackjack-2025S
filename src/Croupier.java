import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Croupier {

    int anzahlTypen = 4;
    int anzahlRänge = 13;

    int aktuelleHand = 0;


    Map<Integer, Boolean> handEingefroren = new HashMap<>();
    List<Double> wetteinsätze = new ArrayList<>();
    List<List<Karte>> händeCroupier = new ArrayList<>();
    List<List<Karte>> händeSpieler = new ArrayList<>();



    List<List<Karte>> KartenDecks = new ArrayList<>();

    Croupier(int anzahlKartensets) {
        erstelleKartendecks(anzahlKartensets);
    }

    Croupier() {
        erstelleKartendecks(1);
    }

    /**
     * Falls KartenDecks keine Karten mehr haben wird null returned
     * Ansonsten wird ein zufälliges Deck und dann eine zufällige Karte von diesem Deck ausgewählt
     * Am Ende wird die Karte aus dem Deck entfernt
     * @return Karte
     */
    public Karte randomSelect(){

        if(KartenDecks.isEmpty())return null;

        int deck  = (int) (Math.random() * KartenDecks.size());
        while(KartenDecks.get(deck).isEmpty()){
            KartenDecks.remove(deck);
            deck  = (int) (Math.random() * KartenDecks.size());
        }
        int karte = (int) (Math.random() * KartenDecks.get(deck).size());

        return KartenDecks.get(deck).remove(karte);
    }


    /**
     * wählt die nächste Hand welche am Zug ist
     * @return die vorher aktive Hand
     */
    public List<Karte> aktualisiereHandAmZug(){
        int temp = aktuelleHand;
        int summeAllerHände = händeCroupier.size()+händeSpieler.size();
        for (int i = 0; i < summeAllerHände; i++) {
            aktuelleHand++;
            aktuelleHand =  aktuelleHand % summeAllerHände;
            if(handEingefroren.get(aktuelleHand) == null) break;
        }

        if(temp<händeCroupier.size())
            return händeCroupier.get(temp);
        else
            return händeSpieler.get(temp - händeCroupier.size());

    }

    /**
     * entscheidet über die anzahl der hände welche des Croupiers und des Spielers welche Hand ausgewählt wird
     * 0 ist dabei immer die Croupier Hand
     * @return aktuelle Hand als Liste von Karten
     */
    public List<Karte> getAktuelleHand(){
        int temp = aktuelleHand;
        if(temp<händeCroupier.size())
            return händeCroupier.get(temp);
        else
            return händeSpieler.get(temp - händeCroupier.size());
    }

    /**
     * gibt die liste der hände zurück
     * @return
     */
    public List<List<Karte>> getAktuellerSpieler(){
        int temp = aktuelleHand;
        if(temp<händeCroupier.size())
            return händeCroupier;
        else
            return händeSpieler;
    }


    public void playout(){

    }

    public void hit() {
        Karte karte = randomSelect();
        List<Karte> hand = getAktuelleHand();
        hand.add(karte);
    }

    public void stand() {
        aktualisiereHandAmZug();
    }

    public void split() {
        List<Karte> tempList = getAktuelleHand();
        if(getAktuellerSpieler() == händeCroupier)return;
        if (tempList.size() !=2) return;
        if (tempList.get(0).rankNumber != tempList.get(1).rankNumber) return;
        else{
            List<Karte> neueListe = new ArrayList<Karte>();
            getAktuellerSpieler().add(neueListe);
            neueListe.add(tempList.remove(1));
            wetteinsätze.add(wetteinsätze.get(aktuelleHand- händeCroupier.size()));
        }
    }

    public void doubleDown() {
        handEingefroren.put(aktuelleHand,true);
        hit();
        double einsatz =  wetteinsätze.get(aktuelleHand);
        wetteinsätze.set(aktuelleHand,einsatz*2);
    }

    public void surrender() {
        double einsatz = wetteinsätze.get(aktuelleHand);
        wetteinsätze.set(aktuelleHand,einsatz/2);
        handEingefroren.put(aktuelleHand, true);
    }

    public static Croupier starteSpiel(int deckMenge){
        Croupier croupier = new Croupier(deckMenge);

        List<Karte> dealerHand = new ArrayList<>();
        List<Karte> spielerHand = new ArrayList<>();
        croupier.händeCroupier.add(dealerHand);
        croupier.händeCroupier.get(0).add(croupier.randomSelect());

        croupier.händeSpieler.add(spielerHand);
        croupier.händeSpieler.get(0).add(croupier.randomSelect());
        croupier.händeSpieler.get(0).add(croupier.randomSelect());


        return croupier;
    }

    public void newGame(){
        händeSpieler.clear();
        händeCroupier.clear();

        List<Karte> spielerHand = new ArrayList<>();
        List<Karte> dealerHand = new ArrayList<>();
        händeCroupier.add(dealerHand);
        händeCroupier.get(0).add(randomSelect());

        händeSpieler.add(spielerHand);
        händeSpieler.get(0).add(randomSelect());
        händeSpieler.get(0).add(randomSelect());

    }

    /**
     * Erstellt die Liste an Decks der Karten jedes Deck hat 52 Karten
     * @param anzahl
     * @return eine Menge an Decks entsprechend der anzahl
     */
    private List<List<Karte>> erstelleKartendecks(int anzahl) {

        List<List<Karte>> rückgabe = new ArrayList<>();

        for (int i = 0; i < anzahl; i++) {

            List<Karte> tempKartenDeck = new ArrayList<>();

            for (int j = 0; j < anzahlTypen; j++) {
                for (int k = 0; k < anzahlRänge; k++) {
                    tempKartenDeck.add(new Karte(j, k));
                }
            }
            rückgabe.add(tempKartenDeck);
        }
        return rückgabe;
    }


    public static void main(String[] args) {


    }
}

