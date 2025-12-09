package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptViewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromptViewLogRepository extends JpaRepository<PromptViewLog, UUID> {

    Optional<PromptViewLog> findPromptViewLogByPromptIdAndUserId(UUID promptId, UUID userId);
}
