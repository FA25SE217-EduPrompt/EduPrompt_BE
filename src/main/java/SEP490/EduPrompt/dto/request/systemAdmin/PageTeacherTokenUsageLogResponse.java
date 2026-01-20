package SEP490.EduPrompt.dto.request.systemAdmin;

import SEP490.EduPrompt.dto.response.teacherTokenUsed.TeacherTokenUsageLogResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageTeacherTokenUsageLogResponse {
    private List<TeacherTokenUsageLogResponse> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
}
