package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
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
