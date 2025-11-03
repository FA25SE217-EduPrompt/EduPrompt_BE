package SEP490.EduPrompt.service.tag;

import SEP490.EduPrompt.dto.request.tag.AddTagsToCollectionRequest;
import SEP490.EduPrompt.dto.request.tag.RemoveTagFromCollectionRequest;
import SEP490.EduPrompt.dto.response.tag.BatchAddResultResponse;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;

import java.util.List;
import java.util.UUID;

public interface CollectionTagService {
    BatchAddResultResponse addTags(UUID collectionId, AddTagsToCollectionRequest request, UserPrincipal user);

    void removeTag(UUID collectionId, RemoveTagFromCollectionRequest request, UserPrincipal user);

    List<TagResponse> getTagsForCollection(UUID collectionId, UserPrincipal user);
}
