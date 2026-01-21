package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptCollectionRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptMetadataRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptVisibilityRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.PageTeacherTokenUsageLogResponse;
import SEP490.EduPrompt.dto.request.systemAdmin.SchoolSubscriptionTokenStatusResponse;
import SEP490.EduPrompt.dto.request.systemAdmin.TeacherTokenMonthlyUsageResponse;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.payment.MonthlyPaymentSummaryResponse;
import SEP490.EduPrompt.dto.response.payment.PagePaymentAdminResponse;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PagePromptAllResponse;
import SEP490.EduPrompt.dto.response.prompt.PagePromptScoreResponse;
import SEP490.EduPrompt.dto.response.systemAdmin.SchoolSubscriptionResponse;
import SEP490.EduPrompt.dto.response.tag.PageTagResponse;
import SEP490.EduPrompt.dto.response.user.PageUserResponse;
import SEP490.EduPrompt.service.admin.AdminService;
import SEP490.EduPrompt.service.admin.SystemAdminService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@Slf4j
public class SystemAdminController {

    private final AdminService adminService;
    private final SystemAdminService sAdminService;

    @PostMapping("/schools/{schoolId}/subscription")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<SchoolSubscriptionResponse> createSubscription(
            @PathVariable UUID schoolId,
            @Valid @RequestBody CreateSchoolSubscriptionRequest request) {

        return ResponseDto.success(adminService.createSchoolSubscription(schoolId, request));
    }

    @PostMapping("/school-admin-acc")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<?> createSchoolAdminAccount(@Valid @RequestBody RegisterRequest registerRequest) {
        return ResponseDto.success(adminService.createSchoolAdminAccount(registerRequest));
    }

    //List all endpoint
    @GetMapping("/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageUserResponse> listAllUsers(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(sAdminService.listAllUser(currentUser, pageable));
    }

    @GetMapping("/collections")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageCollectionResponse> listAllCollections(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(sAdminService.listAllCollection(currentUser, pageable));
    }

    @GetMapping("/groups")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageGroupResponse> listAllGroups(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(sAdminService.listAllGroup(currentUser, pageable));
    }

    @GetMapping("/tags")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageTagResponse> listAllTags(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(sAdminService.listAllTag(currentUser, pageable));
    }

    @GetMapping("/prompts")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PagePromptAllResponse> listAllPrompts(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(sAdminService.listAllPrompt(currentUser, pageable));
    }

    //Create for all
    @PostMapping("/collection")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<CreateCollectionResponse> createCollection(
            @Valid @RequestBody CreateCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        CreateCollectionResponse response = sAdminService.createCollection(request, currentUser);
        return ResponseDto.success(response);
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<CreateGroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        CreateGroupResponse response = sAdminService.createGroup(request, currentUser);
        return ResponseDto.success(response);
    }

    @PostMapping("/prompt/standalone")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<DetailPromptResponse> createStandalonePrompt(
            @Valid @RequestBody CreatePromptRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        DetailPromptResponse response = sAdminService.createStandalonePrompt(request, currentUser);
        return ResponseDto.success(response);
    }

    @PostMapping("/prompt/in-collection")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<DetailPromptResponse> createPromptInCollection(
            @Valid @RequestBody CreatePromptCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        DetailPromptResponse response = sAdminService.createPromptInCollection(request, currentUser);
        return ResponseDto.success(response);
    }

    @PutMapping("/prompt/{promptId}/metadata")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<DetailPromptResponse> updatePromptMetadata(
            @PathVariable UUID promptId,
            @Valid @RequestBody UpdatePromptMetadataRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating metadata for prompt {} by user: {}", promptId, currentUser.getUserId());
        DetailPromptResponse response = sAdminService.updatePromptMetadata(promptId, request, currentUser);
        return ResponseDto.success(response);
    }

    @PutMapping("/prompt/{promptId}/visibility")
    @Operation(summary = "collection id(NULLABLE) is for when the user want to change prompts that is PRIVATE/PUBLIC to GROUP/SCHOOL")
    public ResponseDto<DetailPromptResponse> updatePromptVisibility(
            @PathVariable UUID promptId,
            @Valid @RequestBody UpdatePromptVisibilityRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating visibility for prompt {} by user: {}", promptId, currentUser.getUserId());
        DetailPromptResponse response = sAdminService.updatePromptVisibility(promptId, request, currentUser);
        return ResponseDto.success(response);
    }

    @PutMapping("/collection/{id}")
    public ResponseDto<UpdateCollectionResponse> updateCollection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating collection: {} by user: {}", id, currentUser.getUserId());
        UpdateCollectionResponse response = sAdminService.updateCollection(id, request, currentUser);
        return ResponseDto.success(response);
    }

    @PutMapping("/group/{id}")
    public ResponseDto<?> updateGroup(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGroupRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating group {} by user: {}", id, currentUser.getUserId());
        return ResponseDto.success(sAdminService.updateGroup(id, request, currentUser));
    }

    @DeleteMapping("/prompt/{id}")
    public ResponseDto<Void> softDeletePrompt(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Soft-deleting prompt: {} by user: {}", id, currentUser.getUserId());
        sAdminService.softDeletePrompt(id, currentUser);
        return ResponseDto.success(null);
    }

    @DeleteMapping("/collection/{id}")
    public ResponseDto<String> softDeleteCollection(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Deleting collection: {} by user: {}", id, currentUser.getUserId());
        sAdminService.softDeleteCollection(id, currentUser);
        return ResponseDto.success("Collection deleted successfully");
    }

    @DeleteMapping("/group/{id}")
    public ResponseDto<String> softDeleteGroup(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Deleting group: {} by user: {}", id, currentUser.getUserId());
        sAdminService.softDeleteGroup(id, currentUser);
        return ResponseDto.success("Group deleted successfully");
    }

    @GetMapping("/payments-summary-monthly")
    @Operation(summary = "Get monthly payment summary (admin view)")
    public ResponseDto<List<MonthlyPaymentSummaryResponse>> getMonthlyPaymentSummary(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<MonthlyPaymentSummaryResponse> summary = sAdminService.getMonthlyPaymentSummary(currentUser);
        return ResponseDto.success(summary);
    }

    @GetMapping("/all-payments")
    @Operation(summary = "Get paginated list of all payments (admin view)")
    public ResponseDto<PagePaymentAdminResponse> listAllPayments(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String yearMonth,   // format: 2024-12
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size);
        PagePaymentAdminResponse payments = sAdminService.listAllPayments(
                currentUser, pageable, status, yearMonth);

        return ResponseDto.success(payments);
    }

    // Simple current token status for all active school subscriptions
    @GetMapping("/school-subscriptions-tokens")
    @Operation(summary = "View current token remaining for school subscriptions")
    public ResponseDto<List<SchoolSubscriptionTokenStatusResponse>> getSchoolTokenStatus(
            @RequestParam(required = false) Boolean activeOnly,  // default true
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<SchoolSubscriptionTokenStatusResponse> result =
                sAdminService.getSchoolTokenStatus(currentUser, Boolean.TRUE.equals(activeOnly));
        return ResponseDto.success(result);
    }

    // List all token usage logs (paginated)
    @GetMapping("/teacher-token-usage")
    @Operation(summary = "List all teacher token usage logs (paginated)")
    public ResponseDto<PageTeacherTokenUsageLogResponse> listAllTeacherTokenUsage(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size);
        PageTeacherTokenUsageLogResponse result =
                sAdminService.listAllTeacherTokenUsage(currentUser, pageable);
        return ResponseDto.success(result);
    }

    // List usage logs for one specific school subscription
    @GetMapping("/school-subscriptions/{subscriptionId}/token-usage")
    @Operation(summary = "List teacher token usage for a specific school subscription")
    public ResponseDto<PageTeacherTokenUsageLogResponse> listTokenUsageBySubscription(
            @PathVariable UUID subscriptionId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size);
        PageTeacherTokenUsageLogResponse result =
                sAdminService.listTokenUsageBySubscription(currentUser, subscriptionId, pageable);
        return ResponseDto.success(result);
    }

    // Monthly aggregated usage (all schools)
    @GetMapping("/teacher-token/usage-monthly")
    @Operation(summary = "Monthly summary of teacher token usage (all schools)")
    public ResponseDto<List<TeacherTokenMonthlyUsageResponse>> getMonthlyTokenUsageSummary(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<TeacherTokenMonthlyUsageResponse> result =
                sAdminService.getMonthlyTokenUsageSummary(currentUser);
        return ResponseDto.success(result);
    }

    @GetMapping("/prompts-score")
    public ResponseDto<PagePromptScoreResponse> getRankedPrompts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size);

        PagePromptScoreResponse response = sAdminService.getPromptsWithScores(pageable);
        return ResponseDto.success(response);
    }
}
