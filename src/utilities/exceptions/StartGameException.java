package utilities.exceptions;

public class StartGameException extends Exception{

    public StartGameException (String message){
        super(message);
    }

    public StartGameException (String message, Throwable cause){
        super (message, cause);
    }
    
}
