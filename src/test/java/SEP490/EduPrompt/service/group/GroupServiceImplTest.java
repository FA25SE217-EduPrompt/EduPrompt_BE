package SEP490.EduPrompt.service.group;

import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
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
import SEP490.EduPrompt.repo.SchoolRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupServiceImpl groupService;

    private UserPrincipal systemAdminPrincipal;
    private UserPrincipal schoolAdminPrincipal;
    private UserPrincipal teacherPrincipal;
    private User user;
    private User user2;
    private School school;
    private Group group;
    private GroupMember groupMember;
    private UUID groupId;
    private UUID userId;
    private UUID schoolId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        // Initialize user
        user = User.builder()
                .id(userId)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .schoolId(schoolId)
                .build();

        // Initialize school
        school = School.builder()
                .id(schoolId)
                .build();

        // Initialize group
        group = Group.builder()
                .id(groupId)
                .name("Test Group")
                .school(school)
                .createdBy(user)
                .updatedBy(user)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .groupMembers(new HashSet<>())
                .build();

        // Initialize group member
        groupMember = GroupMember.builder()
                .id(memberId)
                .group(group)
                .user(user)
                .role(GroupRole.ADMIN.name().toLowerCase())
                .status("active")
                .joinedAt(Instant.now())
                .build();

        // Initialize UserPrincipal for different roles
        systemAdminPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.SYSTEM_ADMIN.name())
                .schoolId(null)
                .build();

        schoolAdminPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.SCHOOL_ADMIN.name())
                .schoolId(schoolId)
                .build();

        teacherPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.TEACHER.name())
                .schoolId(schoolId)
                .build();
    }

    //================================================================//
    //====================CREATE GROUP===============================//
    @Test
    void createGroup_Success_SystemAdmin() {
        // Arrange: Set up test data and mock behavior
        CreateGroupRequest request = new CreateGroupRequest("Test Group");
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(groupMember);

        // Act: Call the service method
        CreateGroupResponse response = groupService.createGroup(request, systemAdminPrincipal);

        // Assert: Verify the response and interactions
        assertNotNull(response, "Response should not be null");
        assertEquals(groupId, response.id(), "Group ID should match");
        assertEquals(request.name(), response.name(), "Group name should match");
        assertEquals(schoolId, response.schoolId(), "School ID should match");
        assertTrue(response.isActive(), "Group should be active");
        verify(groupRepository).save(any(Group.class));
        verify(groupMemberRepository).save(any(GroupMember.class));
        verifyNoInteractions(schoolRepository); // Ensure no school repository calls
    }

    @Test
    void createGroup_Success_Teacher() {
        // Arrange
        CreateGroupRequest request = new CreateGroupRequest("Test Group");
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(groupMember);

        // Act
        CreateGroupResponse response = groupService.createGroup(request, teacherPrincipal);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(groupId, response.id(), "Group ID should match");
        assertEquals(request.name(), response.name(), "Group name should match");
        assertEquals(schoolId, response.schoolId(), "School ID should match");
        assertTrue(response.isActive(), "Group should be active");
        verify(groupRepository).save(any(Group.class));
        verify(groupMemberRepository).save(any(GroupMember.class));
        verifyNoInteractions(schoolRepository); // Ensure no school repository calls
    }

    //================================================================//
    //====================UPDATE GROUP===============================//
    @Test
    void updateGroup_Success_SystemAdmin() {
        // Arrange
        UpdateGroupRequest request = new UpdateGroupRequest("Updated Group", true);
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);

        // Act
        UpdateGroupResponse response = groupService.updateGroup(groupId, request, systemAdminPrincipal);

        // Assert
        assertNotNull(response);
        assertEquals(groupId, response.id());
        assertEquals(request.name(), response.name());
        assertTrue(response.isActive());
        verify(groupRepository).save(any(Group.class));
    }

    @Test
    void updateGroup_Success_GroupAdmin() {
        // Arrange
        UpdateGroupRequest request = new UpdateGroupRequest("Updated Group", true);
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())))).thenReturn(true);
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(groupRepository.save(any(Group.class))).thenReturn(group);

        // Act
        UpdateGroupResponse response = groupService.updateGroup(groupId, request, teacherPrincipal);

        // Assert
        assertNotNull(response);
        assertEquals(groupId, response.id());
        assertEquals(request.name(), response.name());
        verify(groupRepository).save(any(Group.class));
    }

    @Test
    void updateGroup_GroupNotFound_ThrowsResourceNotFound() {
        // Arrange
        UpdateGroupRequest request = new UpdateGroupRequest("Updated Group", true);
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> groupService.updateGroup(groupId, request, systemAdminPrincipal));
    }

    @Test
    void updateGroup_NonAdminTeacher_ThrowsAccessDenied() {
        // Arrange
        UpdateGroupRequest request = new UpdateGroupRequest("Updated Group", true);
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())))).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> groupService.updateGroup(groupId, request, teacherPrincipal));
    }

    //================================================================//
    //====================ADD GROUP MEMBERS==========================//
//    @Test
//    void addGroupMembers_Success_GroupAdmin() {
//        // Arrange
//        UUID newMemberId = UUID.randomUUID();
//        AddGroupMembersRequest.MemberRequest memberRequest = new AddGroupMembersRequest.MemberRequest(newMemberId, GroupRole.MEMBER.name(), "active");
//        AddGroupMembersRequest request = new AddGroupMembersRequest(Set.of(memberRequest));
//        User newMember = User.builder().id(newMemberId).build();
//
//        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
//        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
//                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name())))).thenReturn(true);
//        when(userRepository.findById(newMemberId)).thenReturn(Optional.of(newMember));
//        when(userRepository.getReferenceById(userId)).thenReturn(user);
//        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(groupMember);
//        when(groupRepository.save(any(Group.class))).thenReturn(group);
//
//        // Act
//        UpdateGroupResponse response = groupService.addMembersToGroup(groupId, request, teacherPrincipal);
//
//        // Assert
//        assertNotNull(response);
//        assertEquals(groupId, response.id());
//        verify(groupMemberRepository).save(any(GroupMember.class));
//        verify(groupRepository).save(any(Group.class));
//    }
//
//    @Test
//    void addGroupMembers_NonAdmin_ThrowsAccessDenied() {
//        // Arrange
//        UUID newMemberId = UUID.randomUUID();
//        AddGroupMembersRequest.MemberRequest memberRequest = new AddGroupMembersRequest.MemberRequest(newMemberId, GroupRole.MEMBER.name(), "active");
//        AddGroupMembersRequest request = new AddGroupMembersRequest(Set.of(memberRequest));
//
//        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
//        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
//                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name())))).thenReturn(false);
//
//        // Act & Assert
//        assertThrows(AccessDeniedException.class, () -> groupService.addMembersToGroup(groupId, request, teacherPrincipal));
//    }
//
//    @Test
//    void addGroupMembers_UserNotFound_ThrowsResourceNotFound() {
//        // Arrange
//        UUID newMemberId = UUID.randomUUID();
//        AddGroupMembersRequest.MemberRequest memberRequest = new AddGroupMembersRequest.MemberRequest(newMemberId, GroupRole.MEMBER.name(), "active");
//        AddGroupMembersRequest request = new AddGroupMembersRequest(Set.of(memberRequest));
//
//        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
//        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
//                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name())))).thenReturn(true);
//        when(userRepository.findById(newMemberId)).thenReturn(Optional.empty());
//
//        // Act & Assert
//        assertThrows(ResourceNotFoundException.class, () -> groupService.addMembersToGroup(groupId, request, teacherPrincipal));
//    }

    //================================================================//
    //====================REMOVE GROUP MEMBER=======================//
    @Test
    void removeGroupMember_Success_GroupAdmin() {
        // Arrange: Set up test data and mock behavior
        UUID anotherAdminId = UUID.randomUUID();
        User anotherAdminUser = User.builder().id(anotherAdminId).build();
        GroupMember anotherAdmin = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(group)
                .user(anotherAdminUser)
                .role(GroupRole.ADMIN.name().toLowerCase())
                .status("active")
                .joinedAt(Instant.now())
                .build();
        group.getGroupMembers().add(groupMember); // Member to be removed
        group.getGroupMembers().add(anotherAdmin); // Additional active admin

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())))).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, memberId)).thenReturn(Optional.of(groupMember));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(groupRepository.save(any(Group.class))).thenReturn(group);

        // Act: Call the service method
        RemoveGroupMemberRequest request = new RemoveGroupMemberRequest(memberId);
        groupService.removeMemberFromGroup(groupId, request, teacherPrincipal);

        // Assert: Verify interactions
        verify(groupMemberRepository).delete(groupMember);
        verify(groupRepository).save(any(Group.class));
        verify(groupRepository).findByIdAndIsActiveTrue(groupId);
        verify(groupMemberRepository).existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())));
        verify(groupMemberRepository).findByGroupIdAndUserId(groupId, memberId);
        verify(userRepository).getReferenceById(userId);
        verifyNoMoreInteractions(groupRepository, groupMemberRepository, userRepository);
        verifyNoInteractions(schoolRepository);
    }

    @Test
    void removeGroupMember_Creator_ThrowsInvalidAction() {
        // Arrange
        RemoveGroupMemberRequest request = new RemoveGroupMemberRequest(userId);
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())))).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId)).thenReturn(Optional.of(groupMember));

        // Act & Assert
        assertThrows(InvalidActionException.class, () -> groupService.removeMemberFromGroup(groupId, request, teacherPrincipal));
    }

    @Test
    void removeGroupMember_LastAdmin_ThrowsInvalidAction() {
        // Arrange
        RemoveGroupMemberRequest request = new RemoveGroupMemberRequest(memberId);
        group.getGroupMembers().add(groupMember);

        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())))).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, memberId)).thenReturn(Optional.of(groupMember));

        // Act & Assert
        assertThrows(InvalidActionException.class, () -> groupService.removeMemberFromGroup(groupId, request, teacherPrincipal));
    }

    //================================================================//
    //====================GET GROUP BY ID===========================//
    @Test
    void getGroupById_Success_SystemAdmin() {
        // Arrange
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // Act
        GroupResponse response = groupService.getGroupById(groupId, systemAdminPrincipal);

        // Assert
        assertNotNull(response);
        assertEquals(groupId, response.id());
        assertEquals("Test Group", response.name());
        assertEquals(schoolId, response.schoolId());
    }

    @Test
    void getGroupById_NonMemberTeacher_ThrowsAccessDenied() {
        // Arrange
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), anyList())).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> groupService.getGroupById(groupId, teacherPrincipal));
    }

    @Test
    void getGroupById_GroupNotFound_ThrowsResourceNotFound() {
        // Arrange
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> groupService.getGroupById(groupId, systemAdminPrincipal));
    }

    //================================================================//
    //====================SOFT DELETE GROUP==========================//
    @Test
    void softDeleteGroup_Success_SystemAdmin() {
        // Arrange
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(groupRepository.save(any(Group.class))).thenReturn(group);

        // Act
        groupService.softDeleteGroup(groupId, systemAdminPrincipal);

        // Assert
        verify(groupRepository).save(argThat(g -> !g.getIsActive()));
    }

    @Test
    void softDeleteGroup_NonAdmin_ThrowsAccessDenied() {
        // Arrange: Set up test data and mock behavior
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of("admin")))).thenReturn(false);

        // Act & Assert: Verify AccessDeniedException is thrown
        assertThrows(AccessDeniedException.class,
                () -> groupService.softDeleteGroup(groupId, teacherPrincipal),
                "Non-admin teacher should not be able to delete group");

        // Assert: Verify interactions
        verify(groupRepository).findByIdAndIsActiveTrue(groupId);
        verify(groupMemberRepository).existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of("admin")));
        verifyNoMoreInteractions(groupRepository, groupMemberRepository);
        verifyNoInteractions(userRepository, schoolRepository);
    }

    @Test
    void softDeleteGroup_GroupNotFound_ThrowsResourceNotFound() {
        // Arrange
        when(groupRepository.findByIdAndIsActiveTrue(groupId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> groupService.softDeleteGroup(groupId, systemAdminPrincipal));
    }

    //================================================================//
    //====================LIST MY GROUPS=============================//
    @Test
    void listMyGroups_Success_SystemAdmin() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Group> page = new PageImpl<>(List.of(group), pageable, 1);
        when(groupRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

        // Act
        PageGroupResponse response = groupService.listMyGroups(systemAdminPrincipal, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("Test Group", response.content().get(0).name());
        assertEquals(1, response.totalElements());
        assertEquals(0, response.pageNumber());
        assertEquals(10, response.pageSize());
    }

    @Test
    void listMyGroups_Success_Teacher() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Group> page = new PageImpl<>(List.of(group), pageable, 1);
        when(groupRepository.findByUserIdAndStatusAndIsActiveTrue(userId, "active", pageable)).thenReturn(page);

        // Act
        PageGroupResponse response = groupService.listMyGroups(teacherPrincipal, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("Test Group", response.content().get(0).name());
        assertEquals(1, response.totalElements());
    }

    @Test
    void listMyGroups_SchoolAdmin_EmptyPage() {
        // Arrange: Set up test data and mock behavior
        Pageable pageable = PageRequest.of(0, 10);
        Page<Group> page = new PageImpl<>(List.of(), pageable, 0);
        when(groupRepository.findBySchoolIdAndIsActiveTrue(userId, pageable)).thenReturn(page);

        // Act: Call the service method
        PageGroupResponse response = groupService.listMyGroups(schoolAdminPrincipal, pageable);

        // Assert: Verify the response
        assertNotNull(response, "Response should not be null");
        assertEquals(0, response.content().size(), "Content should be empty");
        assertEquals(0, response.totalElements(), "Total elements should be 0");
        assertEquals(0, response.pageNumber(), "Page number should be 0");
        assertEquals(10, response.pageSize(), "Page size should be 10");

        // Assert: Verify interactions
        verify(groupRepository).findBySchoolIdAndIsActiveTrue(userId, pageable);
        verifyNoMoreInteractions(groupRepository);
        verifyNoInteractions(userRepository, groupMemberRepository, schoolRepository);
    }

    //================================================================//
    //====================GET MEMBERS BY GROUP ID===================//
    @Test
    void getMembersByGroupId_Success_GroupAdmin() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<GroupMember> page = new PageImpl<>(List.of(groupMember), pageable, 1);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), anyList())).thenReturn(true);
        when(groupMemberRepository.findByGroupId(groupId, pageable)).thenReturn(page);

        // Act
        PageGroupMemberResponse response = groupService.getMembersByGroupId(groupId, teacherPrincipal, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(userId, response.content().get(0).userId());
        assertEquals(GroupRole.ADMIN.name().toLowerCase(), response.content().get(0).role());
    }

    @Test
    void getMembersByGroupId_GroupNotFound_ThrowsResourceNotFound() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> groupService.getMembersByGroupId(groupId, teacherPrincipal, pageable));
    }

    @Test
    void getMembersByGroupId_NonMember_ThrowsAccessDenied() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), anyList())).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> groupService.getMembersByGroupId(groupId, teacherPrincipal, pageable));
    }

    //================================================================//
    //====================IS GROUP ADMIN=============================//
    @Test
    void isGroupAdmin_Success() {
        // Arrange
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())))).thenReturn(true);

        // Act
        boolean isAdmin = groupService.isGroupAdmin(groupId, teacherPrincipal);

        // Assert
        assertTrue(isAdmin);
    }

    @Test
    void isGroupAdmin_NotAdmin_ReturnsFalse() {
        // Arrange
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                eq(groupId), eq(userId), eq("active"), eq(List.of(GroupRole.ADMIN.name().toLowerCase())))).thenReturn(false);

        // Act
        boolean isAdmin = groupService.isGroupAdmin(groupId, teacherPrincipal);

        // Assert
        assertFalse(isAdmin);
    }
}