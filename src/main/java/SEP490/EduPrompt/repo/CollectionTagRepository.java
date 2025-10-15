package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.CollectionTag;
import SEP490.EduPrompt.model.CollectionTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionTagRepository extends JpaRepository<CollectionTag, CollectionTagId> {
}