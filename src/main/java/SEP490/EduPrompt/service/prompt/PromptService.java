package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedDetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptViewLogResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromptService {

    DetailPromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser);

    DetailPromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser);

    PaginatedDetailPromptResponse getMyPrompts(UserPrincipal currentUser, Pageable pageable);

    PaginatedPromptResponse getNonPrivatePrompts(UserPrincipal currentUser, Pageable pageable);

    PaginatedPromptResponse getPromptsByUserId(UserPrincipal currentUser, Pageable pageable, UUID userId);

    PaginatedPromptResponse getPromptsByCollectionId(UserPrincipal currentUser, Pageable pageable, UUID collectionId);

    DetailPromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request, UserPrincipal currentUser);

    DetailPromptResponse updatePromptVisibility(UUID promptId, UpdatePromptVisibilityRequest request, UserPrincipal currentUser);

    void softDeletePrompt(UUID promptId, UserPrincipal currentUser);

    PaginatedPromptResponse filterPrompts(PromptFilterRequest request, UserPrincipal currentUser, Pageable pageable);

    DetailPromptResponse getPromptById(UUID promptId, UserPrincipal currentUser);

    boolean hasUserViewedPrompt(UserPrincipal currentUser, UUID promptId);

    PromptViewLogResponse logPromptView(UserPrincipal currentUser, CreatePromptViewLogRequest request);
}
