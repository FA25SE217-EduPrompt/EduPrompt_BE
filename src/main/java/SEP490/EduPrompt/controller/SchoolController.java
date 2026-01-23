package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.school.JoinSchoolRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.school.JoinSchoolResponse;
import SEP490.EduPrompt.dto.response.school.SchoolResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.school.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/school")
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class SchoolController {
    private final SchoolService schoolService;

    @GetMapping()
    public ResponseDto<Page<SchoolResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(schoolService.getAllSchools(pageable));
    }

    @GetMapping("/{schoolId}")
    public ResponseDto<SchoolResponse> getSchoolById(@PathVariable UUID schoolId) {
        return ResponseDto.success(schoolService.getSchoolById(schoolId));
    }

    @GetMapping("/user/{userId}")
    public ResponseDto<SchoolResponse> getSchoolByUserId(@PathVariable UUID userId) {
        return ResponseDto.success(schoolService.getSchoolByUserId(userId));
    }

    @PostMapping("/join")
    public ResponseDto<JoinSchoolResponse> joinSchool(
            @RequestBody JoinSchoolRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseDto.success(schoolService.assignTeacherToSchool(currentUser, request));
    }
}
