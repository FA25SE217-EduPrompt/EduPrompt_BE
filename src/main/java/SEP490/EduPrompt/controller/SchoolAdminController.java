package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.school.CreateSchoolRequest;
import SEP490.EduPrompt.dto.request.school.SchoolEmailRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.RemoveTeacherFromSchoolRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.school.CreateSchoolResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolAdminTeacherResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolSubscriptionUsageResponse;
import SEP490.EduPrompt.dto.response.teacherTokenUsed.PaginatedTeacherTokenUsageLogResponse;
import SEP490.EduPrompt.service.admin.AdminService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/school-admin")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN')")
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
    public ResponseDto<CreateSchoolResponse> createSchool(@Valid @RequestBody CreateSchoolRequest request) {
        CreateSchoolResponse response = adminService.createSchool(request);
        return ResponseDto.success(response);
    }

    @GetMapping("/subscription/usage")
    public ResponseDto<SchoolSubscriptionUsageResponse> getSubscriptionUsage(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseDto.success(adminService.getSubscriptionUsage(principal.getUserId()));
    }

    @GetMapping("/teachers/all")
    public ResponseDto<Page<SchoolAdminTeacherResponse>> getTeachers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(adminService.getTeachersInSchool(principal.getUserId(), pageable));
    }

    @GetMapping("/subscription/usage/all-teacher")
    public ResponseDto<PaginatedTeacherTokenUsageLogResponse> getTokenUsageByAllUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        Pageable pageable = PageRequest.of(page, size);

        PaginatedTeacherTokenUsageLogResponse response =
                adminService.getTokenUsageLogsBySchool(principal.getUserId(), pageable);

        return ResponseDto.success(response);
    }

    @GetMapping("/subscription/usage/teacher")
    public ResponseDto<PaginatedTeacherTokenUsageLogResponse> getTokenUsageOfOneUser(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        Pageable pageable = PageRequest.of(page, size);

        PaginatedTeacherTokenUsageLogResponse response =
                adminService.getTokenUsageLogsBySchoolAndUser(principal.getUserId(), userId, pageable);

        return ResponseDto.success(response);
    }

    @DeleteMapping("/teachers/remove")
    public ResponseDto<?> removeTeacher(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RemoveTeacherFromSchoolRequest request) {
        adminService.removeTeacherFromSchool(principal.getUserId(), request);
        return ResponseDto.success("Teacher removed from school successfully");
    }
}