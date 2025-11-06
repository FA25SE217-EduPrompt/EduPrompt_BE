package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.teacherProfile.CreateTeacherProfileRequest;
import SEP490.EduPrompt.dto.request.teacherProfile.UpdateTeacherProfileRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.teacherProfile.TeacherProfileResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.teacherProfile.TeacherProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/teacher-profile")
@RequiredArgsConstructor
@Slf4j

public class TeacherProfileController {

    private final TeacherProfileService teacherProfileService;

    @PostMapping("/new")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseDto<TeacherProfileResponse> createProfile(
            @Valid @RequestBody CreateTeacherProfileRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("POST /teacher-profile - User: {} attempting to create profile", currentUser.getUserId());
        TeacherProfileResponse response = teacherProfileService.createProfile(request, currentUser);
        return ResponseDto.success(response);
    }

    @PutMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseDto<TeacherProfileResponse> updateProfile(
            @Valid @RequestBody UpdateTeacherProfileRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("PUT /teacher-profile - User: {} updating profile", currentUser.getUserId());
        TeacherProfileResponse response = teacherProfileService.updateProfile(request, currentUser);
        return ResponseDto.success(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseDto<TeacherProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("GET /teacher-profile/me - User: {}", currentUser.getUserId());
        TeacherProfileResponse response = teacherProfileService.getMyProfile(currentUser);
        return ResponseDto.success(response);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SYSTEM_ADMIN')")
    public ResponseDto<TeacherProfileResponse> getProfileByUserId(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("GET /teacher-profile/user/{} - Requested by SYSTEM_ADMIN: {}", userId, currentUser.getUserId());
        TeacherProfileResponse response = teacherProfileService.getProfileByUserId(userId, currentUser);
        return ResponseDto.success(response);
    }
}
