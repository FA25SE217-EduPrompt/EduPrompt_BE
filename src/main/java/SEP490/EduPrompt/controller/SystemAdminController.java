package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.prompt.PagePromptAllResponse;
import SEP490.EduPrompt.dto.response.systemAdmin.SchoolSubscriptionResponse;
import SEP490.EduPrompt.dto.response.tag.PageTagResponse;
import SEP490.EduPrompt.dto.response.user.PageUserResponse;
import SEP490.EduPrompt.service.admin.AdminService;
import SEP490.EduPrompt.service.admin.SystemAdminService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
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

    @GetMapping("/users")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageUserResponse> listAllUsers(
            @AuthenticationPrincipal UserPrincipal currentUser,
            Pageable pageable) {
        return ResponseDto.success(sAdminService.listAllUser(currentUser, pageable));
    }

    @GetMapping("/collections")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageCollectionResponse> listAllCollections(
            @AuthenticationPrincipal UserPrincipal currentUser,
            Pageable pageable) {
        return ResponseDto.success(sAdminService.listAllCollection(currentUser, pageable));
    }

    @GetMapping("/groups")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageGroupResponse> listAllGroups(
            @AuthenticationPrincipal UserPrincipal currentUser,
            Pageable pageable) {
        return ResponseDto.success(sAdminService.listAllGroup(currentUser, pageable));
    }

    @GetMapping("/tags")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PageTagResponse> listAllTags(
            @AuthenticationPrincipal UserPrincipal currentUser,
            Pageable pageable) {
        return ResponseDto.success(sAdminService.listAllTag(currentUser, pageable));
    }

    @GetMapping("/prompts")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseDto<PagePromptAllResponse> listAllPrompts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            Pageable pageable) {
        return ResponseDto.success(sAdminService.listAllPrompt(currentUser, pageable));
    }
}
