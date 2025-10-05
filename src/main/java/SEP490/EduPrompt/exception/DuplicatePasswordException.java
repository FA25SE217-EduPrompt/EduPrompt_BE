package SEP490.EduPrompt.exception;

public class DuplicatePasswordException extends RuntimeException {
    public DuplicatePasswordException(String message) {
        super(message);
    }
}
