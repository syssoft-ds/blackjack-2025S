package json;

public class StartGame implements BlackJackMessage {
    public String type = "game_started";

    public StartGame (){}

    @Override
    public String getType(){
        return type;
    }
}
