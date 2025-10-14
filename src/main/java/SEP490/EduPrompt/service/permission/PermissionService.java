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

    //for collection
    boolean canViewCollection(UserPrincipal userPrincipal, Collection collection);


    //for prompt
    boolean canViewPrompt(UserPrincipal user, Prompt prompt);
    boolean canEditPrompt(UserPrincipal user, Prompt prompt);
    boolean canDeletePrompt(UserPrincipal user, Prompt prompt);
    boolean canViewDeleted(UserPrincipal user);

    boolean canDoAll(UserPrincipal user);
}