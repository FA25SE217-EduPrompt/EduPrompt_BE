package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptScoreRepository extends JpaRepository<PromptScore, UUID> {
    Optional<PromptScore> findByPromptIdAndVersionIdIsNull(UUID promptId);

    Optional<PromptScore> findByVersionId(UUID versionId);

    List<PromptScore> findByPromptIdOrderByCreatedAtDesc(UUID promptId);
}