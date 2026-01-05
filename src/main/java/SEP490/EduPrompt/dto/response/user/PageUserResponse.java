package SEP490.EduPrompt.dto.response.user;

import lombok.Builder;

import java.util.List;

@Builder
public record PageUserResponse(
        List<UserResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {
}
