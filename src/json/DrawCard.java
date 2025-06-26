package json;

public class DrawCard implements BlackJackMessage{
    public String type="receive_card";
    public PlayerCard card;
    public int handIndex;

    public DrawCard(){}

    public DrawCard(PlayerCard card, int handIndex){
        this.card = card;
    }

    @Override
    public String getType(){
        return type;
    }
}
