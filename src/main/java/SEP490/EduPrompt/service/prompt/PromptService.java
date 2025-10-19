package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptCollectionRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptMetadataRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptVisibilityRequest;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromptService {

    PromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser);

    PromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser);

    PaginatedPromptResponse getPrivatePrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId);

    PaginatedPromptResponse getSchoolPrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId);

    PaginatedPromptResponse getGroupPrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId);

    PaginatedPromptResponse getPublicPrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId);

    PaginatedPromptResponse getPromptsByCreatedAtAsc(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId);

    PaginatedPromptResponse getPromptsByUpdatedAtAsc(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId);

    PaginatedPromptResponse getPromptsByUserId(UserPrincipal currentUser, Pageable pageable, UUID userId);

    PaginatedPromptResponse getPromptsByCollectionId(UserPrincipal currentUser, Pageable pageable, UUID collectionId);

    PromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request, UserPrincipal currentUser);

    PromptResponse updatePromptVisibility(UUID promptId, UpdatePromptVisibilityRequest request, UserPrincipal currentUser);

}
