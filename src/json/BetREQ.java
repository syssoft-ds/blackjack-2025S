package json;

public class BetREQ implements BlackJackMessage{
    public String type="make_bet";

    public BetREQ(){}

    @Override
    public String getType(){
        return type;
    }
}
