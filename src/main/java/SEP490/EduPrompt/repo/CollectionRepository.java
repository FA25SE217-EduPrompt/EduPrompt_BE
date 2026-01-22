package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.dto.response.collection.CollectionResponse;
import SEP490.EduPrompt.dto.response.collection.CollectionWithGroupResponse;
import SEP490.EduPrompt.model.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    Page<Collection> findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(UUID createdBy, Pageable pageable);

    List<Collection> findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(UUID createdBy);

    Page<Collection> findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(String visibility, Pageable pageable);

    Page<Collection> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    int countByCreatedByAndIsDeletedFalse(UUID createdBy);

    Optional<Collection> findByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT c FROM Collection c WHERE c.id = :id AND c.user.id = :userId")
    Optional<Collection> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
            SELECT c FROM Collection c
            LEFT JOIN FETCH c.group g
            WHERE c.id = :id AND c.isDeleted = false
            """)
    Optional<Collection> findActiveById(@Param("id") UUID id);

    @Query("SELECT DISTINCT c FROM Collection c " +
            "LEFT JOIN FETCH c.user " +
            "WHERE c.groupId = :groupId " +
            "AND c.isDeleted = false")
    List<Collection> findAllByGroupId(@Param("groupId") UUID groupId);
}