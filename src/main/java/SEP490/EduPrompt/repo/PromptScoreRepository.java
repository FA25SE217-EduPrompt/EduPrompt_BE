package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.dto.response.prompt.PromptScoreResponse;
import SEP490.EduPrompt.model.PromptScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptScoreRepository extends JpaRepository<PromptScore, UUID> {
    Optional<PromptScore> findByPromptIdAndVersionIdIsNull(UUID promptId);

    Optional<PromptScore> findByVersionId(UUID versionId);

    List<PromptScore> findByPromptIdOrderByCreatedAtDesc(UUID promptId);

    @Query("""
            SELECT new SEP490.EduPrompt.dto.response.prompt.PromptScoreResponse(
                p.id,
                p.title,
                ps.overallScore,
                p.createdAt,
                p.updatedAt
            )
            FROM PromptScore ps
            JOIN ps.prompt p
            WHERE p.isDeleted = false
            ORDER BY ps.overallScore DESC NULLS LAST, p.updatedAt DESC
            """)
    Page<PromptScoreResponse> findPromptsWithOverallScore(Pageable pageable);
}