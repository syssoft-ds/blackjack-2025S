package utilities.exceptions;

public class DeckException extends Exception{
    public DeckException (String message){
        super(message);
    }

    public DeckException (String message, Throwable cause){
        super(message, cause);
    }
}
