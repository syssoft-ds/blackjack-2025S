package json;

public class TurnREQ implements BlackJackMessage{
    public String type = "your_turn";
    public PlayerCard croupier_card;

    public TurnREQ(){}

    public TurnREQ(PlayerCard card){
        this.croupier_card = card;
    }

    @Override
    public String getType(){
        return type;
    }
}