package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final AuthService authService;

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public ResponseDto<?> login(@Valid @RequestBody LoginRequest loginRequest) throws Exception {
        return ResponseDto.success(authService.login(loginRequest));
    }

    @PostMapping("/register")
    public ResponseDto<?> register(@Valid @RequestBody RegisterRequest registerRequest) throws Exception {
        return ResponseDto.success(authService.register(registerRequest));
    }

    @GetMapping("/verify-email")
    public ResponseDto<String> verifyEmail(@RequestParam("token") String token) {
        try {
            authService.verifyEmail(token);
            return ResponseDto.success("Email verified successfully!");
        } catch (Exception e) {
            return ResponseDto.error("404", "Email verification failed: " + e.getMessage());
        }
    }

    @PostMapping("/resend-verification")
    public ResponseDto<String> resendVerification(@Valid @RequestParam String email) {
        try {
            authService.resendVerificationEmail(email);
            return ResponseDto.success("Verification email resent successfully to " + email);
        } catch (Exception e) {
            return ResponseDto.error("400", "Fail to verify" + e.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseDto<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            authService.changePassword(request);
            return ResponseDto.success("Password changed ");
        } catch (Exception e) {
            return ResponseDto.error("400", "Fail to verify" + e.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseDto<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseDto.success("Password reset email sent successfully.");
    }

    @PostMapping("/reset-password")
    public ResponseDto<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseDto.success("Password has been reset successfully.");
    }

    @PostMapping("/logout")
    public ResponseDto<?> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseDto.success("Logout successful. Token expired immediately.");
    }

    @PostMapping("/refresh-token")
    @Transactional(readOnly = true)
    public ResponseDto<?> refreshToken(HttpServletRequest request) {
        try {
            return ResponseDto.success(authService.refreshToken(request));
        } catch (Exception e) {
            return ResponseDto.error("401", "Token refresh failed: " + e.getMessage());
        }
    }
}
