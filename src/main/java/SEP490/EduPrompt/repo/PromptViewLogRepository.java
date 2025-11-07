package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptViewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromptViewLogRepository extends JpaRepository<PromptViewLog, UUID> {
    boolean existByPromptIdAndUserId(UUID promptId, UUID userId);
}
