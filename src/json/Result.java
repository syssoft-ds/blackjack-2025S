package json;

public class Result implements BlackJackMessage{
    public String type = "result";
    public int earnings;
    public String message;

    public Result(){}

    public Result(int earnings, String message){
        this.earnings = earnings;
        this.message = message;
    }

    @Override
    public String getType(){
        return type;
    }
}
