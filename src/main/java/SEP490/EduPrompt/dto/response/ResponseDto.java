package SEP490.EduPrompt.dto.response;

import org.springframework.http.HttpStatus;

import java.util.List;

public record ResponseDto<T>(T data, ErrorMessage error) {

    public static <T> ResponseDto<T> success(T data) {
        return new ResponseDto<>(data, null);
    }

    public static <T> ResponseDto<T> error(ErrorMessage error) {
        return new ResponseDto<>(null, error);
    }

    public static <T> ResponseDto<T> error(String code, String message) {
        return new ResponseDto<>(null, new ErrorMessage(code, List.of(message)));
    }

    public static <T> ResponseDto<T> error(String code, String message, HttpStatus status) {
        return new ResponseDto<>(null, new ErrorMessage(code, List.of(message), status));
    }

    public static <T> ResponseDto<T> error(String code, List<String> messages) {
        return new ResponseDto<>(null, new ErrorMessage(code, messages));
    }

    public static <T> ResponseDto<T> error(String code, List<String> messages, HttpStatus status) {
        return new ResponseDto<>(null, new ErrorMessage(code, messages, status));
    }
}
