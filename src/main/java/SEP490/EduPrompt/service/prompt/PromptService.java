package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.prompt.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface PromptService {

    DetailPromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser);

    DetailPromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser);

    PaginatedDetailPromptResponse getMyPrompts(UserPrincipal currentUser, Pageable pageable);

    PaginatedPromptResponse getNonPrivatePrompts(UserPrincipal currentUser, Pageable pageable);

    PaginatedPromptResponse getPromptsByUserId(UserPrincipal currentUser, Pageable pageable, UUID userId);

    PaginatedPromptResponse getPromptsByCollectionId(UserPrincipal currentUser, Pageable pageable,
            UUID collectionId);

    DetailPromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request,
            UserPrincipal currentUser);

    DetailPromptResponse updatePromptVisibility(UUID promptId, UpdatePromptVisibilityRequest request,
            UserPrincipal currentUser);

    void softDeletePrompt(UUID promptId, UserPrincipal currentUser);

    PaginatedPromptResponse filterPrompts(PromptFilterRequest request, UserPrincipal currentUser,
            Pageable pageable);

    DetailPromptResponse getPromptById(UUID promptId, UserPrincipal currentUser);

    boolean hasUserViewedPrompt(UserPrincipal currentUser, UUID promptId);

    PromptViewLogResponse logPromptView(UserPrincipal currentUser, CreatePromptViewLogRequest request);

    List<PromptViewStatusResponse> hasUserViewedPromptBatch(UserPrincipal currentUser, List<UUID> promptIds);

    PromptVersionResponse createPromptVersion(UUID promptId, CreatePromptVersionRequest request,
            UserPrincipal currentUser);

    List<PromptVersionResponse> getPromptVersions(UUID promptId, UserPrincipal currentUser);

    DetailPromptResponse rollbackToVersion(UUID promptId, UUID versionId, UserPrincipal currentUser);

    String sharePrompt(UUID promptId, UserPrincipal currentUser);

    PromptShareResponse getSharedPrompt(UUID promptId, UUID token);

    void revokeShare(UUID promptId, UserPrincipal currentUser);

    PaginatedGroupSharedPromptResponse getGroupSharedPrompts(UserPrincipal currentUser, Pageable pageable);

    AddPromptToCollectionResponse addPromptToCollection(AddPromptToCollectionRequest request,
            UserPrincipal currentUser);
}
