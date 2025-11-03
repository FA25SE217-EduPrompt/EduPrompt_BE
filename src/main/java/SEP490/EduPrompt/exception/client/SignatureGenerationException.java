package SEP490.EduPrompt.exception.client;

import SEP490.EduPrompt.exception.BaseException;
import SEP490.EduPrompt.exception.generic.ExceptionCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class SignatureGenerationException extends BaseException {
    public SignatureGenerationException(String message) {
        super(ExceptionCode.FAILED_SIGNATURE_GENERATION.name(), message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
