package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.ChangePasswordRequest;
import SEP490.EduPrompt.dto.request.ForgotPasswordRequest;
import SEP490.EduPrompt.dto.request.LoginRequest;
import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.service.auth.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final UserService userService;

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public ResponseDto<?> login(@Valid @RequestBody LoginRequest loginRequest) throws Exception {
        return ResponseDto.success(userService.login(loginRequest));
    }

    @PostMapping("/register")
    public ResponseDto<?> register(@Valid @RequestBody RegisterRequest registerRequest) throws Exception {
        return ResponseDto.success(userService.register(registerRequest));
    }

    @GetMapping("/verify-email")
    public ResponseDto<String> verifyEmail(@RequestParam("token") String token) {
        try {
            userService.verifyEmail(token);
            return ResponseDto.success("Email verified successfully!");
        } catch (Exception e) {
            return ResponseDto.error("404", "Email verification failed: " + e.getMessage());
        }
    }

    @PostMapping("/resend-verification")
    public ResponseDto<String> resendVerification(@Valid @RequestParam String email) {
        try {
            userService.resendVerificationEmail(email);
            return ResponseDto.success("Verification email resent successfully to " + email);
        } catch (Exception e) {
            return ResponseDto.error("400", "Fail to verify" + e.getMessage());
        }
    }
    @PostMapping("/change-password")
    public ResponseDto<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            userService.changePassword(request);
            return ResponseDto.success("Password changed ");
        }
        catch (Exception e) {
            return ResponseDto.error("400", "Fail to verify" + e.getMessage());
        }
    }
    @PostMapping("/forgot-password")
    public ResponseDto<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request);
        return ResponseDto.success("Password reset email sent successfully.");
    }
}
