package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    // own collections
    Page<Collection> findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(UUID createdBy, Pageable pageable);

    // own/private collection
    Optional<Collection> findByIdAndCreatedByAndIsDeletedFalse(UUID id, UUID createdBy);

    // public list
    @Query("SELECT c FROM Collection c WHERE c.visibility = 'public' AND c.isDeleted = false ORDER BY c.createdAt DESC")
    Page<Collection> findPublicCollections(Pageable pageable);

    // count own collections
    long countByCreatedByAndIsDeletedFalse(UUID createdBy);

    Optional<Collection> findByIdAndIsDeletedFalse(UUID id);
}