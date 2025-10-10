package SEP490.EduPrompt.service.group;

import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.response.group.GroupResponse;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.UpdateGroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
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
    private final SchoolRepository schoolRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

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
                group.getId(), currentUserId, "active", List.of("admin", "member")
        );
        if (!isMember) {
            throw new AccessDeniedException("You are not an active member of this group");
        }
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

        // SCHOOL_ADMIN and TEACHER must belong to a school
        UUID schoolId = currentUser.getSchoolId();
        if (schoolId == null && !Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
            throw new AccessDeniedException("You must be associated with a school to create a group");
        }
        School school = null;
        if (schoolId != null) {
            school = schoolRepository.findById(schoolId)
                    .orElseThrow(() -> new ResourceNotFoundException("School not found"));
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
                .role("admin")
                .status("active")
                .joinedAt(Instant.now())
                .build());

        return CreateGroupResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .schoolId(saved.getSchool() != null ? saved.getSchool().getId() : null)
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
                id, currentUserId, "active", List.of("admin")
        );
        if (!isGroupAdmin && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Only group admins or system/school admins can update this group");
        }

        // SCHOOL_ADMIN can only update groups in their school
        if (Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
            UUID currentSchoolId = currentUser.getSchoolId();
            UUID groupSchoolId = group.getSchool() != null ? group.getSchool().getId() : null;
            if (groupSchoolId == null || !groupSchoolId.equals(currentSchoolId)) {
                throw new AccessDeniedException("You can only update groups in your school");
            }
        }

        // Partial update
        if (req.name() != null) {
            group.setName(req.name());
        }
        group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
        group.setUpdatedAt(Instant.now());

        Group updatedGroup = groupRepository.save(group);
        log.info("Group updated: {} by user: {}", id, currentUserId);

        return UpdateGroupResponse.builder()
                .id(updatedGroup.getId())
                .name(updatedGroup.getName())
                .schoolId(updatedGroup.getSchool() != null ? updatedGroup.getSchool().getId() : null)
                .isActive(updatedGroup.getIsActive())
                .updatedAt(updatedGroup.getUpdatedAt())
                .build();
    }

    @Override
    public GroupResponse getGroupById(UUID id, UserPrincipal currentUser) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        validateAccess(group, currentUser);

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .schoolId(group.getSchool() != null ? group.getSchool().getId() : null)
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
                id, currentUserId, "active", List.of("admin")
        );
        if (!isGroupAdmin && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Only group admins or system/school admins can delete this group");
        }

        // SCHOOL_ADMIN can only delete groups in their school
        if (Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
            UUID currentSchoolId = currentUser.getSchoolId();
            UUID groupSchoolId = group.getSchool() != null ? group.getSchool().getId() : null;
            if (groupSchoolId == null || !groupSchoolId.equals(currentSchoolId)) {
                throw new AccessDeniedException("You can only delete groups in your school");
            }
        }

        group.setIsActive(false);
        group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
        group.setUpdatedAt(Instant.now());

        groupRepository.save(group);
        log.info("Group soft-deleted: {} by user: {}", id, currentUserId);
    }

    @Override
    public PageGroupResponse listMyGroups(UserPrincipal currentUser, Pageable pageable) {
        UUID currentUserId = currentUser.getUserId();
        Page<Group> page;

        if (isAdmin(currentUser)) {
            // Admins see all groups (SYSTEM_ADMIN) or school groups (SCHOOL_ADMIN)
            if (Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
                page = groupRepository.findAllByOrderByCreatedAtDesc(pageable);
            } else {
                page = groupRepository.findBySchoolIdAndIsActiveTrue(currentUser.getSchoolId(), pageable);
            }
        } else {
            // Non-admins see groups they are active members of
            page = groupRepository.findByUserIdAndStatusAndIsActiveTrue(currentUserId, "active", pageable);
        }

        List<GroupResponse> content = page.getContent().stream()
                .map(group -> GroupResponse.builder()
                        .id(group.getId())
                        .name(group.getName())
                        .schoolId(group.getSchool() != null ? group.getSchool().getId() : null)
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
}
