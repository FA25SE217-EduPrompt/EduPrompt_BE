package SEP490.EduPrompt.service.group;

import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.response.group.GroupResponse;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.group.UpdateGroupResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GroupService {

    CreateGroupResponse createGroup(CreateGroupRequest req, UserPrincipal currentUser);

    UpdateGroupResponse updateGroup(UUID id, UpdateGroupRequest req, UserPrincipal currentUser);

    GroupResponse getGroupById(UUID id, UserPrincipal currentUser);

    void softDeleteGroup(UUID id, UserPrincipal currentUser);

    PageGroupResponse listMyGroups(UserPrincipal currentUser, Pageable pageable);
}