package SEP490.EduPrompt.exception.generic;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFileException extends BaseException {
    public InvalidFileException(String message) {
        super(ExceptionCode.INVALID_ACTION.name(), message, HttpStatus.BAD_REQUEST);

    }
}