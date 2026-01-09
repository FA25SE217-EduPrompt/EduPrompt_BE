package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.CollectionTag;
import SEP490.EduPrompt.model.CollectionTagId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CollectionTagRepository extends JpaRepository<CollectionTag, CollectionTagId> {
    @Query("SELECT ct FROM CollectionTag ct JOIN FETCH ct.tag WHERE ct.id.collectionId = :collectionId")
    List<CollectionTag> findByCollectionId(UUID collectionId);

    void deleteByCollectionId(UUID collectionId);

    @Query("SELECT ct FROM CollectionTag ct WHERE ct.collection.id = :collectionId AND ct.tag.id IN :tagIds")
    List<CollectionTag> findExisting(@Param("collectionId") UUID collectionId, @Param("tagIds") List<UUID> tagIds);

    @EntityGraph(attributePaths = {"tag"})
    List<CollectionTag> findByCollection_Id(UUID collectionId);

    @EntityGraph(attributePaths = {"tag"})
    List<CollectionTag> findByCollectionIdIn(List<UUID> collectionIds);
}