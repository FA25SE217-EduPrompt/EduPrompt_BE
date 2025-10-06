package SEP490.EduPrompt.exception;

import SEP490.EduPrompt.dto.response.ErrorMessage;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.exception.auth.AuthExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    //TODO: change those error codes to enum class

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseDto<?> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.toList());

        return ResponseDto.error(
                new ErrorMessage(
                        AuthExceptionCode.VALIDATION_ERROR.name(),
                        errors
                )
        );
    }

    @ExceptionHandler(BaseException.class)
    public ResponseDto<?> handleCustomException(BaseException ex) {
        log.warn("Exception has occurred: {}", ex.getMessage());
        return ResponseDto.error(
                new ErrorMessage(
                        ex.getCode(),
                        ex.getMessages(),
                        ex.getStatus()));
    }


    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseDto<?> handleBadCredentials(BadCredentialsException ex) {
        return ResponseDto.error(
                AuthExceptionCode.AUTH_FAILED.name(),
                "Invalid email or password");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseDto<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseDto.error("INVALID_INPUT", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseDto<?> handleAccessDenied(AccessDeniedException ex) {
        return ResponseDto.error(
                AuthExceptionCode.ACCESS_DENIED.name(),
                "You don't have permission to perform this action. This endpoint requires ADMIN role.");
    }

//    @ExceptionHandler(AuthenticationException.class)
//    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException e) {
//        log.error("Authentication failed: {}", e.getMessage());
//        Map<String, String> error = new HashMap<>();
//        error.put("error", "Authentication failed");
//        error.put("message", "Invalid credentials");
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
//    }
//
//    @ExceptionHandler(RuntimeException.class)
//    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
//        log.error("Runtime error: {}", e.getMessage());
//        Map<String, String> error = new HashMap<>();
//        error.put("error", "Internal server error");
//        error.put("message", "An unexpected error occurred");
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
//    }
//
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
//        log.error("Unexpected error: {}", e.getMessage(), e);
//        Map<String, String> error = new HashMap<>();
//        error.put("error", "Internal server error");
//        error.put("message", "An unexpected error occurred");
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
//    }
}
