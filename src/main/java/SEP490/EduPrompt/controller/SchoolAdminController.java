package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.school.CreateSchoolRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.BulkAssignTeachersRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.RemoveTeacherFromSchoolRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.school.CreateSchoolResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.BulkAssignTeachersResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolAdminTeacherResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolSubscriptionUsageResponse;
import SEP490.EduPrompt.service.admin.AdminService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school-admin")
@RequiredArgsConstructor
public class SchoolAdminController {

    private final AdminService adminService;

    @PostMapping("/teachers/bulk-assign")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseDto<BulkAssignTeachersResponse> bulkAssignTeachers(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BulkAssignTeachersRequest request) {

        return ResponseDto.success(adminService.bulkAssignTeachersToSchool(principal.getUserId(), request));
    }

    @PostMapping("/schools")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
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