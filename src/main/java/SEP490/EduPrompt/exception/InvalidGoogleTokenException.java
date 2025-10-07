package SEP490.EduPrompt.exception;

import SEP490.EduPrompt.exception.auth.AuthExceptionCode;
import org.springframework.http.HttpStatus;

public class InvalidGoogleTokenException extends BaseException {
    public InvalidGoogleTokenException(){
        super(
                AuthExceptionCode.INVALID_GOOGLE_TOKEN.name(),
                "Invalid Google Token",
                HttpStatus.BAD_REQUEST
                );
    }
}
