//package SEP490.EduPrompt.service.auth;
//
//import SEP490.EduPrompt.dto.request.*;
//import SEP490.EduPrompt.dto.response.LoginResponse;
//import SEP490.EduPrompt.dto.response.RegisterResponse;
//import SEP490.EduPrompt.exception.BaseException;
//import SEP490.EduPrompt.exception.auth.*;
//import SEP490.EduPrompt.model.User;
//import SEP490.EduPrompt.model.UserAuth;
//import SEP490.EduPrompt.repo.UserAuthRepository;
//import SEP490.EduPrompt.repo.UserRepository;
//import SEP490.EduPrompt.util.JwtUtil;
//import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
//import jakarta.servlet.http.HttpServletRequest;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.security.crypto.password.PasswordEncoder;
//
//import java.time.Instant;
//import java.util.Date;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class AuthServiceImplTest {
//
//    @Mock
//    private UserRepository userRepository;
//
//    @Mock
//    private UserAuthRepository userAuthRepository;
//
//    @Mock
//    private EmailService emailService;
//
//    @Mock
//    private PasswordEncoder passwordEncoder;
//
//    @Mock
//    private JwtUtil jwtUtil;
//
//    @Mock
//    private TokenBlacklistService blacklistService;
//
//    @Mock
//    private TokenValidationService tokenValidationService;
//
//    @Mock
//    private GoogleIdTokenVerifier googleIdTokenVerifier;
//
//    @Mock
//    private HttpServletRequest httpServletRequest;
//
//    @InjectMocks
//    private AuthServiceImpl authService;
//
//    private User sampleUser;
//    private UserAuth sampleUserAuth;
//    private LoginRequest validLoginRequest;
//    private RegisterRequest validRegisterRequest;
//    private String fakeToken;
//
//    @BeforeEach
//    void setUp() {
//        // Sample User - using Lombok @Builder
//        sampleUser = User.builder()
//                .id(UUID.randomUUID())
//                .firstName("John")
//                .lastName("Doe")
//                .email("test@email.com")
//                .role("teacher")
//                .isActive(true)
//                .isVerified(true)
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .build();
//
//        // Sample UserAuth
//        sampleUserAuth = UserAuth.builder()
//                .id(UUID.randomUUID())
//                .user(sampleUser)
//                .email("test@email.com")
//                .passwordHash("$2a$10$placeholderHashForTest")
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .build();
//
//        // Valid requests
//        validLoginRequest = LoginRequest.builder()
//                .email("test@email.com")
//                .password("validPassword")
//                .build();
//        validRegisterRequest = RegisterRequest.builder()
//                .firstName("Jane")
//                .lastName("Doe")
//                .email("new@email.com")
//                .password("validPassword")
//                .phoneNumber("1234567890")
//                .build();
//
//        fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGVtYWlsLmNvbSJ9.signature";
//    }
//
//    // LOGIN TESTS
//
//    @Test
//    void login_ValidCredentials_ReturnsLoginResponse() {
//        // Arrange
//        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);
//        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGVtYWlsLmNvbSJ9.signature";  // Fake JWT string
//        when(jwtUtil.generateToken(validLoginRequest.getEmail(), sampleUser.getRole())).thenReturn(fakeToken);
//
//        // Act
//        LoginResponse response = authService.login(validLoginRequest);
//
//        // Assert - using record accessor
//        assertNotNull(response);
//        assertEquals(fakeToken, response.token());
//        verify(userAuthRepository).save(any(UserAuth.class)); // Last login update
//        verify(jwtUtil).generateToken(validLoginRequest.getEmail(), sampleUser.getRole());
//    }
//
//    @Test
//    void login_InvalidPassword_ThrowsAuthFailedException() {
//        // Arrange
//        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(false);
//
//        // Act & Assert
//        AuthFailedException exception = assertThrows(AuthFailedException.class, () -> authService.login(validLoginRequest));
//        assertEquals("Invalid email or password", exception.getMessage()); // Updated to match service code
//        verify(userAuthRepository, never()).save(any());
//        verify(jwtUtil, never()).generateToken(anyString(), anyString());
//    }
//
//    @Test
//    void login_UserNotFound_ThrowsResourceNotFoundException() { // Updated to match service code
//        // Arrange
//        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.empty());
//
//        // Act & Assert
//        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> authService.login(validLoginRequest));
//        assertEquals("User not found", exception.getMessage()); // Updated to match service code
//        verify(passwordEncoder, never()).matches(anyString(), anyString());
//    }
//
//    @Test
//    void login_InactiveUser_ThrowsUserNotVerifiedException() {
//        // Arrange
//        sampleUser.setIsActive(false);
//        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);
//
//        // Act & Assert
//        assertThrows(UserNotVerifiedException.class, () -> authService.login(validLoginRequest));
//        verify(jwtUtil, never()).generateToken(anyString(), anyString());
//    }
//
//    @Test
//    void login_UnverifiedUser_ThrowsUserNotVerifiedException() {
//        // Arrange
//        sampleUser.setIsVerified(false);
//        sampleUser.setIsActive(true);
//        when(userAuthRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(passwordEncoder.matches(validLoginRequest.getPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);
//
//        // Act & Assert
//        assertThrows(UserNotVerifiedException.class, () -> authService.login(validLoginRequest));
//        verify(jwtUtil, never()).generateToken(anyString(), anyString());
//    }
//
//    // REGISTER TESTS
//
//    @Test
//    void register_NewUser_ReturnsRegisterResponse() {
//        // Arrange
//        when(userAuthRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
//        User savedUserMock = User.builder().id(UUID.randomUUID()).build();  // Mock saved User
//        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);
//        UserAuth savedUserAuthMock = UserAuth.builder().id(UUID.randomUUID()).build();  // Mock saved UserAuth
//        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(savedUserAuthMock);
//        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJuZXdAZW1haWwuY29tIn0.signature";  // Fake JWT
//        when(jwtUtil.generateTokenWithExpiration(validRegisterRequest.getEmail(), 1440)).thenReturn(fakeToken);  // Updated to mock the correct method
//        doNothing().when(emailService).sendVerificationEmail(eq(validRegisterRequest.getEmail()), eq(validRegisterRequest.getLastName()), eq(fakeToken));
//
//        // Act
//        RegisterResponse response = authService.register(validRegisterRequest);
//
//        // Assert - using record accessor
//        assertNotNull(response);
//        assertEquals("Check your email to verify your account", response.message());
//        verify(userRepository).save(any(User.class));
//        verify(userAuthRepository).save(any(UserAuth.class));
//        verify(emailService).sendVerificationEmail(validRegisterRequest.getEmail(), validRegisterRequest.getLastName(), fakeToken);
//    }
//
//    @Test
//    void register_DuplicateEmail_ThrowsEmailAlreadyExistedException() {
//        // Arrange
//        when(userAuthRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(true);
//
//        // Act & Assert
//        assertThrows(EmailAlreadyExistedException.class, () -> authService.register(validRegisterRequest));
//        verify(userRepository, never()).save(any());
//        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
//    }
//
//    @Test
//    void register_EmailSendFailure_ReturnsErrorResponse() {
//        // Arrange
//        when(userAuthRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
//        User savedUserMock = User.builder().id(UUID.randomUUID()).build();
//        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);
//        UserAuth savedUserAuthMock = UserAuth.builder().id(UUID.randomUUID()).build();
//        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(savedUserAuthMock);
//        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJuZXdAZW1haWwuY29tIn0.signature";
//        when(jwtUtil.generateTokenWithExpiration(validRegisterRequest.getEmail(), 1440)).thenReturn(fakeToken);  // Updated to mock the correct method
//        doThrow(new RuntimeException("Email send failed")).when(emailService).sendVerificationEmail(eq(validRegisterRequest.getEmail()), eq(validRegisterRequest.getLastName()), eq(fakeToken));
//
//        // Act
//        RegisterResponse response = authService.register(validRegisterRequest);
//
//        // Assert - using record accessor
//        assertNotNull(response);
//        assertEquals("Registration successful but email sending failed. Please contact support.", response.message()); // Updated to match service code
//        verify(userRepository).save(any(User.class)); // Saves still happen per your try-catch
//        verify(userAuthRepository).save(any(UserAuth.class));
//        verify(emailService).sendVerificationEmail(validRegisterRequest.getEmail(), validRegisterRequest.getLastName(), fakeToken);
//    }
//
//    // VERIFY EMAIL TESTS
//
//    @Test
//    void verifyEmail_ValidToken_UpdatesUser() {
//        // Arrange
//        sampleUser.setIsVerified(false); // Ensure user starts unverified
//        when(jwtUtil.extractUsernameAllowExpired(fakeToken)).thenReturn(sampleUserAuth.getEmail());
//        when(userAuthRepository.findByEmail(sampleUserAuth.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        sampleUserAuth.setVerificationToken(fakeToken);
//
//        // Mock the save to capture the updated object
//        UserAuth savedUserAuth = UserAuth.builder().id(sampleUserAuth.getId()).verificationToken(null).build();
//        when(userAuthRepository.save(any(UserAuth.class))).thenAnswer(invocation -> {
//            UserAuth updatedAuth = invocation.getArgument(0);
//            updatedAuth.setVerificationToken(null); // Simulate the update
//            return updatedAuth;
//        });
//
//        // Act
//        authService.verifyEmail(fakeToken);
//
//        // Assert
//        assertTrue(sampleUser.getIsVerified());
//        assertTrue(sampleUser.getIsActive());
//        assertNull(sampleUserAuth.getVerificationToken()); // Check the updated state
//        verify(userRepository).save(sampleUser);
//        verify(userAuthRepository).save(sampleUserAuth);
//        verify(emailService).sendWelcomeEmail(sampleUser.getEmail(), sampleUser.getLastName());
//    }
//
//    @Test
//    void verifyEmail_InvalidToken_ThrowsInvalidInputException() {
//        // Arrange
//        when(jwtUtil.extractUsernameAllowExpired("invalidToken")).thenThrow(new RuntimeException("Invalid token"));
//
//        // Act & Assert
//        assertThrows(InvalidInputException.class, () -> authService.verifyEmail("invalidToken"));
//        verify(userAuthRepository, never()).findByEmail(anyString());
//    }
//
//    @Test
//    void verifyEmail_AlreadyVerified_DoesNothing() {
//        // Arrange
//        sampleUser.setIsVerified(true);
//        when(jwtUtil.extractUsernameAllowExpired(fakeToken)).thenReturn(sampleUserAuth.getEmail());
//        when(userAuthRepository.findByEmail(sampleUserAuth.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        sampleUserAuth.setVerificationToken(fakeToken);
//
//        // Act
//        authService.verifyEmail(fakeToken);
//
//        // Assert
//        verify(userRepository, never()).save(any());
//        verify(userAuthRepository, never()).save(any());
//    }
//
//    // RESEND VERIFICATION EMAIL TESTS
//
//    @Test
//    void resendVerificationEmail_ValidEmail_SendsNewToken() {
//        // Arrange
//        when(userAuthRepository.findByEmail(sampleUserAuth.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        sampleUser.setIsVerified(false);
//        String newToken = "newToken123";
//        when(jwtUtil.generateTokenWithExpiration(sampleUserAuth.getEmail(), 1440)).thenReturn(newToken);
//
//        // Act
//        authService.resendVerificationEmail(sampleUserAuth.getEmail());
//
//        // Assert
//        assertEquals(newToken, sampleUserAuth.getVerificationToken());
//        verify(userAuthRepository).save(sampleUserAuth);
//        verify(emailService).sendVerificationEmail(sampleUserAuth.getEmail(), sampleUser.getLastName(), newToken);
//    }
//
//    @Test
//    void resendVerificationEmail_AlreadyVerified_ThrowsUserVerifiedException() {
//        // Arrange
//        sampleUser.setIsVerified(true);
//        when(userAuthRepository.findByEmail(sampleUserAuth.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//
//        // Act & Assert
//        assertThrows(UserVerifiedException.class, () -> authService.resendVerificationEmail(sampleUserAuth.getEmail()));
//        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
//    }
//
//    // CHANGE PASSWORD TESTS
//
//    @Test
//    void changePassword_ValidRequest_UpdatesPassword() {
//        // Arrange
//        ChangePasswordRequest request = ChangePasswordRequest.builder()
//                .email(sampleUserAuth.getEmail())
//                .oldPassword("validPassword")
//                .newPassword("newPassword")
//                .build();
//        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(passwordEncoder.matches(request.getOldPassword(), sampleUserAuth.getPasswordHash())).thenReturn(true);
//        when(passwordEncoder.encode(request.getNewPassword())).thenReturn("encodedNewPassword");
//
//        // Act
//        authService.changePassword(request);
//
//        // Assert
//        assertEquals("encodedNewPassword", sampleUserAuth.getPasswordHash());
//        verify(userAuthRepository).save(sampleUserAuth);
//    }
//
//    @Test
//    void changePassword_InvalidOldPassword_ThrowsInvalidInputException() {
//        // Arrange
//        ChangePasswordRequest request = ChangePasswordRequest.builder()
//                .email(sampleUserAuth.getEmail())
//                .oldPassword("wrongPassword")
//                .newPassword("newPassword")
//                .build();
//        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(passwordEncoder.matches(request.getOldPassword(), sampleUserAuth.getPasswordHash())).thenReturn(false);
//
//        // Act & Assert
//        assertThrows(InvalidInputException.class, () -> authService.changePassword(request));
//        verify(userAuthRepository, never()).save(any());
//    }
//
//    // FORGOT PASSWORD TESTS
//
//    @Test
//    void forgotPassword_ValidEmail_SendsResetToken() {
//        // Arrange
//        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
//                .email(sampleUserAuth.getEmail())
//                .build();
//        String resetToken = "resetToken123";
//        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(jwtUtil.generateTokenWithExpiration(sampleUserAuth.getEmail(), 5)).thenReturn(resetToken);
//        doNothing().when(emailService).sendResetPasswordEmail(eq(sampleUserAuth.getEmail()), eq(sampleUser.getLastName()), eq(resetToken), eq(5));
//
//        // Act
//        authService.forgotPassword(request);
//
//        // Assert
//        assertEquals(resetToken, sampleUserAuth.getVerificationToken());
//        verify(userAuthRepository).save(sampleUserAuth);
//        verify(emailService).sendResetPasswordEmail(sampleUserAuth.getEmail(), sampleUser.getLastName(), resetToken, 5);
//    }
//
//    @Test
//    void forgotPassword_EmailSendFailure_ThrowsBaseException() {
//        // Arrange
//        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
//                .email(sampleUserAuth.getEmail())
//                .build();
//        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(jwtUtil.generateTokenWithExpiration(sampleUserAuth.getEmail(), 5)).thenReturn("resetToken");
//        doThrow(new RuntimeException("Email failed")).when(emailService).sendResetPasswordEmail(anyString(), anyString(), anyString(), anyInt());
//
//        // Act & Assert
//        BaseException exception = assertThrows(BaseException.class, () -> authService.forgotPassword(request));
//        assertEquals("Failed to send email to user " + request.getEmail(), exception.getMessage());
//        verify(userAuthRepository).save(sampleUserAuth); // Token still set
//        // Optional: If forgotPassword returns a response, capture and verify it
//        // e.g., if it returns a ResponseEntity, add: ResponseEntity<?> response = authService.forgotPassword(request);
//    }
//
//    // RESET PASSWORD TESTS
//
//    @Test
//    void resetPassword_ValidToken_UpdatesPassword() {
//        // Arrange
//        ResetPasswordRequest request = ResetPasswordRequest.builder()
//                .token(fakeToken)
//                .newPassword("newPassword")
//                .build();
//        when(jwtUtil.extractUsernameAllowExpired(fakeToken)).thenReturn(sampleUserAuth.getEmail());
//        when(jwtUtil.extractExpirationAllowExpired(fakeToken)).thenReturn(new Date(System.currentTimeMillis() + 10000)); // Not expired
//        when(userAuthRepository.findByEmail(sampleUserAuth.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        sampleUserAuth.setVerificationToken(fakeToken);
//        when(passwordEncoder.encode(request.getNewPassword())).thenReturn("encodedNewPassword");
//        when(passwordEncoder.matches(request.getNewPassword(), sampleUserAuth.getPasswordHash())).thenReturn(false);
//
//        // Act
//        authService.resetPassword(request);
//
//        // Assert
//        assertEquals("encodedNewPassword", sampleUserAuth.getPasswordHash());
//        assertNull(sampleUserAuth.getVerificationToken());
//        verify(userAuthRepository).save(sampleUserAuth);
//    }
//
//    @Test
//    void resetPassword_ExpiredToken_ThrowsInvalidInputException() {
//        // Arrange
//        ResetPasswordRequest request = ResetPasswordRequest.builder()
//                .token(fakeToken)
//                .newPassword("newPassword")
//                .build();
//        when(jwtUtil.extractExpirationAllowExpired(fakeToken)).thenReturn(new Date(System.currentTimeMillis() - 10000)); // Expired
//
//        // Act & Assert
//        assertThrows(InvalidInputException.class, () -> authService.resetPassword(request));
//        verify(userAuthRepository, never()).save(any());
//    }
//
//    // LOGOUT TESTS
//
//    @Test
//    void logout_ValidToken_BlacklistsToken() {
//        // Arrange
//        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
//        when(jwtUtil.extractUsernameAllowExpired(fakeToken)).thenReturn(sampleUserAuth.getEmail());
//        when(userAuthRepository.findByEmail(sampleUserAuth.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        when(jwtUtil.isTokenSignatureValid(fakeToken)).thenReturn(true);
//        Date expiresAt = new Date(System.currentTimeMillis() + 3600000); // 1 hour
//        when(jwtUtil.extractExpirationAllowExpired(fakeToken)).thenReturn(expiresAt);
//        when(tokenValidationService.isTokenBlacklisted(fakeToken)).thenReturn(false); // This might be unnecessary
//
//        // Act
//        authService.logout(httpServletRequest);
//
//        // Assert
//        verify(blacklistService).blacklistToken(fakeToken, expiresAt);
//    }
//
//    @Test
//    void logout_InvalidSignature_ThrowsTokenInvalidException() {
//        // Arrange
//        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
//        when(jwtUtil.isTokenSignatureValid(fakeToken)).thenReturn(false);
//
//        // Act & Assert
//        assertThrows(TokenInvalidException.class, () -> authService.logout(httpServletRequest));
//        verify(blacklistService, never()).blacklistToken(anyString(), any());
//    }
//
//    // REFRESH TOKEN TESTS
//
//    @Test
//    void refreshToken_ValidToken_ReturnsNewToken() {
//        // Arrange
//        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
//        when(jwtUtil.extractUsernameAllowExpired(fakeToken)).thenReturn(sampleUserAuth.getEmail());
//        when(userAuthRepository.findByEmail(sampleUserAuth.getEmail())).thenReturn(Optional.of(sampleUserAuth));
//        Date expiresAt = new Date(System.currentTimeMillis() + 60000); // 1 min
//        when(jwtUtil.extractExpirationAllowExpired(fakeToken)).thenReturn(expiresAt);
//        when(blacklistService.markTokenUsedForRefresh(fakeToken, 60000)).thenReturn(true);
//        when(blacklistService.isTokenBlacklisted(fakeToken)).thenReturn(false);
//        when(blacklistService.areAllUserTokensBlacklisted(sampleUserAuth.getEmail())).thenReturn(false);
//        String newToken = "newToken123";
//        when(jwtUtil.generateToken(sampleUserAuth.getEmail(), sampleUser.getRole())).thenReturn(newToken);
//
//        // Act
//        LoginResponse response = authService.refreshToken(httpServletRequest);
//
//        // Assert
//        assertNotNull(response);
//        assertEquals(newToken, response.token());
//        verify(jwtUtil).generateToken(sampleUserAuth.getEmail(), sampleUser.getRole());
//    }
//
//    @Test
//    void refreshToken_AlreadyUsed_ThrowsTokenInvalidException() {
//        // Arrange
//        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + fakeToken);
//        when(jwtUtil.extractUsernameAllowExpired(fakeToken)).thenReturn(sampleUserAuth.getEmail());
//        Date expiresAt = new Date(System.currentTimeMillis() + 60000); // 1 minute from now
//        when(jwtUtil.extractExpirationAllowExpired(fakeToken)).thenReturn(expiresAt);
//        when(blacklistService.markTokenUsedForRefresh(anyString(), anyLong())).thenReturn(false); // Use matchers for both arguments
//
//        // Act & Assert
//        assertThrows(TokenInvalidException.class, () -> authService.refreshToken(httpServletRequest));
//        verify(jwtUtil, never()).generateToken(anyString(), anyString());
//    }
//}
