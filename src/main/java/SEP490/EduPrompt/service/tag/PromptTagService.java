package SEP490.EduPrompt.service.tag;

import SEP490.EduPrompt.dto.request.tag.AddTagsToPromptRequest;
import SEP490.EduPrompt.dto.request.tag.RemoveTagFromPromptRequest;
import SEP490.EduPrompt.dto.response.tag.BatchAddResultResponse;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;

import java.util.List;
import java.util.UUID;

public interface PromptTagService {
    BatchAddResultResponse addTags(UUID promptId, AddTagsToPromptRequest request, UserPrincipal user);

    void removeTag(UUID promptId, RemoveTagFromPromptRequest request, UserPrincipal user);

    List<TagResponse> getTagsForPrompt(UUID promptId, UserPrincipal user);
}
