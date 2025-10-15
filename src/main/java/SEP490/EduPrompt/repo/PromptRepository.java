package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID> {
    Page<Prompt> findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(UUID createdBy, Pageable pageable);

    Optional<Prompt> findByIdAndCreatedByAndIsDeletedFalse(UUID id, UUID createdBy);

    Page<Prompt> findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(String visibility, Pageable pageable);

    Optional<Prompt> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndCollectionId(UUID promptId, UUID collectionId);
}