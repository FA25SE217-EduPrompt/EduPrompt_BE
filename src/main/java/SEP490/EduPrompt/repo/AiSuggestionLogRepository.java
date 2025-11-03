package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.AiSuggestionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiSuggestionLogRepository extends JpaRepository<AiSuggestionLog, UUID> {
}