package SEP490.EduPrompt.dto.response;

import lombok.Builder;

@Builder
public record LoginResponse(
        String token
) {
}

