package SEP490.EduPrompt.exception.client;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;

public class SignatureGenerationException extends BaseException {
    /**
     * Creates an exception representing a failure to generate a signature.
     *
     * Initializes the exception with the error code "FAILED_SIGNATURE_GENERATION" and HTTP status 503 (Service Unavailable).
     *
     * @param message a human-readable message describing the signature generation failure
     */
    public SignatureGenerationException( String message) {
        super("FAILED_SIGNATURE_GENERATION", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}