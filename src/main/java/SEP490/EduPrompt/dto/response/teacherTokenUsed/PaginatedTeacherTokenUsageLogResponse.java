package SEP490.EduPrompt.dto.response.teacherTokenUsed;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
public record PaginatedTeacherTokenUsageLogResponse (
        List<TeacherTokenUsageLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
){
}
