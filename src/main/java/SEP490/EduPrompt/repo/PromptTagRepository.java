package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptTag;
import SEP490.EduPrompt.model.PromptTagId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromptTagRepository extends JpaRepository<PromptTag, PromptTagId> {
    List<PromptTag> findByPromptId(UUID promptId);

    void deleteByPromptId(UUID promptId);

    @Query("SELECT pt FROM PromptTag pt WHERE pt.prompt.id = :promptId AND pt.tag.id IN :tagIds")
    List<PromptTag> findExisting(@Param("promptId") UUID promptId, @Param("tagIds") List<UUID> tagIds);

    @EntityGraph(attributePaths = {"tag"})
    List<PromptTag> findByPrompt_Id(UUID promptId);
}
