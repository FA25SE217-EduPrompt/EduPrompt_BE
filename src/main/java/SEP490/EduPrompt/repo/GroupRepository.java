package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Group;
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
public interface GroupRepository extends JpaRepository<Group, UUID> {
    Optional<Group> findByIdAndIsActiveTrue(UUID id);

    Page<Group> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Group> findBySchoolIdAndIsActiveTrue(UUID schoolId, Pageable pageable);

    @Query("SELECT g FROM Group g JOIN GroupMember gm ON g.id = gm.group.id " +
            "WHERE gm.user.id = :userId AND gm.status = :status AND g.isActive = true")
    Page<Group> findByUserIdAndStatusAndIsActiveTrue(
            @Param("userId") UUID userId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("SELECT g FROM Group g JOIN GroupMember gm ON g.id = gm.group.id " +
            "WHERE gm.user.id = :userId AND gm.status = :status AND g.isActive = true")
    List<Group> findByUserIdAndStatusAndIsActiveTrue(
            @Param("userId") UUID userId,
            @Param("status") String status
    );
}