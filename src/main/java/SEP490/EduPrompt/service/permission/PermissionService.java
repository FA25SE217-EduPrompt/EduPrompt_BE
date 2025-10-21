package SEP490.EduPrompt.service.permission;

import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.service.auth.UserPrincipal;

import java.util.UUID;

public interface PermissionService {
    //generic use
    boolean isAdmin(UserPrincipal user);

    boolean isSchoolAdmin(UserPrincipal user);

    boolean isSystemAdmin(UserPrincipal user);

    boolean isGroupMember(UserPrincipal user, UUID groupId);

    boolean isSchoolMember(UserPrincipal user, UUID schoolId);

    boolean isGroupAdmin(UserPrincipal user, UUID groupId);

    //for collection
    boolean canViewCollection(UserPrincipal userPrincipal, Collection collection);

    boolean canAccessCollection(UserPrincipal currentUser, UUID collectionId);

    boolean canEditCollection(UserPrincipal userPrincipal, Collection collection);

    void validateCollectionVisibility(Collection collection, String promptVisibility);

    //for prompt

    boolean canEditPrompt(UserPrincipal user, Prompt prompt);

    boolean canDeletePrompt(UserPrincipal user, Prompt prompt);

    boolean canViewDeleted(UserPrincipal user);

    boolean canDoAll(UserPrincipal user);

    boolean canFilterPrompt(Prompt prompt, UserPrincipal currentUser);

    boolean canCreatePrompt(UserPrincipal user);

    boolean canAccessPrompt(Prompt prompt, UserPrincipal currentUser);

}