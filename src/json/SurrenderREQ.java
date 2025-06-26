package json;

public class SurrenderREQ implements BlackJackMessage{
    public String type = "offer_surrender";

    public SurrenderREQ (){}

    @Override
    public String getType(){
        return type;
    }
}
