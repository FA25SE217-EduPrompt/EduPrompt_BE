package SEP490.EduPrompt.exception.client;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;

public class SignatureGenerationException extends BaseException {
    public SignatureGenerationException( String message) {
        super("FAILED_SIGNATURE_GENERATION", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
