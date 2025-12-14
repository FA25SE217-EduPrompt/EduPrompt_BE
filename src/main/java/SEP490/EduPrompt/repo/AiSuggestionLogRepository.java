package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.AiSuggestionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiSuggestionLogRepository extends JpaRepository<AiSuggestionLog, UUID> {
}