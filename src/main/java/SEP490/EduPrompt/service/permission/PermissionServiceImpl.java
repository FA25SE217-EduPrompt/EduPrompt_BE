package SEP490.EduPrompt.service.permission;

import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final PromptRepository promptRepository;
    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;

    @Override
    public boolean canAccessPrompt(Prompt prompt, UserPrincipal currentUser) {
        // Check PermissionService
        if (!canViewPrompt(currentUser, prompt)) {
            return false;
        }

        // Additional checks based on visibility
        switch (Visibility.valueOf(prompt.getVisibility())) {
            case PRIVATE:
                // Only owner can access
                return currentUser.getUserId().equals(prompt.getCreatedBy());
            case SCHOOL:
                // Check if user's schoolId matches prompt owner's schoolId
                User promptOwner = userRepository.findById(prompt.getCreatedBy())
                        .orElseThrow(() -> new ResourceNotFoundException("Prompt owner not found"));
                return currentUser.getSchoolId() != null &&
                        currentUser.getSchoolId().equals(promptOwner.getSchoolId());
            case GROUP:
                // Check if user is a member of the group associated with the collection
                if (prompt.getCollection() == null || prompt.getCollection().getGroup() == null) {
                    return false;
                }
                UUID groupId = prompt.getCollection().getGroup().getId();
                return isGroupMember(currentUser, groupId);
            case PUBLIC:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canViewPrompt(UserPrincipal user, Prompt prompt) {
        if (prompt.getIsDeleted() != null && prompt.getIsDeleted()) {
            return isAdmin(user);
        }

        switch (Visibility.valueOf(prompt.getVisibility())) {
            case PUBLIC:
                return true;
            case PRIVATE:
                if (user == null) return false;
                return user.getUserId().equals(prompt.getCreatedBy()) || isAdmin(user);
            case SCHOOL:
                if (user == null) return false;
                //TODO: complete the check
            case GROUP:
                if (user == null) return false;
                //TODO: complete the check
            default:
                return false;
        }
    }

    @Override
    public boolean canEditPrompt(UserPrincipal user, Prompt prompt) {
        if (user == null) return false;
        if (user.getUserId().equals(prompt.getCreatedBy())) return true;
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
        return isAdmin(user) && user.getUsername().equalsIgnoreCase("lord tri nguyen"); //dont ask why
    }

    public boolean isAdmin(UserPrincipal user) {
        if (user == null) return false;
        String r = user.getRole();
        return Role.SYSTEM_ADMIN.name().equalsIgnoreCase(r) || Role.SCHOOL_ADMIN.name().equalsIgnoreCase(r);
    }

    @Override
    public boolean isSchoolAdmin(UserPrincipal user) {
        if (user == null) return false;
        return Role.SCHOOL_ADMIN.name().equalsIgnoreCase(user.getRole());
    }

    @Override
    public boolean isSystemAdmin(UserPrincipal user) {
        if (user == null) return false;
        return Role.SYSTEM_ADMIN.name().equalsIgnoreCase(user.getRole());
    }

    @Override
    public boolean isGroupMember(UserPrincipal user, UUID groupId) {
        if (user == null || groupId == null) return false;
        return groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, user.getUserId(), "active");
    }

    @Override
    public boolean isSchoolMember(UserPrincipal user, UUID schoolId) {
        return user.getSchoolId() != null && user.getSchoolId().equals(schoolId);
    }

    @Override
    public boolean canViewCollection(UserPrincipal userPrincipal, Collection collection) {
        //TODO: finish this function
        return false;
    }

    @Override
    public boolean canCreatePrompt(UserPrincipal user) {
        if (user == null) return false;
        String role = user.getRole();
        return Role.TEACHER.name().equalsIgnoreCase(role)
                || Role.SCHOOL_ADMIN.name().equalsIgnoreCase(role)
                || Role.SYSTEM_ADMIN.name().equalsIgnoreCase(role);
    }

    @Override
    public void validateCollectionVisibility(Collection collection, String promptVisibility) {
        if (collection == null || promptVisibility == null) {
            throw new IllegalArgumentException("Collection and visibility must not be null");
        }

        String collectionVisibility = collection.getVisibility();

        if (collectionVisibility.equalsIgnoreCase(Visibility.PRIVATE.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PRIVATE.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PRIVATE for a PRIVATE collection");
        }
        if (collectionVisibility.equalsIgnoreCase(Visibility.GROUP.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PUBLIC.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.GROUP.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PUBLIC or GROUP for a GROUP collection");
        }
        if (collectionVisibility.equalsIgnoreCase(Visibility.SCHOOL.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PUBLIC.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.SCHOOL.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PUBLIC or SCHOOL for a SCHOOL collection");
        }
        if (collectionVisibility.equalsIgnoreCase(Visibility.PUBLIC.name()) &&
                !promptVisibility.equalsIgnoreCase(Visibility.PUBLIC.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PUBLIC for a PUBLIC collection");
        }
    }

    @Override
    public boolean canAccessCollection(UserPrincipal currentUser, UUID collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
        return canViewCollection(currentUser, collection) ||
                collection.getUser().getId().equals(currentUser.getUserId()) ||
                isAdmin(currentUser);
    }

    @Override
    public boolean canEditCollection(UserPrincipal userPrincipal, Collection collection) {
        if (userPrincipal == null || collection == null) {
            return false;
        }

        // Admin users can edit any collection
        if (isAdmin(userPrincipal)) {
            return true;
        }

        // Collection owner can edit their own collection
        if (userPrincipal.getUserId().equals(collection.getUser().getId())) {
            return true;
        }

        // Check group membership for GROUP visibility collections
        if (Visibility.GROUP.name().equals(collection.getVisibility()) && collection.getGroup() != null) {
            return isGroupMember(userPrincipal, collection.getGroup().getId());
        }

        // For SCHOOL visibility, check if user is from the same school
        if (Visibility.SCHOOL.name().equals(collection.getVisibility())) {
            return isSchoolMember(userPrincipal, collection.getUser().getSchoolId());
        }

//        // For PUBLIC collections, anyone with appropriate role can edit
//        if (Visibility.PUBLIC.name().equals(collection.getVisibility())) {
//            return hasEditCollectionRole(userPrincipal);
//        }

        // For PRIVATE collections, only owner can edit (already checked above)
        return false;
    }

    // Helper method to check if user has roles that allow editing collections
    private boolean hasEditCollectionRole(UserPrincipal userPrincipal) {
        String role = userPrincipal.getRole();
        return Role.TEACHER.name().equalsIgnoreCase(role) ||
                Role.SCHOOL_ADMIN.name().equalsIgnoreCase(role) ||
                Role.SYSTEM_ADMIN.name().equalsIgnoreCase(role);
    }
}
