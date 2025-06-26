package utilities.exceptions;

public class UnknownJsonTypeException extends Exception{
    
    public UnknownJsonTypeException(String message){
        super(message);
    }

    public UnknownJsonTypeException(String message, Throwable cause){
        super(message, cause);
    }
}
