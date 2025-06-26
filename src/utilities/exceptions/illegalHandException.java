package utilities.exceptions;

public class illegalHandException extends Exception {

    public illegalHandException(String message){
        super(message);
    }

    public illegalHandException(String message, Throwable cause){
        super(message, cause);
    }
    
}
