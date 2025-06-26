package utilities.exceptions;

public class RegisterException extends Exception {
    public RegisterException(String message){
        super(message);
    }

    public RegisterException(String message, Throwable cause){
        super(message, cause);
    }
}
