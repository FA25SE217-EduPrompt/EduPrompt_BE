package SEP490.EduPrompt.group;

import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.request.groupMember.AddGroupMembersRequest;
import SEP490.EduPrompt.dto.request.groupMember.RemoveGroupMemberRequest;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.GroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.group.UpdateGroupResponse;
import SEP490.EduPrompt.dto.response.groupMember.PageGroupMemberResponse;
import SEP490.EduPrompt.enums.GroupRole;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.Group;
import SEP490.EduPrompt.model.GroupMember;
import SEP490.EduPrompt.model.School;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.GroupMemberRepository;
import SEP490.EduPrompt.repo.GroupRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.group.GroupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private GroupServiceImpl groupService;

    private UserPrincipal currentUser;
    private User userEntity;
    private UUID userId;
    private UUID groupId;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        schoolId = UUID.randomUUID();

        currentUser = UserPrincipal.builder()
                .userId(userId)
                .email("teacher@test.com")
                .role("TEACHER")
                .schoolId(schoolId)
                .build();

        userEntity = User.builder()
                .id(userId)
                .email("teacher@test.com")
                .build();
    }

    // ======================================================================//
    // =========================== CREATE GROUP =============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Create Group - Success (Teacher)")
    void createGroup_WhenTeacher_ShouldSaveGroupAndAddAdmin() {
        // Arrange
        CreateGroupRequest request = new CreateGroupRequest("Math Club");

        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);

        when(groupRepository.save(any(Group.class))).thenAnswer(i -> {
            Group g = i.getArgument(0);
            g.setId(groupId);
            g.setCreatedAt(Instant.now());
            return g;
        });

        // Act
        CreateGroupResponse response = groupService.createGroup(request, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("Math Club", response.name());

        // Verify Group Saved
        verify(groupRepository).save(any(Group.class));
        // Verify Creator added as Admin
        verify(groupMemberRepository).save(argThat(member ->
                member.getUser().getId().equals(userId) &&
                        member.getRole().equals("admin")
        ));
    }

    @Test
    @DisplayName("Case 2: Create Group - Fail (Student/Invalid Role)")
    void createGroup_WhenStudent_ShouldThrowAccessDenied() {
        // Arrange
        UserPrincipal studentUser = UserPrincipal.builder()
                .userId(userId)
                .role("STUDENT")
                .build();
        CreateGroupRequest request = new CreateGroupRequest("Gaming Club");

        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> groupService.createGroup(request, studentUser));
    }

    // ======================================================================//
    // =========================== UPDATE GROUP =============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Update Group - Success (Group Admin)")
    void updateGroup_WhenGroupAdmin_ShouldUpdateDetails() {
        // Arrange
        UpdateGroupRequest request = new UpdateGroupRequest("New Name", true);
        Group group = Group.builder().id(groupId).name("Old Name").isActive(true).build();

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));

        // Mock Group Admin Permission
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), anyList()))
                .thenReturn(true);

        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);

        // Act
        UpdateGroupResponse response = groupService.updateGroup(groupId, request, currentUser);

        // Assert
        assertEquals("New Name", response.name());
        verify(groupRepository).save(group);
    }

    @Test
    @DisplayName("Case 2: Update Group - Fail (School Admin Mismatch)")
    void updateGroup_WhenSchoolAdminDifferentSchool_ShouldThrowAccessDenied() {
        // Arrange
        UserPrincipal schoolAdmin = UserPrincipal.builder()
                .userId(userId)
                .role("SCHOOL_ADMIN")
                .schoolId(UUID.randomUUID()) // Different School
                .build();

        School groupSchool = new School();
        groupSchool.setId(UUID.randomUUID()); // Group belongs to other school
        Group group = Group.builder().id(groupId).school(groupSchool).build();

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        // Not a group member
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any()))
                .thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> groupService.updateGroup(groupId, new UpdateGroupRequest("Name", true), schoolAdmin));
    }

    // ======================================================================//
    // =========================== ADD MEMBERS ==============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Add Members - Success (New and Update)")
    void addMembersToGroup_WhenValid_ShouldAddAndUpdate() {
        // Arrange
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        AddGroupMembersRequest.MemberRequest m1 = new AddGroupMembersRequest.MemberRequest(user1Id, "member", "active");
        AddGroupMembersRequest.MemberRequest m2 = new AddGroupMembersRequest.MemberRequest(user2Id, "admin", "active"); // Update role
        AddGroupMembersRequest request = new AddGroupMembersRequest(Set.of(m1, m2));

        Group group = Group.builder().id(groupId).groupMembers(new HashSet<>()).build();
        User user1 = User.builder().id(user1Id).build();
        GroupMember member2 = GroupMember.builder().user(User.builder().id(user2Id).build()).role("member").build();

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        // Permission check pass
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any())).thenReturn(true);
        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);

        // User lookup
        when(userRepository.findById(user1Id)).thenReturn(Optional.of(user1));
        when(userRepository.findById(user2Id)).thenReturn(Optional.of(member2.getUser()));

        // Check existence
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, user1Id)).thenReturn(Optional.empty()); // New
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, user2Id)).thenReturn(Optional.of(member2)); // Existing

        // Act
        groupService.addMembersToGroup(groupId, request, currentUser);

        // Assert
        verify(groupMemberRepository, times(2)).save(any(GroupMember.class));
        // Verify member2 role updated to admin
        assertEquals("admin", member2.getRole());
    }

    @Test
    @DisplayName("Case 2: Add Members - Fail (Access Denied)")
    void addMembersToGroup_WhenNotAuthorized_ShouldThrowAccessDenied() {
        // Arrange
        Group group = Group.builder().id(groupId).build();
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));

        // Not admin, not group admin
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any())).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> groupService.addMembersToGroup(groupId, new AddGroupMembersRequest(Set.of()), currentUser));
    }

    // ======================================================================//
    // ========================= REMOVE MEMBERS =============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Remove Member - Success")
    void removeMemberFromGroup_WhenValid_ShouldDeleteMember() {
        // Arrange
        UUID targetId = UUID.randomUUID();
        RemoveGroupMemberRequest request = new RemoveGroupMemberRequest(targetId);

        User creator = User.builder().id(UUID.randomUUID()).build();
        Group group = Group.builder().id(groupId).createdBy(creator).groupMembers(new HashSet<>()).build();
        GroupMember member = GroupMember.builder().user(User.builder().id(targetId).build()).role("member").build();
        group.getGroupMembers().add(member);

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any())).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, targetId)).thenReturn(Optional.of(member));
        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);

        // Act
        groupService.removeMemberFromGroup(groupId, request, currentUser);

        // Assert
        verify(groupMemberRepository).delete(member);
    }

    @Test
    @DisplayName("Case 2: Remove Member - Fail (Remove Creator)")
    void removeMemberFromGroup_WhenTargetIsCreator_ShouldThrowInvalidAction() {
        // Arrange
        UUID targetId = userId; // Creator
        Group group = Group.builder().id(groupId).createdBy(userEntity).build(); // currentUser is creator
        GroupMember member = GroupMember.builder().user(userEntity).build();

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any())).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, targetId)).thenReturn(Optional.of(member));

        // Act & Assert
        assertThrows(InvalidActionException.class,
                () -> groupService.removeMemberFromGroup(groupId, new RemoveGroupMemberRequest(targetId), currentUser));
    }

    @Test
    @DisplayName("Case 3: Remove Member - Fail (Last Admin)")
    void removeMemberFromGroup_WhenLastAdmin_ShouldThrowInvalidAction() {
        // Arrange
        UUID targetId = UUID.randomUUID();
        User creator = User.builder().id(UUID.randomUUID()).build();

        GroupMember adminMember = GroupMember.builder()
                .user(User.builder().id(targetId).build())
                .role("admin")
                .status("active")
                .build();

        // Group has only 1 admin
        Set<GroupMember> members = new HashSet<>();
        members.add(adminMember);
        Group group = Group.builder().id(groupId).createdBy(creator).groupMembers(members).build();

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any())).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, targetId)).thenReturn(Optional.of(adminMember));

        // Act & Assert
        assertThrows(InvalidActionException.class,
                () -> groupService.removeMemberFromGroup(groupId, new RemoveGroupMemberRequest(targetId), currentUser));
    }

    // ======================================================================//
    // ============================ GET GROUP ===============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Group - Success (Active Member)")
    void getGroupById_WhenActiveMember_ShouldReturnGroup() {
        // Arrange
        Group group = Group.builder().id(groupId).name("Group").isActive(true).build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        // Mock membership check
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), anyList()))
                .thenReturn(true);

        // Act
        GroupResponse response = groupService.getGroupById(groupId, currentUser);

        // Assert
        assertEquals("Group", response.name());
    }

    @Test
    @DisplayName("Case 2: Get Group - Fail (Inactive Group & Not System Admin)")
    void getGroupById_WhenGroupInactiveAndNotSysAdmin_ShouldThrowAccessDenied() {
        // Arrange
        Group group = Group.builder().id(groupId).isActive(false).build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> groupService.getGroupById(groupId, currentUser));
    }

    // ======================================================================//
    // ======================== SOFT DELETE =================================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Soft Delete - Success")
    void softDeleteGroup_WhenAuthorized_ShouldSetInactive() {
        // Arrange
        Group group = Group.builder().id(groupId).isActive(true).build();
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any())).thenReturn(true);
        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);

        // Act
        groupService.softDeleteGroup(groupId, currentUser);

        // Assert
        assertFalse(group.getIsActive());
        verify(groupRepository).save(group);
    }

    // ======================================================================//
    // ========================= LIST GROUPS ================================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: List My Groups - Success")
    void listMyGroups_WhenCalled_ShouldReturnPaginatedList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Group group = Group.builder().id(groupId).name("My Group").build();
        Page<Group> page = new PageImpl<>(List.of(group));

        when(groupRepository.findByUserIdAndStatusAndIsActiveTrue(userId, "active", pageable))
                .thenReturn(page);

        // Act
        PageGroupResponse response = groupService.listMyGroups(currentUser, pageable);

        // Assert
        assertEquals(1, response.totalElements());
        assertEquals("My Group", response.content().get(0).name());
    }

    @Test
    @DisplayName("Case 2: Get Members - Success")
    void getMembersByGroupId_WhenAuthorized_ShouldReturnMembers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Group group = Group.builder().id(groupId).isActive(true).build();
        GroupMember member = GroupMember.builder().user(userEntity).role("member").status("active").build();
        Page<GroupMember> page = new PageImpl<>(List.of(member));

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(any(), any(), any(), any())).thenReturn(true);
        when(groupMemberRepository.findByGroupId(groupId, pageable)).thenReturn(page);

        // Act
        PageGroupMemberResponse response = groupService.getMembersByGroupId(groupId, currentUser, pageable);

        // Assert
        assertEquals(1, response.totalElements());
        assertEquals(userId, response.content().get(0).userId());
    }
}
