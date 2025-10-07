package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.exception.TokenInvalidException;
import SEP490.EduPrompt.exception.auth.*;
import SEP490.EduPrompt.service.auth.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthenticationController authenticationController;

    private ObjectMapper objectMapper;

    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;
    private ChangePasswordRequest changePasswordRequest;
    private ForgotPasswordRequest forgotPasswordRequest;
    private ResetPasswordRequest resetPasswordRequest;
//    private GoogleLoginRequest googleLoginRequest;
    private String fakeToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authenticationController).build();
        objectMapper = new ObjectMapper();

        loginRequest = LoginRequest.builder()
                .email("test@email.com")
                .password("validPassword")
                .build();
        registerRequest = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("new@email.com")
                .password("validPassword")
                .phoneNumber("1234567890")
                .build();
        changePasswordRequest = ChangePasswordRequest.builder()
                .email("test@email.com")
                .oldPassword("oldPassword")
                .newPassword("newPassword")
                .build();
        forgotPasswordRequest = ForgotPasswordRequest.builder()
                .email("test@email.com")
                .build();
        resetPasswordRequest = ResetPasswordRequest.builder()
                .token("fakeToken")
                .newPassword("newPassword")
                .build();
//        googleLoginRequest = GoogleLoginRequest.builder()
//                .tokenId("googleToken")
//                .build();
        fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGVtYWlsLmNvbSJ9.signature";
    }

    // LOGIN CONTROLLER
    @Test
    void login_ValidRequest_ReturnsSuccess() throws Exception {
        LoginResponse loginResponse = new LoginResponse(fakeToken);
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(fakeToken))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).login(loginRequest);
    }

    @Test
    void login_InvalidRequest_ThrowsBadRequest() throws Exception {
        LoginRequest invalidRequest = LoginRequest.builder()
                .email("") // Invalid email
                .password("validPassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    void login_AuthFailed_ThrowsException() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new AuthFailedException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.error.message[0]").value("Authentication failed"));

        verify(authService).login(loginRequest);
    }

    // REGISTER CONTROLLER
    @Test
    void register_ValidRequest_ReturnsSuccess() throws Exception {
        RegisterResponse registerResponse = new RegisterResponse("Check your email to verify your account");
        when(authService.register(any(RegisterRequest.class))).thenReturn(registerResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Check your email to verify your account"))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).register(registerRequest);
    }

    @Test
    void register_InvalidRequest_ThrowsBadRequest() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .firstName("")
                .lastName("Doe")
                .email("new@email.com")
                .password("validPassword")
                .phoneNumber("1234567890")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    // VERIFY EMAIL CONTROLLER
    @Test
    void verifyEmail_ValidToken_ReturnsSuccess() throws Exception {
        doNothing().when(authService).verifyEmail(fakeToken);

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", fakeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Email verified successfully!"))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).verifyEmail(fakeToken);
    }

    @Test
    void verifyEmail_InvalidToken_ThrowsException() throws Exception {
        doThrow(new InvalidInputException("Invalid verification token")).when(authService).verifyEmail(fakeToken);

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", fakeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value(AuthExceptionCode.INVALID_INPUT.name()))
                .andExpect(jsonPath("$.error.message[0]").value("Invalid verification token"));

        verify(authService).verifyEmail(fakeToken);
    }

    // RESEND VERIFICATION EMAIL CONTROLLER
    @Test
    void resendVerification_ValidEmail_ReturnsSuccess() throws Exception {
        doNothing().when(authService).resendVerificationEmail("test@email.com");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .param("email", "test@email.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Verification email resent successfully to test@email.com"))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).resendVerificationEmail("test@email.com");
    }

    @Test
    void resendVerification_InvalidEmail_ThrowsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .param("email", "invalid")) // Invalid email format
                .andExpect(status().isBadRequest());

        verify(authService, never()).resendVerificationEmail(anyString());
    }

    @Test
    void resendVerification_UserNotFound_ThrowsException() throws Exception {
        doThrow(new ResourceNotFoundException("User not found with email: test@email.com")).when(authService).resendVerificationEmail("test@email.com");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .param("email", "test@email.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message[0]").value("User not found with email: test@email.com"));

        verify(authService).resendVerificationEmail("test@email.com");
    }

    // CHANGE PASSWORD CONTROLLER
    @Test
    void changePassword_ValidRequest_ReturnsSuccess() throws Exception {
        doNothing().when(authService).changePassword(any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Password changed "))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).changePassword(changePasswordRequest);
    }

    @Test
    void changePassword_InvalidRequest_ThrowsBadRequest() throws Exception {
        ChangePasswordRequest invalidRequest = ChangePasswordRequest.builder()
                .email("")
                .oldPassword("oldPassword")
                .newPassword("newPassword")
                .build();

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).changePassword(any(ChangePasswordRequest.class));
    }

    // FORGOT PASSWORD CONTROLLER
    @Test
    void forgotPassword_ValidRequest_ReturnsSuccess() throws Exception {
        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Password reset email sent successfully."))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).forgotPassword(forgotPasswordRequest);
    }

    @Test
    void forgotPassword_InvalidRequest_ThrowsBadRequest() throws Exception {
        ForgotPasswordRequest invalidRequest = ForgotPasswordRequest.builder()
                .email("")
                .build();

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).forgotPassword(any(ForgotPasswordRequest.class));
    }

    // RESET PASSWORD CONTROLLER
    @Test
    void resetPassword_ValidRequest_ReturnsSuccess() throws Exception {
        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Password has been reset successfully."))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).resetPassword(resetPasswordRequest);
    }

    @Test
    void resetPassword_InvalidRequest_ThrowsBadRequest() throws Exception {
        ResetPasswordRequest invalidRequest = ResetPasswordRequest.builder()
                .token("")
                .newPassword("newPassword")
                .build();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resetPassword(any(ResetPasswordRequest.class));
    }

    // LOGOUT CONTROLLER
    @Test
    void logout_ValidRequest_ReturnsSuccess() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + fakeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Logout successful. Token expired immediately."))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).logout(any());
    }

    @Test
    void logout_ServiceThrowsException_ThrowsUnauthorized() throws Exception {
        doThrow(new TokenInvalidException("Invalid token")).when(authService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + fakeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.error.message[0]").value("Invalid token"));

        verify(authService).logout(any());
    }

    // REFRESH TOKEN CONTROLLER
    @Test
    void refreshToken_ValidRequest_ReturnsSuccess() throws Exception {
        LoginResponse loginResponse = new LoginResponse(fakeToken);
        when(authService.refreshToken(any())).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Authorization", "Bearer " + fakeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(fakeToken))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(authService).refreshToken(any());
    }

    @Test
    void refreshToken_ServiceThrowsException_ThrowsUnauthorized() throws Exception {
        doThrow(new TokenInvalidException("Invalid token")).when(authService).refreshToken(any());

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("Authorization", "Bearer " + fakeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.error.message[0]").value("Invalid token"));

        verify(authService).refreshToken(any());
    }

//    // GOOGLE LOGIN CONTROLLER
//    @Test
//    void googleLogin_ValidRequest_ReturnsSuccess() throws Exception {
//        LoginResponse loginResponse = new LoginResponse(fakeToken);
//        when(authService.googleLogin(any(GoogleLoginRequest.class))).thenReturn(loginResponse);
//
//        mockMvc.perform(post("/api/auth/google")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(googleLoginRequest)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.data.token").value(fakeToken))
//                .andExpect(jsonPath("$.error").isEmpty());
//
//        verify(authService).googleLogin(googleLoginRequest);
//    }
//
//    @Test
//    void googleLogin_InvalidRequest_ThrowsBadRequest() throws Exception {
//        GoogleLoginRequest invalidRequest = GoogleLoginRequest.builder()
//                .tokenId("")
//                .build();
//
//        mockMvc.perform(post("/api/auth/google")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(invalidRequest)))
//                .andExpect(status().isBadRequest());
//
//        verify(authService, never()).googleLogin(any(GoogleLoginRequest.class));
//    }
}