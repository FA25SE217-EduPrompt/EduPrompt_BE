package SEP490.EduPrompt.service.group;

import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.request.groupMember.AddGroupMembersRequest;
import SEP490.EduPrompt.dto.request.groupMember.RemoveGroupMemberRequest;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.GroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.group.UpdateGroupResponse;
import SEP490.EduPrompt.dto.response.groupMember.GroupMemberResponse;
import SEP490.EduPrompt.dto.response.groupMember.PageGroupMemberResponse;
import SEP490.EduPrompt.enums.GroupRole;
import SEP490.EduPrompt.enums.GroupStatus;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service implementation for managing groups.
 * <p>
 * Responsibilities:
 * - Create, update, retrieve, and soft-delete groups.
 * - Enforce access control rules based on user roles and group membership.
 * <p>
 * Access Rules:
 * - SYSTEM_ADMIN: Can manage any group.
 * - SCHOOL_ADMIN: Can manage groups within their school.
 * - TEACHER: Can create groups in their school, manage groups where they are group admin, and view groups where they are a member.
 * - Soft-deleted groups (isActive = false) are only accessible to SYSTEM_ADMIN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SchoolRepository  schoolRepository;
    private final UserRepository userRepository;

    @Value("${group.max-size:100}")
    private int maxGroupSize;

    @Value("${group.max-per-school:500}")
    private int maxGroupsPerSchool;

    @Value("${group.max-per-user:50}")
    private int maxGroupsPerUser;

    private boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        return Role.SYSTEM_ADMIN.name().equalsIgnoreCase(role) || Role.SCHOOL_ADMIN.name().equalsIgnoreCase(role);
    }

    private void validateAccess(Group group, UserPrincipal currentUser) {
        String currentRole = currentUser.getRole();
        UUID currentUserId = currentUser.getUserId();

        // Inactive groups are only accessible to SYSTEM_ADMIN
        if (!group.getIsActive()) {
            if (!Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentRole)) {
                throw new AccessDeniedException("You do not have permission to access this group");
            }
            return;
        }

        // Admins bypass restrictions
        if (isAdmin(currentUser)) {
            // SCHOOL_ADMIN can only access groups in their school
            if (Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentRole)) {
                UUID currentSchoolId = currentUser.getSchoolId();
                UUID groupSchoolId = group.getSchool() != null ? group.getSchool().getId() : null;
                if (groupSchoolId == null || !groupSchoolId.equals(currentSchoolId)) {
                    throw new AccessDeniedException("You can only access groups in your school");
                }
            }
            return;
        }

        // Non-admins must be active group members
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                group.getId(), currentUserId, "active", List.of(GroupRole.ADMIN.name().toLowerCase(), GroupRole.MEMBER.name().toLowerCase())
        );
        if (!isMember) {
            throw new AccessDeniedException("You are not an active member of this group");
        }
    }

    @Override
    public boolean isGroupAdmin(UUID groupId, UserPrincipal currentUser) {
        return groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                groupId, currentUser.getUserId(), "active", List.of(GroupRole.ADMIN.name().toLowerCase())
        );
    }

    @Override
    @Transactional
    public CreateGroupResponse createGroup(CreateGroupRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        User creator = userRepository.getReferenceById(currentUserId);

        // Only admins or teachers in a school can create groups
        if (!isAdmin(currentUser) && !Role.TEACHER.name().equalsIgnoreCase(currentUser.getRole())) {
            throw new AccessDeniedException("Only admins or teachers can create groups");
        }

        UUID schoolId = currentUser.getSchoolId();
        School school = null;
        if (schoolId != null) {
            school = schoolRepository.findById(schoolId)
                    .orElseThrow(() -> new ResourceNotFoundException("School not found"));
            // Check max groups per school
            long schoolGroupCount = groupRepository.countBySchoolIdAndIsActiveTrue(schoolId);
            if (schoolGroupCount >= maxGroupsPerSchool) {
                throw new InvalidActionException("Maximum number of groups per school reached");
            }
        }

        long userGroupCount = groupRepository.countByCreatedByIdAndIsActiveTrue(currentUserId);
        if (userGroupCount >= maxGroupsPerUser) {
            throw new InvalidActionException("Maximum number of groups per user reached");
        }

        Group group = Group.builder()
                .name(req.name())
                .school(school)
                .createdBy(creator)
                .updatedBy(creator)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Group saved = groupRepository.save(group);
        log.info("Group created: {} by user: {}", saved.getId(), currentUserId);

        // Automatically add creator as group admin
        groupMemberRepository.save(GroupMember.builder()
                .group(saved)
                .user(creator)
                .role(GroupRole.ADMIN.name().toLowerCase())
                .status(GroupStatus.ACTIVE.name().toLowerCase())
                .joinedAt(Instant.now())
                .build());

        return CreateGroupResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .schoolId(saved.getSchool() != null ? saved.getSchoolId() : null)
                .isActive(saved.getIsActive())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public UpdateGroupResponse updateGroup(UUID id, UpdateGroupRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        Group group = groupRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        // Only group admins or system/school admins can update
        boolean isGroupAdmin = groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                id, currentUserId, GroupStatus.ACTIVE.name().toLowerCase(), List.of(GroupRole.ADMIN.name().toLowerCase())
        );
        if (!isGroupAdmin && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Only group admins or system/school admins can update this group");
        }

        // SCHOOL_ADMIN can only update groups in their school
        if (Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
            UUID currentSchoolId = currentUser.getSchoolId();
            UUID groupSchoolId = group.getSchool() != null ? group.getSchoolId() : null;
            if (groupSchoolId == null || !groupSchoolId.equals(currentSchoolId)) {
                throw new AccessDeniedException("You can only update groups in your school");
            }
        }

        // Track if any changes were made
        boolean updated = false;

        // Update name if provided
        if (req.name() != null && !req.name().isBlank()) {
            group.setName(req.name());
            updated = true;
        }

        // Update isActive if provided
        if (req.isActive() != null && !req.isActive().equals(group.getIsActive())) {
            group.setIsActive(req.isActive());
            updated = true;
        }

        // Update group metadata if changes were made
        if (updated) {
            group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
            group.setUpdatedAt(Instant.now());
            groupRepository.save(group);
            log.info("Group updated: {} by user: {}", id, currentUserId);
        } else {
            log.info("No changes applied to group: {} by user: {}", id, currentUserId);
        }

        return UpdateGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .schoolId(group.getSchool() != null ? group.getSchoolId() : null)
                .isActive(group.getIsActive())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public UpdateGroupResponse addMembersToGroup(UUID id, AddGroupMembersRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        Group group = groupRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        // Only group admins or system/school admins can add members
        boolean isGroupAdmin = groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                id, currentUserId, GroupStatus.ACTIVE.name().toLowerCase(), List.of(GroupRole.ADMIN.name().toLowerCase())
        );
        if (!isGroupAdmin && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Only group admins or system/school admins can add members to this group");
        }

        // SCHOOL_ADMIN can only manage groups in their school
        if (Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
            UUID currentSchoolId = currentUser.getSchoolId();
            UUID groupSchoolId = group.getSchool() != null ? group.getSchoolId() : null;
            if (groupSchoolId == null || !groupSchoolId.equals(currentSchoolId)) {
                throw new AccessDeniedException("You can only manage groups in your school");
            }
        }

        long currentMemberCount = groupMemberRepository.countByGroupIdAndStatus(id, GroupStatus.ACTIVE.name().toLowerCase());
        if (currentMemberCount + req.members().size() > maxGroupSize) {
            throw new InvalidActionException("Maximum group size reached");
        }

        UUID groupSchoolId = group.getSchool() != null ? group.getSchoolId() : null;

        for (AddGroupMembersRequest.MemberRequest memberRequest : req.members()) {
            User user = userRepository.findById(memberRequest.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + memberRequest.userId()));

            if (groupSchoolId != null && !groupSchoolId.equals(user.getSchoolId())) {
                throw new InvalidActionException("User must belong to the same school as the group");
            }

            GroupMember existingMember = groupMemberRepository.findByGroupIdAndUserId(group.getId(), memberRequest.userId())
                    .orElse(null);

            if (existingMember == null) {
                GroupMember newMember = GroupMember.builder()
                        .group(group)
                        .user(user)
                        .role(GroupRole.MEMBER.name().toLowerCase())
                        .status(GroupStatus.ACTIVE.name().toLowerCase())
                        .joinedAt(Instant.now())
                        .build();
                groupMemberRepository.save(newMember);
                log.info("Added member {} to group {}", memberRequest.userId(), id);
            } else {
                throw new InvalidActionException("User already in this group!");
            }
        }
        group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
        log.info("Group members updated for group: {} by user: {}", id, currentUserId);

        return UpdateGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .schoolId(group.getSchool() != null ? group.getSchoolId() : null)
                .isActive(group.getIsActive())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public void removeMemberFromGroup(UUID id, RemoveGroupMemberRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        Group group = groupRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        // Only group admins or system/school admins can remove members
        boolean isGroupAdmin = groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                id, currentUserId, GroupStatus.ACTIVE.name().toLowerCase(), List.of(GroupRole.ADMIN.name().toLowerCase())
        );
        if (!isGroupAdmin && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Only group admins or system/school admins can remove members from this group");
        }

        // SCHOOL_ADMIN can only manage groups in their school
        if (Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
            UUID currentSchoolId = currentUser.getSchoolId();
            UUID groupSchoolId = group.getSchool() != null ? group.getSchoolId() : null;
            if (groupSchoolId == null || !groupSchoolId.equals(currentSchoolId)) {
                throw new AccessDeniedException("You can only manage groups in your school");
            }
        }

        // Prevent removing the creator or the last admin
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(group.getId(), req.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in group: " + req.userId()));
        if (group.getCreatedBy().getId().equals(req.userId())) {
            throw new InvalidActionException("Cannot remove the group creator");
        }
        if (GroupRole.ADMIN.name().equalsIgnoreCase(member.getRole())) {
            long adminCount = groupMemberRepository.countByGroupIdAndRoleAndStatus(
                    id,
                    GroupRole.ADMIN.name().toLowerCase(),
                    GroupStatus.ACTIVE.name().toLowerCase()
            );
            if (adminCount <= 1) {
                throw new InvalidActionException("Cannot remove the last active admin of the group");
            }
        }

        // Remove the member
        groupMemberRepository.delete(member);
        group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
        log.info("Removed member {} from group {} by user: {}", req.userId(), id, currentUserId);
    }

    @Override
    public GroupResponse getGroupById(UUID id, UserPrincipal currentUser) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        validateAccess(group, currentUser);

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .schoolId(group.getSchool() != null ? group.getSchoolId() : null)
                .isActive(group.getIsActive())
                .createdAt(group.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void softDeleteGroup(UUID id, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        Group group = groupRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        // Only group admins or system/school admins can delete
        boolean isGroupAdmin = groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                id, currentUserId, GroupStatus.ACTIVE.name().toLowerCase(), List.of(GroupRole.ADMIN.name().toLowerCase())
        );
        if (!isGroupAdmin && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Only group admins or system/school admins can delete this group");
        }

        // SCHOOL_ADMIN can only delete groups in their school
        if (Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
            UUID currentSchoolId = currentUser.getSchoolId();
            UUID groupSchoolId = group.getSchool() != null ? group.getSchoolId(): null;
            if (groupSchoolId == null || !groupSchoolId.equals(currentSchoolId)) {
                throw new InvalidActionException("You can only delete groups in your school");
            }
        }

        group.setIsActive(false);
        group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
        group.setUpdatedAt(Instant.now());

        groupRepository.save(group);
        groupMemberRepository.updateStatusByGroupId(id, GroupStatus.REMOVED.name());

        log.info("Group soft-deleted: {} by user: {}", id, currentUserId);
    }

    @Override
    public PageGroupResponse listMyGroups(UserPrincipal currentUser, Pageable pageable) {
        UUID currentUserId = currentUser.getUserId();
        Page<Group> page;

        if (isAdmin(currentUser)) {
            if (Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
                page = groupRepository.findAllByOrderByCreatedAtDesc(pageable);
            } else {
                page = groupRepository.findBySchoolIdAndIsActiveTrue(currentUser.getSchoolId(), pageable);
            }
        } else if (Role.TEACHER.name().equalsIgnoreCase(currentUser.getRole())) {
            page = groupRepository.findBySchoolIdAndIsActiveTrue(currentUser.getSchoolId(), pageable);
        } else {
            page = groupRepository.findByUserIdAndStatusAndIsActiveTrue(currentUserId, GroupStatus.ACTIVE.name().toLowerCase(), pageable);
        }

        List<GroupResponse> content = page.getContent().stream()
                .map(group -> GroupResponse.builder()
                        .id(group.getId())
                        .name(group.getName())
                        .schoolId(group.getSchool() != null ? group.getSchoolId() : null)
                        .isActive(group.getIsActive())
                        .createdAt(group.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PageGroupResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    @Transactional
    public PageGroupMemberResponse getMembersByGroupId(UUID id, UserPrincipal currentUser, Pageable pageable) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        validateAccess(group, currentUser);

        Page<GroupMember> memberPage = groupMemberRepository.findByGroupIdAndStatus(id, GroupStatus.ACTIVE.name().toLowerCase(), pageable);
        log.info("Retrieved {} members for group {} by user: {}, page: {}",
                memberPage.getContent().size(), id, currentUser.getUserId(), pageable.getPageNumber());

        List<GroupMemberResponse> content = memberPage.getContent().stream()
                .map(member -> new GroupMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getFirstName(),
                        member.getUser().getLastName(),
                        member.getUser().getEmail(),
                        member.getRole(),
                        member.getStatus(),
                        member.getJoinedAt()
                ))
                .collect(Collectors.toList());

        return new PageGroupMemberResponse(
                content,
                memberPage.getTotalElements(),
                memberPage.getTotalPages(),
                memberPage.getNumber(),
                memberPage.getSize()
        );
    }
}
