package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.systemAdmin.SchoolSubscriptionResponse;
import SEP490.EduPrompt.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SystemAdminController {

    private final AdminService adminService;

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
}
