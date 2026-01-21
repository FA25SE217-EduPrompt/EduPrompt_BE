package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptCollectionRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptMetadataRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptVisibilityRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.PageTeacherTokenUsageLogResponse;
import SEP490.EduPrompt.dto.request.systemAdmin.SchoolSubscriptionTokenStatusResponse;
import SEP490.EduPrompt.dto.request.systemAdmin.TeacherTokenMonthlyUsageResponse;
import SEP490.EduPrompt.dto.request.tag.CreateTagBatchRequest;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.group.UpdateGroupResponse;
import SEP490.EduPrompt.dto.response.payment.MonthlyPaymentSummaryResponse;
import SEP490.EduPrompt.dto.response.payment.PagePaymentAdminResponse;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PagePromptAllResponse;
import SEP490.EduPrompt.dto.response.prompt.PagePromptScoreResponse;
import SEP490.EduPrompt.dto.response.tag.PageTagResponse;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.dto.response.user.PageUserResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SystemAdminService {
    //List all
    PageUserResponse listAllUser(UserPrincipal currentUser, Pageable pageable);

    PageCollectionResponse listAllCollection(UserPrincipal currentUser, Pageable pageable);

    PageGroupResponse listAllGroup(UserPrincipal currentUser, Pageable pageable);

    PageTagResponse listAllTag(UserPrincipal currentUser, Pageable pageable);

    PagePromptAllResponse listAllPrompt(UserPrincipal currentUser, Pageable pageable);

    //Create
    CreateCollectionResponse createCollection(CreateCollectionRequest req, UserPrincipal currentUser);

    CreateGroupResponse createGroup(CreateGroupRequest req, UserPrincipal currentUser);

    DetailPromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser);

    DetailPromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser);

    List<TagResponse> createBatch(CreateTagBatchRequest request);

    //Update
    DetailPromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request,
                                              UserPrincipal currentUser);

    DetailPromptResponse updatePromptVisibility(UUID promptId, UpdatePromptVisibilityRequest request,
                                                UserPrincipal currentUser);

    UpdateCollectionResponse updateCollection(UUID id, UpdateCollectionRequest request, UserPrincipal currentUser);

    UpdateGroupResponse updateGroup(UUID id, UpdateGroupRequest req, UserPrincipal currentUser);

    //Delete
    void softDeletePrompt(UUID promptId, UserPrincipal currentUser);

    void softDeleteCollection(UUID id, UserPrincipal currentUser);

    void softDeleteGroup(UUID id, UserPrincipal currentUser);

    //
    List<MonthlyPaymentSummaryResponse> getMonthlyPaymentSummary(UserPrincipal currentUser);

    PagePaymentAdminResponse listAllPayments(UserPrincipal currentUser, Pageable pageable, String status, String yearMonth);

    List<SchoolSubscriptionTokenStatusResponse> getSchoolTokenStatus(UserPrincipal currentUser, boolean activeOnly);

    PageTeacherTokenUsageLogResponse listAllTeacherTokenUsage(UserPrincipal currentUser, Pageable pageable);

    PageTeacherTokenUsageLogResponse listTokenUsageBySubscription(
            UserPrincipal currentUser, UUID subscriptionId, Pageable pageable);

    List<TeacherTokenMonthlyUsageResponse> getMonthlyTokenUsageSummary(UserPrincipal currentUser);

    PagePromptScoreResponse getPromptsWithScores(Pageable pageable);
}
