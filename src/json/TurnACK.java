package json;

//import utilities.enums.Move;

public class TurnACK implements BlackJackMessage{
    public String type = "action";
    public String action;
    public int handIndex;

    public TurnACK(){}

    public TurnACK (String move, int handIndex){
        this.action = move;
        this.handIndex = handIndex;
    }

    @Override
    public String getType(){
        return type;
    }
}
