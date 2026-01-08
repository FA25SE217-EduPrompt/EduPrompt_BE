package SEP490.EduPrompt.dto.response.curriculum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CurriculumContext {
    private String subject;
    private UUID subjectId;
    private Integer gradeLevel;
    private Integer semester;
    private List<String> detectedKeywords;
    private String detectedChapter;
    private String detectedLesson;
}
