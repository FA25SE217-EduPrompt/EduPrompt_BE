package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.GroupMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID currentUserId, String status);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID currentUserId);

    @Query("SELECT gm FROM GroupMember gm WHERE gm.group.id = :groupId")
    Page<GroupMember> findByGroupId(@Param("groupId") UUID groupId, Pageable pageable);

    boolean existsByGroupIdAndUserIdAndStatus(UUID groupId, UUID currentUserId, String status);

    boolean existsByGroupIdAndUserIdAndRoleIn(UUID groupId, UUID currentUserId, List<String> roles);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserIdAndStatusAndRoleIn(UUID groupId, UUID currentUserId, String status, List<String> roles);

    long countByGroupIdAndStatus(UUID groupId, String status);

    long countByGroupIdAndRoleAndStatus(UUID groupId, String role, String status);

    @Modifying
    @Query("UPDATE GroupMember gm SET gm.status = :status WHERE gm.group.id = :groupId")
    void updateStatusByGroupId(UUID groupId, String status);

    Page<GroupMember> findByGroupIdAndStatus(UUID groupId, String status, Pageable pageable);
}