package athena.coder.exception;

public class RocAgentException extends RuntimeException {

    public RocAgentException(String message) {
        super(message);
    }

    public RocAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
