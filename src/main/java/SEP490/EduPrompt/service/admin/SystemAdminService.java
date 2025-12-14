package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.response.auditLog.PageAuditLogResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.prompt.PagePromptAllResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedDetailPromptResponse;
import SEP490.EduPrompt.dto.response.tag.PageTagResponse;
import SEP490.EduPrompt.dto.response.user.PageUserResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;

public interface SystemAdminService {
    PageUserResponse listAllUser(UserPrincipal currentUser, Pageable pageable);
    PageCollectionResponse listAllCollection(UserPrincipal currentUser, Pageable pageable);
    PageGroupResponse listAllGroup(UserPrincipal currentUser, Pageable pageable);
    PageTagResponse listAllTag(UserPrincipal currentUser, Pageable pageable);
    PagePromptAllResponse listAllPrompt(UserPrincipal currentUser, Pageable pageable);
}
