package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.CollectionTag;
import SEP490.EduPrompt.model.CollectionTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CollectionTagRepository extends JpaRepository<CollectionTag, CollectionTagId> {
    @Query("SELECT ct FROM CollectionTag ct JOIN FETCH ct.tag WHERE ct.id.collectionId = :collectionId")
    List<CollectionTag> findByCollectionId(UUID collectionId);

    void deleteByCollectionId(UUID collectionId);
}