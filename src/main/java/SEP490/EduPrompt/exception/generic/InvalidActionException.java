package SEP490.EduPrompt.exception.generic;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvalidActionException extends BaseException {
    public InvalidActionException(String message) {
        super(ExceptionCode.INVALID_ACTION.name(), message, HttpStatus.FORBIDDEN);
    }
}
