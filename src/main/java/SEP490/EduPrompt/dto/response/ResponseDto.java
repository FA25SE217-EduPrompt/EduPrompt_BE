package SEP490.EduPrompt.dto.response;

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

    public static <T> ResponseDto<T> error(String code, List<String> messages) {
        return new ResponseDto<>(null, new ErrorMessage(code, messages));
    }
}
