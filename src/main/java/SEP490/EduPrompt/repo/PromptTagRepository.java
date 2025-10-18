package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptTag;
import SEP490.EduPrompt.model.PromptTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromptTagRepository extends JpaRepository<PromptTag, PromptTagId> {
    List<PromptTag> findByPromptId(UUID promptId);
}
