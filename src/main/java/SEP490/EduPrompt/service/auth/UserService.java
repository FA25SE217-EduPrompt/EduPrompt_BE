package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.ChangePasswordRequest;
import SEP490.EduPrompt.dto.request.LoginRequest;
import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService {

    boolean authenticateUser(String email, String password);
    void updateLastLogin(String email);
    LoginResponse login(LoginRequest loginRequest) throws Exception;
    RegisterResponse register(RegisterRequest registerRequest) throws Exception;
    void verifyEmail(String token) throws Exception;
    void resendVerificationEmail(String email) throws Exception;
    void changePassword(ChangePasswordRequest request) throws Exception;
    //TODO forgotPassword, logout

}
