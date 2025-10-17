package SEP490.EduPrompt.service.permission;

import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.repo.CollectionRepository;
import SEP490.EduPrompt.repo.GroupMemberRepository;
import SEP490.EduPrompt.repo.GroupRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private GroupMemberRepository groupMemberRepository;
    private GroupRepository groupRepository;
    private PromptRepository promptRepository;
    private CollectionRepository collectionRepository;

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
        //TODO: finish this function
        return false;
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

        if (collectionVisibility.equals(Visibility.PRIVATE.name()) &&
                !promptVisibility.equals(Visibility.PRIVATE.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PRIVATE for a PRIVATE collection");
        }
        if (collectionVisibility.equals(Visibility.GROUP.name()) &&
                !promptVisibility.equals(Visibility.PUBLIC.name()) &&
                !promptVisibility.equals(Visibility.GROUP.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PUBLIC or GROUP for a GROUP collection");
        }
        if (collectionVisibility.equals(Visibility.SCHOOL.name()) &&
                !promptVisibility.equals(Visibility.PUBLIC.name()) &&
                !promptVisibility.equals(Visibility.SCHOOL.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PUBLIC or SCHOOL for a SCHOOL collection");
        }
        if (collectionVisibility.equals(Visibility.PUBLIC.name()) &&
                !promptVisibility.equals(Visibility.PUBLIC.name())) {
            throw new IllegalArgumentException("Prompt visibility must be PUBLIC for a PUBLIC collection");
        }
    }
}
