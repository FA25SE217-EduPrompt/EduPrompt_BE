package SEP490.EduPrompt.dto.request.systemAdmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// TeacherTokenMonthlyUsageResponse.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherTokenMonthlyUsageResponse {
    private Integer year;
    private Integer month;
    private String monthName;
    private Long totalTokensUsed;
    private Long usageCount;
    private Long uniqueTeachers;
}
