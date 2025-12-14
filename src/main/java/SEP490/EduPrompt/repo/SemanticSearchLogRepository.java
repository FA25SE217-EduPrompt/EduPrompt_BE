package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.SemanticSearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SemanticSearchLogRepository extends JpaRepository<SemanticSearchLog, UUID> {
}