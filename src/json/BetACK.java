package json;
public class BetACK implements BlackJackMessage{
    public String type = "bet";
    public int amount;

    public BetACK(){}

    public BetACK(int amount){
        this.amount=amount;
    }

    @Override
    public String getType(){
        return type;
    }
}
