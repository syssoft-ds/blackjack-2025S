package json;

public class MalformedRequest implements BlackJackMessage{

    public String type = "error";
    public String message;

    public MalformedRequest(){}

    public MalformedRequest (String message){
        this.message = message;
    }
    
    @Override
    public String getType(){
        return type;
    }
}
