package SEP490.EduPrompt.service.permission;

import SEP490.EduPrompt.enums.GroupRole;
import SEP490.EduPrompt.enums.GroupStatus;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.TeacherProfile;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.CollectionRepository;
import SEP490.EduPrompt.repo.GroupMemberRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final GroupMemberRepository groupMemberRepository;
    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;

    @Override
    public boolean canAccessPrompt(Prompt prompt, UserPrincipal currentUser) {
        // Check PermissionService
        if (prompt.getIsDeleted() != null && prompt.getIsDeleted()) {
            return isAdmin(currentUser);
        }

        // Additional checks based on visibility
        switch (Visibility.valueOf(prompt.getVisibility())) {
            case PRIVATE:
                // Only owner can access
                return currentUser.getUserId().equals(prompt.getUserId());
            case SCHOOL:
                // Check if user's schoolId matches prompt owner's schoolId
                User promptOwner = userRepository.findById(prompt.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("Prompt owner not found"));
                return currentUser.getSchoolId() != null &&
                        currentUser.getSchoolId().equals(promptOwner.getSchoolId());
            case GROUP:
                // Check if user is a member of the group associated with the collection
                Collection collection = collectionRepository.findById(prompt.getCollectionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
                if (collection.getGroupId() == null) {
                    return false;
                }
                UUID groupId = collection.getGroupId();
                return isGroupMember(currentUser, groupId);
            case PUBLIC:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canEditPrompt(UserPrincipal user, Prompt prompt) {
        if (user == null)
            return false;
        if (user.getUserId().equals(prompt.getUserId()))
            return true;
        return isAdmin(user);
    }

    @Override
    public boolean canDeletePrompt(UserPrincipal user, Prompt prompt) {
        return canEditPrompt(user, prompt);
    }

    @Override
    public boolean canViewDeleted(UserPrincipal user) {
        return isAdmin(user);
    }

    @Override
    public boolean canDoAll(UserPrincipal user) {
        return isAdmin(user) && user.getUsername().equalsIgnoreCase("lord tri nguyen"); // dont ask why
    }

    @Override
    public boolean canFilterPrompt(Prompt prompt, UserPrincipal currentUser) {
        // Check PermissionService
        if (prompt.getIsDeleted() != null && prompt.getIsDeleted()) {
            return isAdmin(currentUser);
        }
        return switch (Visibility.valueOf(prompt.getVisibility())) {
            case PRIVATE ->
                // Only owner can access
                    currentUser.getUserId().equals(prompt.getUserId());
            case GROUP, SCHOOL, PUBLIC -> true;
            default -> false;
        };
    }

    public boolean isAdmin(UserPrincipal user) {
        if (user == null)
            return false;
        String r = user.getRole();
        return Role.SYSTEM_ADMIN.name().equalsIgnoreCase(r) || Role.SCHOOL_ADMIN.name().equalsIgnoreCase(r);
    }

    @Override
    public boolean isSchoolAdmin(UserPrincipal user) {
        if (user == null)
            return false;
        return Role.SCHOOL_ADMIN.name().equalsIgnoreCase(user.getRole());
    }

    @Override
    public boolean isSystemAdmin(UserPrincipal user) {
        if (user == null)
            return false;
        return Role.SYSTEM_ADMIN.name().equalsIgnoreCase(user.getRole());
    }

    @Override
    public boolean isGroupMember(UserPrincipal user, UUID groupId) {
        if (user == null || groupId == null)
            return false;
        return groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, user.getUserId(), GroupStatus.ACTIVE.name().toLowerCase());
    }

    @Override
    public boolean isSchoolMember(UserPrincipal user, UUID schoolId) {
        return user.getSchoolId() != null && user.getSchoolId().equals(schoolId);
    }

    @Override
    public boolean isGroupAdmin(UserPrincipal user, UUID groupId) {
        if (user == null || groupId == null)
            return false;
        return groupMemberRepository.existsByGroupIdAndUserIdAndRoleIn(groupId, user.getUserId(),
                List.of(GroupRole.ADMIN.name().toLowerCase()));
    }

    @Override
    public boolean canViewCollection(UserPrincipal userPrincipal, Collection collection) {
        if (collection.getIsDeleted() != null && collection.getIsDeleted()) {
            return isSystemAdmin(userPrincipal);
        }
        switch (Visibility.valueOf(collection.getVisibility())) {
            case PRIVATE:
                // Only owner can access
                return userPrincipal.getUserId().equals(collection.getUserId());
            case SCHOOL:
                // Check if user's schoolId matches prompt owner's schoolId
                User promptOwner = userRepository.findById(collection.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("Prompt owner not found"));
                return userPrincipal.getSchoolId() != null &&
                        userPrincipal.getSchoolId().equals(promptOwner.getSchoolId());
            case GROUP:
                // Check if user is a member of the group associated with the collection
                if (collection.getGroupId() == null) {
                    return false;
                }
                UUID groupId = collection.getGroupId();
                return isGroupMember(userPrincipal, groupId);
            case PUBLIC:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canCreatePrompt(UserPrincipal user) {
        if (user == null)
            return false;
        String role = user.getRole();
        return Role.TEACHER.name().equalsIgnoreCase(role)
                || Role.SCHOOL_ADMIN.name().equalsIgnoreCase(role)
                || Role.SYSTEM_ADMIN.name().equalsIgnoreCase(role);
    }

    @Override
    public void validateCollectionVisibility(Collection collection, String promptVisibility) {
        if (collection == null || promptVisibility == null) {
            throw new InvalidInputException("Collection and visibility must not be null");
        }

        String collectionVisibility = collection.getVisibility();

        if (collectionVisibility.equalsIgnoreCase(Visibility.PRIVATE.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PRIVATE.name())) {
            throw new InvalidInputException("Prompt visibility must be PRIVATE for a PRIVATE collection");
        }
        if (collectionVisibility.equalsIgnoreCase(Visibility.GROUP.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PUBLIC.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.GROUP.name())) {
            throw new InvalidInputException("Prompt visibility must be PUBLIC or GROUP for a GROUP collection! ");
        }
        if (collectionVisibility.equalsIgnoreCase(Visibility.SCHOOL.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PUBLIC.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.SCHOOL.name())) {
            throw new InvalidInputException("Prompt visibility must be PUBLIC or SCHOOL for a SCHOOL collection");
        }
        if (collectionVisibility.equalsIgnoreCase(Visibility.PUBLIC.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PUBLIC.name())) {
            throw new InvalidInputException("Prompt visibility must be PUBLIC for a PUBLIC collection");
        }
    }

    @Override
    public boolean canAccessCollection(UserPrincipal currentUser, UUID collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
        return canViewCollection(currentUser, collection) ||
                (collection.getUserId() != null && collection.getUserId().equals(currentUser.getUserId())) ||
                isSystemAdmin(currentUser);
    }

    @Override
    public boolean canEditCollection(UserPrincipal userPrincipal, Collection collection) {
        if (userPrincipal == null || collection == null) {
            return false;
        }

        // Admin users can edit any collection
        if (isSystemAdmin(userPrincipal)) {
            return true;
        }

        // Collection owner can edit their own collection
        UUID ownerId = collection.getUserId();
        return userPrincipal.getUserId().equals(ownerId);
    }

    public User validateAndGetSchoolAdmin(UUID adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (!Role.SCHOOL_ADMIN.name().equals(admin.getRole())) {
            throw new AccessDeniedException("Only SCHOOL_ADMIN can perform this action");
        }
        if (admin.getSchoolId() == null) {
            throw new AccessDeniedException("School admin has no school assigned");
        }
        return admin;
    }

    public void validatePromptAccess(Prompt prompt, UserPrincipal currentUser) {
        switch (Visibility.valueOf(prompt.getVisibility())) {
            case PRIVATE:
                if (!currentUser.getUserId().equals(prompt.getUserId()) && !isAdmin(currentUser)) {
                    throw new AccessDeniedException("You do not have permission to view this private prompt");
                }
                break;
            case GROUP:
                Collection collection = collectionRepository.findById(prompt.getCollectionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
                if (collection.getGroupId() == null) {
                    throw new ResourceNotFoundException("Group not found for this prompt");
                }
                if (!isGroupMember(currentUser, collection.getGroupId())) {
                    throw new AccessDeniedException("You are not a member of the group associated with this prompt");
                }
                break;
            case SCHOOL:
                if (currentUser.getSchoolId() == null) {
                    throw new AccessDeniedException("You must have a school affiliation to view this prompt");
                }
                User promptOwner = userRepository.findById(prompt.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("Prompt owner not found"));
                if (!currentUser.getSchoolId().equals(promptOwner.getSchoolId())) {
                    throw new AccessDeniedException("You do not belong to the same school as the prompt owner");
                }
                break;
            case PUBLIC:
                // No additional checks needed for public prompts
                break;
            default:
                throw new InvalidInputException("Invalid visibility value: " + prompt.getVisibility());
        }
    }

    @Override
    public void validateTeacherRole(User user) {
        if (!Role.TEACHER.name().equalsIgnoreCase(user.getRole())) {
            throw new AccessDeniedException("You dont have permission to perform this action");
        }
    }

    @Override
    public void validateOwnershipOrAdmin(TeacherProfile profile, UserPrincipal currentUser) {
        UUID ownerId = profile.getUserId();
        UUID currentUserId = currentUser.getUserId();

        if (!ownerId.equals(currentUserId) && !isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission to do this action");
        }
    }
}
