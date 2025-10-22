package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.PersonalInfoResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface AuthService {

//    boolean authenticateUser(String email, String password);

//    void updateLastLogin(String email);

    LoginResponse login(LoginRequest loginRequest);

    RegisterResponse register(RegisterRequest registerRequest);

    void verifyEmail(String token);

    void resendVerificationEmail(String email);

    void changePassword(ChangePasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void logout(HttpServletRequest authHeader);

    LoginResponse refreshToken(HttpServletRequest request);

    LoginResponse googleLogin(GoogleLoginRequeset requeset) throws GeneralSecurityException, IOException;

    PersonalInfoResponse getPersonalInfo(HttpServletRequest request);
}
