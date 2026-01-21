package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.user.UserSchoolResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;

    @GetMapping("/school/users")
    @PreAuthorize("hasAnyRole('TEACHER','SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get all active users in the current user's school")
    public ResponseDto<List<UserSchoolResponse>> getUsersInMySchool(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<UserSchoolResponse> users = userService.getUsersInSameSchool(currentUser);
        return ResponseDto.success(users);
    }
}
