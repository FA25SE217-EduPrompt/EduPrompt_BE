package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    Optional<GroupMember> findByGroupIdAndUserIdAndStatus( UUID groupId, UUID currentUserId, String status);
}