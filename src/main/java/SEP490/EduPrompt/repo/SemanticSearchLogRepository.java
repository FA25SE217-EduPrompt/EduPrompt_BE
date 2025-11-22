package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.SemanticSearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SemanticSearchLogRepository extends JpaRepository<SemanticSearchLog, UUID> {
}