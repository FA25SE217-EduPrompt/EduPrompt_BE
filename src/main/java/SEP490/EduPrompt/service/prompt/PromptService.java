package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.prompt.GetPaginatedPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromptService {

    PromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser);

    PromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser);

    PaginatedPromptResponse getMyPrompts(UserPrincipal currentUser, Pageable pageable);

    GetPaginatedPromptResponse getNonPrivatePrompts(UserPrincipal currentUser, Pageable pageable);

    GetPaginatedPromptResponse getPromptsByUserId(UserPrincipal currentUser, Pageable pageable, UUID userId);

    GetPaginatedPromptResponse getPromptsByCollectionId(UserPrincipal currentUser, Pageable pageable, UUID collectionId);

    PromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request, UserPrincipal currentUser);

    PromptResponse updatePromptVisibility(UUID promptId, UpdatePromptVisibilityRequest request, UserPrincipal currentUser);

    void softDeletePrompt(UUID promptId, UserPrincipal currentUser);

    PaginatedPromptResponse filterPrompts(PromptFilterRequest request, UserPrincipal currentUser, Pageable pageable);

    PromptResponse getPromptById(UUID promptId, UserPrincipal currentUser);


}
