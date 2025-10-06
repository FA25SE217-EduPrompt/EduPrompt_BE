package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidInputException extends BaseException {
    public InvalidInputException() {
        super(AuthExceptionCode.INVALID_INPUT.name(), "Invalid input ", HttpStatus.BAD_REQUEST);
    }

    public InvalidInputException(String message) {
        super(AuthExceptionCode.INVALID_INPUT.name(), message, HttpStatus.BAD_REQUEST);
    }
}
