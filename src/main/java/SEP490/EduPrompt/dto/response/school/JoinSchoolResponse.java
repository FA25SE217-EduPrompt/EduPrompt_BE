package SEP490.EduPrompt.dto.response.school;

import lombok.Builder;

@Builder
public record JoinSchoolResponse(
        boolean isSuccess
) {
}
