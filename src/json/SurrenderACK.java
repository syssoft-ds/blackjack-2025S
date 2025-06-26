package json;

public class SurrenderACK implements BlackJackMessage{
    public String type = "surrender";
    public String answer;

    public SurrenderACK(){}

    public SurrenderACK (String answer){
        this.answer = answer;
    }

    @Override
    public String getType(){
        return type;
    }
}
