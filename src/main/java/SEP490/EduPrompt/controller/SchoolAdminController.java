package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.school.CreateSchoolRequest;
import SEP490.EduPrompt.dto.request.school.SchoolEmailRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.RemoveTeacherFromSchoolRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.school.CreateSchoolResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolAdminTeacherResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolSubscriptionUsageResponse;
import SEP490.EduPrompt.service.admin.AdminService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school-admin")
@RequiredArgsConstructor
public class SchoolAdminController {

    private final AdminService adminService;

    @PostMapping("/{schoolId}/new-email")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<?> addEmails(
            @Valid @RequestBody SchoolEmailRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        adminService.addEmailsToSchool(principal, request);
        return ResponseDto.success("Successfully added emails");
    }

    @PostMapping("/schools")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN')")
    public ResponseDto<CreateSchoolResponse> createSchool(@Valid @RequestBody CreateSchoolRequest request) {
        CreateSchoolResponse response = adminService.createSchool(request);
        return ResponseDto.success(response);
    }

    @GetMapping("/subscription/usage")
    public ResponseDto<SchoolSubscriptionUsageResponse> getSubscriptionUsage(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseDto.success(adminService.getSubscriptionUsage(principal.getUserId()));
    }

    @GetMapping("/teachers")
    public ResponseDto<Page<SchoolAdminTeacherResponse>> getTeachers(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        return ResponseDto.success(adminService.getTeachersInSchool(principal.getUserId(), pageable));
    }

    @DeleteMapping("/teachers/remove")
    public ResponseDto<?> removeTeacher(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RemoveTeacherFromSchoolRequest request) {
        adminService.removeTeacherFromSchool(principal.getUserId(), request);
        return ResponseDto.success("Teacher removed from school successfully");
    }
}