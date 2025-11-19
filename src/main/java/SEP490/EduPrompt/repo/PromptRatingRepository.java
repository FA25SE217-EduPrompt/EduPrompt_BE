package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptRating;
import SEP490.EduPrompt.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromptRatingRepository extends JpaRepository<PromptRating, UUID> {
    PromptRating findByPromptIdAndUserId(UUID promptId, UUID userId);

    UUID user(User user);

    @Query("SELECT DISTINCT pr.prompt.id FROM PromptRating pr")
    List<UUID> findAllDistinctPromptIds();

    @Query("""
           SELECT COALESCE(AVG(CAST(pr.rating AS double)), 0.0)
           FROM PromptRating pr
           WHERE pr.prompt.id = :promptId
           """)
    Double calculateAverageRatingByPromptId(@Param("promptId") UUID promptId);
}
