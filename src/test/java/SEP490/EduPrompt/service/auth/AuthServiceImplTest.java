package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.exception.BaseException;
import SEP490.EduPrompt.exception.auth.DuplicatePasswordException;
import SEP490.EduPrompt.exception.auth.TokenInvalidException;
import SEP490.EduPrompt.exception.auth.*;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.util.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthRepository userAuthRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;
    private UserAuth sampleUserAuth;
    private LoginRequest validLoginRequest;
    private RegisterRequest validRegisterRequest;
    private String fakeToken;

    @BeforeEach
    void setUp() {
        // Sample User - using Lombok @Builder
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("test@email.com")
                .role("teacher")
                .isActive(true)
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Sample UserAuth
        sampleUserAuth = UserAuth.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .email("test@email.com")
                .passwordHash("$2a$10$placeholderHashForTest")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Valid requests
        validLoginRequest = LoginRequest.builder()
                .email("test@email.com")
                .password("validPassword")
                .build();
        validRegisterRequest = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("new@email.com")
                .password("validPassword")
                .phoneNumber("1234567890")
                .build();

        fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGVtYWlsLmNvbSJ9.signature";
    }

    // LOGIN TESTS

    @Test
    void login_ValidCredentials_ReturnsLoginResponse() {
        // Arrange
        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);
        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
        when(jwtUtil.generateToken(validLoginRequest.getEmail(), sampleUser.getRole())).thenReturn(fakeToken);

        // Act
        LoginResponse response = authService.login(validLoginRequest);

        // Assert
        assertNotNull(response);
        assertEquals(fakeToken, response.token());
        verify(userAuthRepository).save(any(UserAuth.class)); // Last login update
        verify(jwtUtil).generateToken(validLoginRequest.getEmail(), sampleUser.getRole());
    }

    @Test
    void login_InvalidPassword_ThrowsAuthFailedException() {
        // Arrange
        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(false);

        // Act & Assert
        AuthFailedException exception = assertThrows(AuthFailedException.class, () -> authService.login(validLoginRequest));

        assertEquals("Invalid email or password", exception.getMessage());
        verify(userAuthRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void login_UserNotFound_ThrowsAuthFailedException() {
        // Arrange
        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> authService.login(validLoginRequest));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_InactiveUser_ThrowsUserNotVerifiedException() {
        // Arrange
        sampleUser.setIsActive(false);
        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);

        // Act & Assert
        assertThrows(UserNotVerifiedException.class, () -> authService.login(validLoginRequest));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void login_UnverifiedUser_ThrowsUserNotVerifiedException() {
        // Arrange
        sampleUser.setIsVerified(false);
        sampleUser.setIsActive(true);
        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);

        // Act & Assert
        assertThrows(UserNotVerifiedException.class, () -> authService.login(validLoginRequest));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    // REGISTER TESTS

    @Test
    void register_NewUser_ReturnsRegisterResponse() {
        // Arrange
        when(userAuthRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
        User savedUserMock = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);
        UserAuth savedUserAuthMock = UserAuth.builder().id(UUID.randomUUID()).build();
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(savedUserAuthMock);
        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJuZXdAZW1haWwuY29tIn0.signature";  // fake JWT , we all know :D
        when(jwtUtil.generateToken(validRegisterRequest.getEmail(), "teacher")).thenReturn(fakeToken);
        doNothing().when(emailService).sendVerificationEmail(eq(validRegisterRequest.getEmail()), eq(validRegisterRequest.getLastName()), eq(fakeToken));

        // Act
        RegisterResponse response = authService.register(validRegisterRequest);

        // Assert r
        assertNotNull(response);
        assertEquals("Check your email to verify your account", response.message());
        verify(userRepository).save(any(User.class));
        verify(userAuthRepository).save(any(UserAuth.class));
        verify(emailService).sendVerificationEmail(validRegisterRequest.getEmail(), validRegisterRequest.getLastName(), fakeToken);
    }

    @Test
    void register_DuplicateEmail_ThrowsEmailAlreadyExistedException() {
        // Arrange
        when(userAuthRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(true);

        // Act & Assert
        assertThrows(EmailAlreadyExistedException.class, () -> authService.register(validRegisterRequest));
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void register_EmailSendFailure_ReturnsErrorResponse() {
        // Arrange
        when(userAuthRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
        User savedUserMock = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);
        UserAuth savedUserAuthMock = UserAuth.builder().id(UUID.randomUUID()).build();
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(savedUserAuthMock);
        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJuZXdAZW1haWwuY29tIn0.signature"; //fake
        when(jwtUtil.generateToken(validRegisterRequest.getEmail(), "teacher")).thenReturn(fakeToken);
        doThrow(new RuntimeException("Email send failed")).when(emailService).sendVerificationEmail(eq(validRegisterRequest.getEmail()), eq(validRegisterRequest.getLastName()), eq(fakeToken));

        // Act
        RegisterResponse response = authService.register(validRegisterRequest);

        // Assert
        assertNotNull(response);
        assertEquals("Check your email again or email not Exist", response.message());
        verify(userRepository).save(any(User.class));
        verify(userAuthRepository).save(any(UserAuth.class));
        verify(emailService).sendVerificationEmail(validRegisterRequest.getEmail(), validRegisterRequest.getLastName(), fakeToken);
    }

    // VERIFY EMAIL TESTS

    @Test
    void verifyEmail_ValidToken_ActivatesUserAndSendsWelcomeEmail() {
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        sampleUserAuth.setVerificationToken(fakeToken);
        doNothing().when(emailService).sendWelcomeEmail(sampleUser.getEmail(), sampleUser.getLastName());

        authService.verifyEmail(fakeToken);

        assertTrue(sampleUser.getIsVerified());
        assertTrue(sampleUser.getIsActive());
        verify(userRepository).save(any(User.class));
        verify(emailService).sendWelcomeEmail(sampleUser.getEmail(), sampleUser.getLastName());
    }

    @Test
    void verifyEmail_InvalidToken_ThrowsInvalidInputException() {
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        sampleUserAuth.setVerificationToken("differentToken");

        assertThrows(InvalidInputException.class, () -> authService.verifyEmail(fakeToken));
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendWelcomeEmail(anyString(), anyString());
    }

    @Test
    void verifyEmail_NullEmailFromToken_ThrowsInvalidInputException() {
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(null);

        assertThrows(InvalidInputException.class, () -> authService.verifyEmail(fakeToken));
        verify(userAuthRepository, never()).findByEmail(anyString());
    }

    @Test
    void verifyEmail_UserNotFound_ThrowsResourceNotFoundException() {
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.verifyEmail(fakeToken));
        verify(userRepository, never()).save(any());
    }

    // RESEND VERIFICATION EMAIL TESTS

    @Test
    void resendVerificationEmail_UnverifiedUser_SendsNewEmail() {
        sampleUser.setIsVerified(false);
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        String newToken = "eyJhbGciOiJIUzI1NiJ9.newToken.signature";
        when(jwtUtil.generateToken(sampleUser.getEmail(), "teacher")).thenReturn(newToken);
        doNothing().when(emailService).sendVerificationEmail(sampleUser.getEmail(), sampleUser.getLastName(), newToken);

        authService.resendVerificationEmail(sampleUser.getEmail());

        verify(userAuthRepository).save(any(UserAuth.class));
        verify(emailService).sendVerificationEmail(sampleUser.getEmail(), sampleUser.getLastName(), newToken);
    }

    @Test
    void resendVerificationEmail_AlreadyVerified_ThrowsIllegalStateException() {
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));

        assertThrows(IllegalStateException.class, () -> authService.resendVerificationEmail(sampleUser.getEmail()));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerificationEmail_UserNotFound_ThrowsResourceNotFoundException() {
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.resendVerificationEmail(sampleUser.getEmail()));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    // CHANGE PASSWORD TESTS

    @Test
    void changePassword_ValidOldPassword_UpdatesPassword() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .email(sampleUser.getEmail())
                .oldPassword("validPassword")
                .newPassword("newPassword")
                .build();
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(passwordEncoder.matches(request.getOldPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode(request.getNewPassword())).thenReturn("$2a$10$newHash");

        authService.changePassword(request);

        verify(userAuthRepository).save(any(UserAuth.class));
        assertEquals("$2a$10$newHash", sampleUserAuth.getPasswordHash());
    }

    @Test
    void changePassword_InvalidOldPassword_ThrowsInvalidInputException() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .email(sampleUser.getEmail())
                .oldPassword("wrongPassword")
                .newPassword("newPassword")
                .build();
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(passwordEncoder.matches(request.getOldPassword(), sampleUserAuth.getPasswordHash())).thenReturn(false);

        assertThrows(InvalidInputException.class, () -> authService.changePassword(request));
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void changePassword_UserNotFound_ThrowsResourceNotFoundException() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .email(sampleUser.getEmail())
                .oldPassword("validPassword")
                .newPassword("newPassword")
                .build();
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.changePassword(request));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // FORGOT PASSWORD TESTS

    @Test
    void forgotPassword_ValidUser_SendsResetEmail() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(sampleUser.getEmail())
                .build();
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        String resetToken = "eyJhbGciOiJIUzI1NiJ9.resetToken.signature";
        when(jwtUtil.generateToken(sampleUser.getEmail(), "teacher")).thenReturn(resetToken);
        doNothing().when(emailService).sendResetPasswordEmail(sampleUser.getEmail(), sampleUser.getLastName(), resetToken, 5);

        authService.forgotPassword(request);

        verify(userAuthRepository).save(any(UserAuth.class));
        verify(emailService).sendResetPasswordEmail(sampleUser.getEmail(), sampleUser.getLastName(), resetToken, 5);
    }

    @Test
    void forgotPassword_UserNotFound_ThrowsResourceNotFoundException() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(sampleUser.getEmail())
                .build();
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.forgotPassword(request));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void forgotPassword_EmailSendFailure_ThrowsBaseException() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(sampleUser.getEmail())
                .build();
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        String resetToken = "eyJhbGciOiJIUzI1NiJ9.resetToken.signature";
        when(jwtUtil.generateToken(sampleUser.getEmail(), "teacher")).thenReturn(resetToken);
        doThrow(new RuntimeException("Email send failed")).when(emailService).sendResetPasswordEmail(anyString(), anyString(), anyString(), anyInt());

        BaseException ex = assertThrows(BaseException.class, () -> authService.forgotPassword(request));
        assertEquals("AUTH_FAILED", ex.getCode());
        verify(userAuthRepository).save(any(UserAuth.class));
    }

    // RESET PASSWORD TESTS

    @Test
    void resetPassword_ValidToken_UpdatesPassword() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(fakeToken)
                .newPassword("newPassword")
                .build();
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        sampleUserAuth.setVerificationToken(fakeToken);
        when(passwordEncoder.matches("newPassword", sampleUserAuth.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("newPassword")).thenReturn("$2a$10$newHash");

        authService.resetPassword(request);

        verify(userAuthRepository).save(any(UserAuth.class));
        assertEquals("$2a$10$newHash", sampleUserAuth.getPasswordHash());
        assertNull(sampleUserAuth.getVerificationToken());
    }

    @Test
    void resetPassword_InvalidToken_ThrowsInvalidInputException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(fakeToken)
                .newPassword("newPassword")
                .build();
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        sampleUserAuth.setVerificationToken("differentToken");

        assertThrows(InvalidInputException.class, () -> authService.resetPassword(request));
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void resetPassword_SamePassword_ThrowsDuplicatePasswordException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(fakeToken)
                .newPassword("newPassword")
                .build();
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        sampleUserAuth.setVerificationToken(fakeToken);
        when(passwordEncoder.matches("newPassword", sampleUserAuth.getPasswordHash())).thenReturn(true);

        assertThrows(DuplicatePasswordException.class, () -> authService.resetPassword(request));
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void resetPassword_MalformedToken_ThrowsInvalidInputException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token(fakeToken)
                .newPassword("newPassword")
                .build();
        when(jwtUtil.extractUsername(fakeToken)).thenThrow(new RuntimeException("Malformed token"));

        assertThrows(InvalidInputException.class, () -> authService.resetPassword(request));
        verify(userAuthRepository, never()).findByEmail(anyString());
    }

    // LOGOUT TESTS

    @Test
    void logout_ValidToken_UpdatesLastLogin() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(jwtUtil.extractIssuedAt(fakeToken)).thenReturn(new Date());

        authService.logout(httpServletRequest);

        verify(userAuthRepository).save(any(UserAuth.class));
    }

    @Test
    void logout_MissingHeader_ThrowsTokenInvalidException() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn(null);

        assertThrows(TokenInvalidException.class, () -> authService.logout(httpServletRequest));
        verify(userAuthRepository, never()).findByEmail(anyString());
    }

    @Test
    void logout_InvalidatedToken_ThrowsTokenInvalidException() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(jwtUtil.extractIssuedAt(fakeToken)).thenReturn(new Date(Instant.now().minusSeconds(3600).toEpochMilli()));
        sampleUserAuth.setLastLogin(Instant.now());

        assertThrows(TokenInvalidException.class, () -> authService.logout(httpServletRequest));
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void logout_MalformedHeader_ThrowsTokenInvalidException() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("InvalidHeader");
        assertThrows(TokenInvalidException.class, () -> authService.logout(httpServletRequest));
    }

    // REFRESH TOKEN TESTS

    @Test
    void refreshToken_ValidToken_ReturnsNewToken() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(jwtUtil.validateToken(fakeToken)).thenReturn(true);
        when(jwtUtil.extractIssuedAt(fakeToken)).thenReturn(new Date());
        String newToken = "eyJhbGciOiJIUzI1NiJ9.newToken.signature";
        when(jwtUtil.generateToken(sampleUser.getEmail(), "teacher")).thenReturn(newToken);

        LoginResponse response = authService.refreshToken(httpServletRequest);

        assertNotNull(response);
        assertEquals(newToken, response.token());
        verify(jwtUtil).generateToken(sampleUser.getEmail(), "teacher");
    }

    @Test
    void refreshToken_InvalidToken_ThrowsTokenInvalidException() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        when(jwtUtil.validateToken(fakeToken)).thenReturn(false);

        assertThrows(TokenInvalidException.class, () -> authService.refreshToken(httpServletRequest));
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    @Test
    void refreshToken_UnverifiedUser_ThrowsUserNotVerifiedException() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
        when(jwtUtil.extractUsername(fakeToken)).thenReturn(sampleUser.getEmail());
        when(userAuthRepository.findByEmail(sampleUser.getEmail())).thenReturn(Optional.of(sampleUserAuth));
        sampleUser.setIsVerified(false);

        assertThrows(UserNotVerifiedException.class, () -> authService.refreshToken(httpServletRequest));
        verify(jwtUtil, never()).validateToken(anyString());
    }

    @Test
    void refreshToken_MissingHeader_ThrowsTokenInvalidException() {
        when(httpServletRequest.getHeader("Authorization")).thenReturn(null);

        assertThrows(TokenInvalidException.class, () -> authService.refreshToken(httpServletRequest));
        verify(jwtUtil, never()).extractUsername(anyString());
    }

//    // GOOGLE LOGIN TESTS
//
//    @Test
//    void googleLogin_NewUser_CreatesUserAndReturnsToken() {
//        GoogleLoginRequeset request = GoogleLoginRequeset.builder()
//                .tokenId(fakeToken)
//                .build();
//        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
//        when(payload.getEmail()).thenReturn("google@email.com");
//        when(payload.getSubject()).thenReturn("google123");
//        when(payload.get("given_name")).thenReturn("Jane");
//        when(payload.get("family_name")).thenReturn("Doe");
//        GoogleIdToken idToken = mock(GoogleIdToken.class);
//        when(idToken.getPayload()).thenReturn(payload);
//        when(googleIdTokenVerifier.verify(fakeToken)).thenReturn(idToken);
//        when(userAuthRepository.findByGoogleUserId("google123")).thenReturn(Optional.empty());
//        User savedUserMock = User.builder().id(UUID.randomUUID()).build();
//        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);
//        UserAuth savedUserAuthMock = UserAuth.builder().id(UUID.randomUUID()).build();
//        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(savedUserAuthMock);
//        String newToken = "eyJhbGciOiJIUzI1NiJ9.googleToken.signature";
//        when(jwtUtil.generateToken("google@email.com", "teacher")).thenReturn(newToken);
//
//        LoginResponse response = authService.googleLogin(request);
//
//        assertNotNull(response);
//        assertEquals(newToken, response.token());
//        verify(userRepository).save(any(User.class));
//        verify(userAuthRepository).save(any(UserAuth.class));
//        verify(jwtUtil).generateToken("google@email.com", "teacher");
//    }
//
//    @Test
//    void googleLogin_ExistingUser_ReturnsToken() {
//        GoogleLoginRequeset request = GoogleLoginRequeset.builder()
//                .tokenId(fakeToken)
//                .build();
//        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
//        when(payload.getEmail()).thenReturn(sampleUser.getEmail());
//        when(payload.getSubject()).thenReturn("google123");
//        GoogleIdToken idToken = mock(GoogleIdToken.class);
//        when(idToken.getPayload()).thenReturn(payload);
//        when(googleIdTokenVerifier.verify(fakeToken)).thenReturn(idToken);
//        when(userAuthRepository.findByGoogleUserId("google123")).thenReturn(Optional.of(sampleUserAuth));
//        when(jwtUtil.generateToken(sampleUser.getEmail(), "teacher")).thenReturn(fakeToken);
//
//        LoginResponse response = authService.googleLogin(request);
//
//        assertNotNull(response);
//        assertEquals(fakeToken, response.token());
//        verify(userRepository, never()).save(any());
//        verify(userAuthRepository, never()).save(any());
//    }
//
//    @Test
//    void googleLogin_InvalidToken_ThrowsInvalidGoogleTokenException() {
//        GoogleLoginRequeset request = GoogleLoginRequeset.builder()
//                .tokenId(fakeToken)
//                .build();
//        when(googleIdTokenVerifier.verify(fakeToken)).thenReturn(null);
//
//        assertThrows(InvalidGoogleTokenException.class, () -> authService.googleLogin(request));
//        verify(userAuthRepository, never()).findByGoogleUserId(anyString());
//    }
}
