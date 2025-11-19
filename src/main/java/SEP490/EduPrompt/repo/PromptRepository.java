package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID>, JpaSpecificationExecutor<Prompt> {
        Page<Prompt> findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(UUID createdBy, Pageable pageable);

        Optional<Prompt> findByIdAndCreatedByAndIsDeletedFalse(UUID id, UUID createdBy);

        Page<Prompt> findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(String visibility, Pageable pageable);

        Optional<Prompt> findByIdAndIsDeletedFalse(UUID id);

        boolean existsByIdAndCollectionId(UUID promptId, UUID collectionId);

        // Private prompts: Only fetch for the current user
        @Query("SELECT p FROM Prompt p WHERE p.visibility = :visibility AND p.isDeleted = false AND p.user.id = :userId")
        Page<Prompt> findByVisibilityAndIsDeletedFalseAndUserId(@Param("visibility") String visibility,
                        @Param("userId") UUID userId, Pageable pageable);

        // Visibility with userId filter
        Page<Prompt> findByVisibilityAndUserIdAndIsDeletedFalse(String visibility, UUID userId, Pageable pageable);

        // Visibility with collectionId filter
        Page<Prompt> findByVisibilityAndCollectionIdAndIsDeletedFalse(String visibility, UUID collectionId,
                        Pageable pageable);

        // Visibility with both userId and collectionId
        Page<Prompt> findByVisibilityAndUserIdAndCollectionIdAndIsDeletedFalse(String visibility, UUID userId,
                        UUID collectionId, Pageable pageable);

        // All non-deleted prompts, sorted by createdAt
        Page<Prompt> findByIsDeletedFalseOrderByCreatedAtAsc(Pageable pageable);

        // All non-deleted prompts, sorted by updatedAt
        Page<Prompt> findByIsDeletedFalseOrderByUpdatedAtAsc(Pageable pageable);

        // Prompts by userId
        Page<Prompt> findByUserIdAndIsDeletedFalse(UUID userId, Pageable pageable);

        // Prompts by collectionId
        Page<Prompt> findByCollectionIdAndIsDeletedFalse(UUID collectionId, Pageable pageable);

        //
        Page<Prompt> findByVisibilityAndIsDeletedFalse(String visibility, Pageable pageable);

        // Group visibility: Only fetch for collections where user is a group member
        @Query("SELECT p FROM Prompt p JOIN p.collection c JOIN GroupMember gm ON c.group.id = gm.group.id " +
                        "WHERE p.visibility = 'GROUP' AND p.isDeleted = false AND gm.user.id = :userId AND gm.status = 'active'")
        Page<Prompt> findGroupPromptsByUserId(@Param("userId") UUID userId, Pageable pageable);

        // Temporary method to handle combined userId and collectionId query
        @Query("SELECT p FROM Prompt p WHERE p.user.id = :userId AND p.collection.id = :collectionId AND p.isDeleted = false")
        Page<Prompt> findByUserIdAndCollectionIdAndIsDeletedFalse(@Param("userId") UUID userId,
                        @Param("collectionId") UUID collectionId, Pageable pageable);

        @Query("SELECT p FROM Prompt p JOIN FETCH p.user JOIN FETCH p.collection c LEFT JOIN FETCH c.group WHERE p.collection.id = :collectionId AND p.isDeleted = false")
        List<Prompt> findByCollectionIdAndIsDeletedFalse(UUID collectionId);

        @Query("""
                        SELECT p FROM Prompt p
                        LEFT JOIN FETCH p.collection c
                        LEFT JOIN FETCH c.group g
                        WHERE p.id = :id AND p.isDeleted = false
                        """)
        Optional<Prompt> findActiveById(@Param("id") UUID id);

        /**
         * Find all prompts with specific indexing status
         */
        List<Prompt> findByIndexingStatusAndIsDeletedAndVisibility(String indexingStatus, Boolean isDeleted,
                        String visibility);

        List<Prompt> findByIndexingStatusAndIsDeleted(String indexingStatus, boolean isDeleted);

        /**
         * Find all prompts that need reindexing (updated after last index)
         */
        @Query("SELECT p FROM Prompt p WHERE p.indexingStatus = 'indexed' " +
                        "AND p.isDeleted = false " +
                        "AND p.updatedAt > p.lastIndexedAt")
        List<Prompt> findPromptsNeedingReindex();

        /**
         * Count indexed prompts by status
         */
        long countByIndexingStatus(String indexingStatus);
}