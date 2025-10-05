package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    boolean authenticateUser(String email, String password);
    void updateLastLogin(String email);
    LoginResponse login(LoginRequest loginRequest) throws Exception;
    RegisterResponse register(RegisterRequest registerRequest) throws Exception;
    void verifyEmail(String token) throws Exception;
    void resendVerificationEmail(String email) throws Exception;
    void changePassword(ChangePasswordRequest request) throws Exception;
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void logout(HttpServletRequest authHeader);
    LoginResponse refreshToken(HttpServletRequest request) throws Exception;
}
