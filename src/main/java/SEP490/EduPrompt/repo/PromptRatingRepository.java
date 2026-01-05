package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PromptRatingRepository extends JpaRepository<PromptRating, UUID> {
    PromptRating findByPromptIdAndUserId(UUID promptId, UUID userId);

    @Modifying
    @Query("""
            UPDATE Prompt p
            SET p.avgRating = ROUND(
                (SELECT AVG(CAST(pr.rating AS integer ))
                 FROM PromptRating pr
                 WHERE pr.prompt.id = p.id), 1)
            WHERE p.id IN (
                SELECT DISTINCT pr2.prompt.id
                FROM PromptRating pr2
                WHERE pr2.prompt.id IS NOT NULL
            )
            """)
    int bulkUpdateAverageRatings();

    @Modifying
    @Query("""
             UPDATE Prompt p
             SET p.avgRating = NULL
             WHERE p.id NOT IN (
                 SELECT DISTINCT pr.prompt.id
            FROM PromptRating pr
                 WHERE pr.prompt.id IS NOT NULL
             )
             """)
    int clearAvgRatingForUnratedPrompts();
}
