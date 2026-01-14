package SEP490.EduPrompt.auth;

import SEP490.EduPrompt.dto.request.GoogleLoginRequeset;
import SEP490.EduPrompt.dto.request.LoginRequest;
import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.exception.auth.AuthFailedException;
import SEP490.EduPrompt.exception.auth.EmailAlreadyExistedException;
import SEP490.EduPrompt.exception.auth.InvalidCredentialsException;
import SEP490.EduPrompt.exception.auth.UserNotVerifiedException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.AuthServiceImpl;
import SEP490.EduPrompt.service.auth.EmailService;
import SEP490.EduPrompt.service.auth.TokenBlacklistService;
import SEP490.EduPrompt.service.auth.TokenValidationService;
import SEP490.EduPrompt.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import org.mockito.MockedConstruction;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;


import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAuthRepository userAuthRepository;
    @Mock private SchoolEmailRepository schoolEmailRepository;
    @Mock private SubscriptionTierRepository subscriptionTierRepository;
    @Mock private SchoolSubscriptionRepository schoolSubscriptionRepository;
    @Mock private UserQuotaRepository userQuotaRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private TokenValidationService tokenValidationService;

    // These are injected but not used in login(), strictly speaking we don't need to stub them
    // but MockitoExtension needs to know they exist if strict stubbing is on.
    // However, for unit testing specific methods, we focus on the used mocks.

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<UserAuth> userAuthCaptor;

    // --- Helper Data ---
    private final String EMAIL = "teacher@test.com";
    private final String PASSWORD = "password123";
    private final String ENCODED_PASSWORD = "encodedPassword123";
    private final String MOCK_TOKEN = "eyJhbGciOiJIUzI1...";
    private final String RAW_EMAIL = "TEST.USER@Example.com";
    private final String CLEAN_EMAIL = "test.user@example.com";
    private final String RAW_PASSWORD = "password123";
    private final String VERIFY_TOKEN = "valid.verification.token";

    @BeforeEach
    void setUp() {
        // This manually injects the value into the private field "@Value"
        ReflectionTestUtils.setField(authService, "googleClientId", "mock-google-client-id");
    }

    //Login
    @Test
    @DisplayName("Case 1: Login Success - Valid credentials and active/verified user")
    void login_WhenCredentialsValid_ShouldReturnToken() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);

        User user = User.builder()
                .email(EMAIL)
                .role(Role.TEACHER.name())
                .isActive(true)
                .isVerified(true)
                .build();

        UserAuth userAuth = UserAuth.builder()
                .email(EMAIL)
                .passwordHash(ENCODED_PASSWORD)
                .user(user)
                .build();

        // 1. Mock finding the user
        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));
        // 2. Mock password match
        when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        // 3. Mock token generation
        when(jwtUtil.generateToken(EMAIL, Role.TEACHER.name())).thenReturn(MOCK_TOKEN);

        // Act
        LoginResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals(MOCK_TOKEN, response.token());

        // Verify last login was updated (save was called)
        verify(userAuthRepository).save(userAuth);
        assertNotNull(userAuth.getLastLogin()); // check if timestamp was actually set
    }

    @Test
    @DisplayName("Case 2: Login Fail - Email not found")
    void login_WhenEmailNotFound_ShouldThrowInvalidCredentialsException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@test.com");
        request.setPassword(PASSWORD);

        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        // Verify we never checked password or generated token
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Case 3: Login Fail - Wrong Password")
    void login_WhenPasswordInvalid_ShouldThrowAuthFailedException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("wrongPassword");

        UserAuth userAuth = UserAuth.builder()
                .email(EMAIL)
                .passwordHash(ENCODED_PASSWORD)
                .build();

        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("wrongPassword", ENCODED_PASSWORD)).thenReturn(false);

        // Act & Assert
        AuthFailedException exception = assertThrows(AuthFailedException.class, () -> authService.login(request));
        assertEquals("Invalid password", exception.getMessage());

        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Case 4: Login Fail - User Not Active")
    void login_WhenUserNotActive_ShouldThrowUserNotVerifiedException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);

        User user = User.builder()
                .isActive(false) // <--- TARGET
                .isVerified(true)
                .build();

        UserAuth userAuth = UserAuth.builder()
                .email(EMAIL)
                .passwordHash(ENCODED_PASSWORD)
                .user(user)
                .build();

        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        // Act & Assert
        assertThrows(UserNotVerifiedException.class, () -> authService.login(request));

        verify(jwtUtil, never()).generateToken(any(), any());
        verify(userAuthRepository, never()).save(any()); // ensure we didn't update last login
    }

    @Test
    @DisplayName("Case 5: Login Fail - User Not Verified")
    void login_WhenUserNotVerified_ShouldThrowUserNotVerifiedException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);

        User user = User.builder()
                .isActive(true)
                .isVerified(false) // <--- TARGET
                .build();

        UserAuth userAuth = UserAuth.builder()
                .email(EMAIL)
                .passwordHash(ENCODED_PASSWORD)
                .user(user)
                .build();

        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        // Act & Assert
        assertThrows(UserNotVerifiedException.class, () -> authService.login(request));

        verify(jwtUtil, never()).generateToken(any(), any());
    }

    //Register
    @Test
    @DisplayName("Case 1: Register Success - Should save User/UserAuth and send Email")
    void register_WhenValidRequest_ShouldSaveAndSendEmail() {

        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail(RAW_EMAIL);
        request.setPassword(RAW_PASSWORD);
        request.setPhoneNumber("0987654321");

        when(userAuthRepository.existsByEmail(CLEAN_EMAIL)).thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        when(jwtUtil.generateTokenWithExpiration(eq(RAW_EMAIL), anyInt())).thenReturn("mock-verify-token");

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertTrue(response.message().contains("Check your email"));

        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals(CLEAN_EMAIL, capturedUser.getEmail());
        assertEquals(Role.TEACHER.name(), capturedUser.getRole());
        assertFalse(capturedUser.getIsActive());
        assertFalse(capturedUser.getIsVerified());

        verify(userAuthRepository).save(userAuthCaptor.capture());
        UserAuth capturedAuth = userAuthCaptor.getValue();
        assertEquals(CLEAN_EMAIL, capturedAuth.getEmail());
        assertEquals(ENCODED_PASSWORD, capturedAuth.getPasswordHash());
        assertEquals("mock-verify-token", capturedAuth.getVerificationToken());
        assertEquals(capturedUser, capturedAuth.getUser()); // Relationship check

        verify(emailService).sendVerificationEmail(eq(RAW_EMAIL), eq("User"), eq("mock-verify-token"));
    }

    @Test
    @DisplayName("Case 2: Register Fail - Email Already Exists")
    void register_WhenEmailExists_ShouldThrowException() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail(RAW_EMAIL);

        when(userAuthRepository.existsByEmail(CLEAN_EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyExistedException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
        verify(userAuthRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }
    @Test
    @DisplayName("Case 3: Register Partial Success - Email Service Fails")
    void register_WhenEmailServiceFails_ShouldReturnWarningMessage() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail(RAW_EMAIL);
        request.setPassword(RAW_PASSWORD);
        request.setLastName("User");

        when(userAuthRepository.existsByEmail(CLEAN_EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateTokenWithExpiration(any(), anyInt())).thenReturn("token");

        doThrow(new RuntimeException("SMTP Server Down"))
                .when(emailService).sendVerificationEmail(any(), any(), any());

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertTrue(response.message().contains("email sending failed"));
        assertTrue(response.message().contains("contact support"));

        verify(userRepository).save(any(User.class));
        verify(userAuthRepository).save(any(UserAuth.class));
    }

    //Verify email
    @Test
    @DisplayName("Case 1: Verify Success - Standard User (No School Email)")
    void verifyEmail_WhenStandardUser_ShouldVerifyAndSetFreeQuota() {

        User user = User.builder().id(UUID.randomUUID()).email(EMAIL).isVerified(false).build();
        UserAuth userAuth = UserAuth.builder().user(user).email(EMAIL).verificationToken(VERIFY_TOKEN).build();
        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setId(UUID.randomUUID());
        freeTier.setIndividualTokenLimit(100);

        when(jwtUtil.extractUsernameAllowExpired(VERIFY_TOKEN)).thenReturn(EMAIL);

        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));

        when(schoolEmailRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        when(subscriptionTierRepository.findByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));

        authService.verifyEmail(VERIFY_TOKEN);

        assertTrue(user.getIsVerified());
        assertTrue(user.getIsActive());
        assertNotNull(user.getUpdatedAt());
        verify(userRepository, times(2)).save(user); // Saved once for status, once for tier

        assertNull(userAuth.getVerificationToken());
        verify(userAuthRepository).save(userAuth);

        ArgumentCaptor<UserQuota> quotaCaptor = ArgumentCaptor.forClass(UserQuota.class);
        verify(userQuotaRepository).save(quotaCaptor.capture());
        UserQuota savedQuota = quotaCaptor.getValue();

        assertEquals(user, savedQuota.getUser());
        assertEquals(freeTier, savedQuota.getSubscriptionTier());
        assertEquals(100, savedQuota.getIndividualTokenLimit());

        verify(emailService).sendWelcomeEmail(eq(EMAIL), any());
    }
    @Test
    @DisplayName("Case 2: Verify Success - School User (With Active Subscription)")
    void verifyEmail_WhenSchoolUser_ShouldLinkSchoolAndSubscription() {
        // Arrange
        User user = User.builder().email(EMAIL).isVerified(false).build();
        UserAuth userAuth = UserAuth.builder().user(user).verificationToken(VERIFY_TOKEN).build();
        SubscriptionTier freeTier = new SubscriptionTier(); // Needed for fallback props

        // School Data Setup
        UUID schoolId = UUID.randomUUID();
        School school = new School();
        school.setId(schoolId);
        SchoolEmail schoolEmail = new SchoolEmail();
        schoolEmail.setSchool(school);

        SchoolSubscription schoolSub = new SchoolSubscription();
        schoolSub.setId(UUID.randomUUID());

        when(jwtUtil.extractUsernameAllowExpired(VERIFY_TOKEN)).thenReturn(EMAIL);
        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));

        // Mock School Found
        when(schoolEmailRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(schoolEmail));
        // Mock Active School Subscription Found
        when(schoolSubscriptionRepository.findActiveBySchoolId(schoolId)).thenReturn(Optional.of(schoolSub));
        // Mock Free Tier (still needed for basic tier assignment in code)
        when(subscriptionTierRepository.findByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));

        // Act
        authService.verifyEmail(VERIFY_TOKEN);

        // Assert
        verify(userRepository, atLeastOnce()).save(user);

        // Check School ID was set on user
        assertEquals(schoolId, user.getSchoolId());

        // Check Quota linked to School Subscription
        ArgumentCaptor<UserQuota> quotaCaptor = ArgumentCaptor.forClass(UserQuota.class);
        verify(userQuotaRepository).save(quotaCaptor.capture());
        UserQuota savedQuota = quotaCaptor.getValue();

        assertEquals(schoolSub, savedQuota.getSchoolSubscription());
    }
    @Test
    @DisplayName("Case 3: Verify Success - Idempotency (Already Verified)")
    void verifyEmail_WhenAlreadyVerified_ShouldReturnEarly() {
        // Arrange
        User user = User.builder().isVerified(true).build(); // Already verified
        UserAuth userAuth = UserAuth.builder().user(user).verificationToken(VERIFY_TOKEN).build();

        when(jwtUtil.extractUsernameAllowExpired(VERIFY_TOKEN)).thenReturn(EMAIL);
        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));

        // Act
        authService.verifyEmail(VERIFY_TOKEN);

        // Assert
        // Should return immediately after log check
        verify(userRepository, never()).save(any());
        verify(userQuotaRepository, never()).save(any());
        verify(emailService, never()).sendWelcomeEmail(any(), any());
    }

    @Test
    @DisplayName("Case 4: Verify Fail - Invalid Token (Mismatch)")
    void verifyEmail_WhenTokenMismatch_ShouldThrowException() {
        // Arrange
        UserAuth userAuth = UserAuth.builder().verificationToken("real-token").build();

        when(jwtUtil.extractUsernameAllowExpired(VERIFY_TOKEN)).thenReturn(EMAIL);
        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));

        // Act & Assert
        // The incoming token (VERIFY_TOKEN) does not match "real-token"
        assertThrows(SEP490.EduPrompt.exception.auth.InvalidInputException.class,
                () -> authService.verifyEmail(VERIFY_TOKEN));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case 5: Verify Fail - User Not Found")
    void verifyEmail_WhenUserNotFound_ShouldThrowException() {
        // Arrange
        when(jwtUtil.extractUsernameAllowExpired(VERIFY_TOKEN)).thenReturn(EMAIL);
        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.InvalidCredentialsException.class,
                () -> authService.verifyEmail(VERIFY_TOKEN));
    }

    @Test
    @DisplayName("Case 6: Verify Fail - Malformed Token (Jwt Exception)")
    void verifyEmail_WhenJwtInvalid_ShouldThrowInvalidInputException() {
        // Arrange
        when(jwtUtil.extractUsernameAllowExpired(any())).thenThrow(new RuntimeException("Malformed"));

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.InvalidInputException.class,
                () -> authService.verifyEmail("bad-token"));
    }

    @Test
    @DisplayName("Case 7: Verify Success - Welcome Email Fails")
    void verifyEmail_WhenEmailFails_ShouldNotRollback() {
        // Arrange
        User user = User.builder().id(UUID.randomUUID()).email(EMAIL).isVerified(false).build();
        UserAuth userAuth = UserAuth.builder().user(user).verificationToken(VERIFY_TOKEN).build();
        SubscriptionTier freeTier = new SubscriptionTier();

        when(jwtUtil.extractUsernameAllowExpired(VERIFY_TOKEN)).thenReturn(EMAIL);
        when(userAuthRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAuth));
        when(subscriptionTierRepository.findByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));

        // Mock Email Failure
        doThrow(new RuntimeException("Mail Error"))
                .when(emailService).sendWelcomeEmail(any(), any());

        // Act
        authService.verifyEmail(VERIFY_TOKEN);

        // Assert
        // Code swallows the email exception, so it should succeed purely
        assertTrue(user.getIsVerified());
        verify(userRepository, atLeastOnce()).save(user);
    }

    // Resend Verification Tests

    @Test
    @DisplayName("Resend Case 1: Success - Should generate new token and send email")
    void resendVerification_WhenUserUnverified_ShouldUpdateTokenAndSend() {
        // Arrange
        String email = "resend@test.com";
        User user = User.builder().lastName("Doe").isVerified(false).build();
        UserAuth userAuth = UserAuth.builder().user(user).email(email).build();
        String newToken = "new-token-123";

        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.of(userAuth));
        when(jwtUtil.generateTokenWithExpiration(eq(email), anyInt())).thenReturn(newToken);

        // Act
        authService.resendVerificationEmail(email);

        // Assert
        // 1. Verify Token Updated in Object
        assertEquals(newToken, userAuth.getVerificationToken());

        // 2. Verify Save Called
        verify(userAuthRepository).save(userAuth);

        // 3. Verify Email Sent
        verify(emailService).sendVerificationEmail(email, "Doe", newToken);
    }

    @Test
    @DisplayName("Resend Case 2: Fail - User Not Found")
    void resendVerification_WhenUserNotFound_ShouldThrowException() {
        // Arrange
        String email = "unknown@test.com";
        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.InvalidCredentialsException.class,
                () -> authService.resendVerificationEmail(email));

        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Resend Case 3: Fail - User Already Verified")
    void resendVerification_WhenUserVerified_ShouldThrowException() {
        // Arrange
        String email = "verified@test.com";
        User user = User.builder().isVerified(true).build(); // <--- Verified
        UserAuth userAuth = UserAuth.builder().user(user).email(email).build();

        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.of(userAuth));

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.UserVerifiedException.class,
                () -> authService.resendVerificationEmail(email));

        // Verify no new token generated
        verify(jwtUtil, never()).generateTokenWithExpiration(any(), anyInt());
    }

    // Change Password Tests

    @Test
    @DisplayName("ChangePwd Case 1: Success - correct old password")
    void changePassword_WhenCredentialsValid_ShouldUpdateHash() {
        // Arrange
        SEP490.EduPrompt.dto.request.ChangePasswordRequest request = new SEP490.EduPrompt.dto.request.ChangePasswordRequest();
        request.setEmail("user@test.com");
        request.setOldPassword("oldPass");
        request.setNewPassword("newPass");

        UserAuth userAuth = UserAuth.builder()
                .email("user@test.com")
                .passwordHash("encodedOldPass")
                .build();

        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("oldPass", "encodedOldPass")).thenReturn(true); // Match success
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");

        // Act
        authService.changePassword(request);

        // Assert
        assertEquals("encodedNewPass", userAuth.getPasswordHash());
        assertNotNull(userAuth.getUpdatedAt());
        verify(userAuthRepository).save(userAuth);
    }

    @Test
    @DisplayName("ChangePwd Case 2: Fail - Wrong old password")
    void changePassword_WhenOldPasswordWrong_ShouldThrowException() {
        // Arrange
        SEP490.EduPrompt.dto.request.ChangePasswordRequest request = new SEP490.EduPrompt.dto.request.ChangePasswordRequest();
        request.setEmail("user@test.com");
        request.setOldPassword("wrongPass");

        UserAuth userAuth = UserAuth.builder()
                .passwordHash("encodedCorrectPass")
                .build();

        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("wrongPass", "encodedCorrectPass")).thenReturn(false); // Match fail

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.InvalidInputException.class,
                () -> authService.changePassword(request));

        // Verify we never saved a new password
        verify(userAuthRepository, never()).save(any());
    }

    // --- Forgot Password Tests ---

    @Test
    @DisplayName("ForgotPwd Case 1: Success - Should set token and send email")
    void forgotPassword_WhenUserExists_ShouldSendEmail() {
        // Arrange
        SEP490.EduPrompt.dto.request.ForgotPasswordRequest request = new SEP490.EduPrompt.dto.request.ForgotPasswordRequest();
        request.setEmail("forgot@test.com");

        User user = User.builder().lastName("Doe").build();
        UserAuth userAuth = UserAuth.builder().user(user).email("forgot@test.com").build();
        String token = "reset-token";

        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(userAuth));
        when(jwtUtil.generateTokenWithExpiration(eq("forgot@test.com"), anyInt())).thenReturn(token);

        // Act
        authService.forgotPassword(request);

        // Assert
        assertEquals(token, userAuth.getVerificationToken());
        verify(emailService).sendPasswordResetEmail("forgot@test.com", "Doe", token, 5);
        verify(userAuthRepository).save(userAuth);
    }

    @Test
    @DisplayName("ForgotPwd Case 2: Fail - Email Sending Error")
    void forgotPassword_WhenEmailFails_ShouldThrowBaseException() {
        // Arrange
        SEP490.EduPrompt.dto.request.ForgotPasswordRequest request = new SEP490.EduPrompt.dto.request.ForgotPasswordRequest();
        request.setEmail("error@test.com");

        User user = User.builder().lastName("Doe").build();
        UserAuth userAuth = UserAuth.builder().user(user).email("error@test.com").build();

        when(userAuthRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(userAuth));
        when(jwtUtil.generateTokenWithExpiration(any(), anyInt())).thenReturn("token");

        // Mock Email Failure
        doThrow(new RuntimeException("SMTP Error"))
                .when(emailService).sendPasswordResetEmail(any(), any(), any(), anyInt());

        // Act & Assert
        // Code wraps generic exception into BaseException(AUTH_FAILED)
        SEP490.EduPrompt.exception.BaseException ex = assertThrows(SEP490.EduPrompt.exception.BaseException.class,
                () -> authService.forgotPassword(request));

        assertEquals("Failed to send email to user " + request.getEmail(), ex.getMessage());
    }

    // Reset Password Tests
    @Test
    @DisplayName("ResetPwd Case 1: Success - Valid token and new password")
    void resetPassword_WhenValid_ShouldUpdatePassword() {
        // Arrange
        SEP490.EduPrompt.dto.request.ResetPasswordRequest request = new SEP490.EduPrompt.dto.request.ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("newPass");

        String email = "reset@test.com";
        Date futureDate = new Date(System.currentTimeMillis() + 10000); // Not expired

        UserAuth userAuth = UserAuth.builder()
                .email(email)
                .verificationToken("valid-token") // Matches request
                .passwordHash("oldEncoded")
                .build();

        // 1. Mock JWT Extraction
        when(jwtUtil.extractUsernameAllowExpired("valid-token")).thenReturn(email);
        when(jwtUtil.extractExpirationAllowExpired("valid-token")).thenReturn(futureDate);

        // 2. Mock DB Lookups
        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.of(userAuth));

        // 3. Mock Password checks
        when(passwordEncoder.matches("newPass", "oldEncoded")).thenReturn(false); // Not same as old
        when(passwordEncoder.encode("newPass")).thenReturn("newEncoded");

        // Act
        authService.resetPassword(request);

        // Assert
        assertEquals("newEncoded", userAuth.getPasswordHash());
        assertNull(userAuth.getVerificationToken()); // Should be cleared
        verify(userAuthRepository).save(userAuth);
    }

    @Test
    @DisplayName("ResetPwd Case 2: Fail - Token Expired")
    void resetPassword_WhenTokenExpired_ShouldThrowException() {
        // Arrange
        SEP490.EduPrompt.dto.request.ResetPasswordRequest request = new SEP490.EduPrompt.dto.request.ResetPasswordRequest();
        request.setToken("expired-token");

        Date pastDate = new Date(System.currentTimeMillis() - 10000); // Expired

        when(jwtUtil.extractUsernameAllowExpired("expired-token")).thenReturn("user@test.com");
        when(jwtUtil.extractExpirationAllowExpired("expired-token")).thenReturn(pastDate);

        // Act & Assert
        SEP490.EduPrompt.exception.auth.InvalidInputException ex = assertThrows(
                SEP490.EduPrompt.exception.auth.InvalidInputException.class,
                () -> authService.resetPassword(request)
        );
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("ResetPwd Case 3: Fail - New Password same as Old")
    void resetPassword_WhenDuplicatePassword_ShouldThrowException() {
        // Arrange
        SEP490.EduPrompt.dto.request.ResetPasswordRequest request = new SEP490.EduPrompt.dto.request.ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("samePass");

        Date futureDate = new Date(System.currentTimeMillis() + 10000);
        UserAuth userAuth = UserAuth.builder()
                .verificationToken("valid-token")
                .passwordHash("encodedSamePass")
                .build();

        when(jwtUtil.extractUsernameAllowExpired(any())).thenReturn("user@test.com");
        when(jwtUtil.extractExpirationAllowExpired(any())).thenReturn(futureDate);
        when(userAuthRepository.findByEmail(any())).thenReturn(Optional.of(userAuth));

        // Mock Match TRUE (Password is same)
        when(passwordEncoder.matches("samePass", "encodedSamePass")).thenReturn(true);

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.DuplicatePasswordException.class,
                () -> authService.resetPassword(request));

        verify(userAuthRepository, never()).save(any());
    }

    @Test
    @DisplayName("ResetPwd Case 4: Fail - Token Mismatch (DB vs Request)")
    void resetPassword_WhenTokenMismatch_ShouldThrowException() {
        // Arrange
        SEP490.EduPrompt.dto.request.ResetPasswordRequest request = new SEP490.EduPrompt.dto.request.ResetPasswordRequest();
        request.setToken("incoming-token");

        UserAuth userAuth = UserAuth.builder()
                .verificationToken("different-db-token") // <--- Mismatch
                .build();

        when(jwtUtil.extractUsernameAllowExpired(any())).thenReturn("user@test.com");
        when(jwtUtil.extractExpirationAllowExpired(any())).thenReturn(new Date(System.currentTimeMillis() + 10000));
        when(userAuthRepository.findByEmail(any())).thenReturn(Optional.of(userAuth));

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.InvalidInputException.class,
                () -> authService.resetPassword(request));
    }

    // Logout Tests

    @Test
    @DisplayName("Logout Case 1: Success - Valid token should be blacklisted")
    void logout_WhenTokenValid_ShouldBlacklist() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String token = "valid.jwt.token";
        String email = "logout@test.com";
        Date expiresAt = new Date(System.currentTimeMillis() + 3600000); // +1 hour

        // 1. Mock Header Extraction
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);

        // 2. Mock Validation Checks
        when(jwtUtil.isTokenSignatureValid(token)).thenReturn(true);
        when(tokenValidationService.isTokenBlacklisted(token)).thenReturn(false);

        // 3. Mock Claims Extraction
        when(jwtUtil.extractUsernameAllowExpired(token)).thenReturn(email);
        when(jwtUtil.extractExpirationAllowExpired(token)).thenReturn(expiresAt);

        // 4. Mock User Existence
        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.of(new UserAuth()));

        // Act
        authService.logout(httpRequest);

        // Assert
        verify(blacklistService).blacklistToken(token, expiresAt);
    }

    @Test
    @DisplayName("Logout Case 2: Fail - Invalid Signature")
    void logout_WhenSignatureInvalid_ShouldThrowException() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String token = "tampered.token";
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);

        when(jwtUtil.isTokenSignatureValid(token)).thenReturn(false);

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.TokenInvalidException.class,
                () -> authService.logout(httpRequest));

        verify(blacklistService, never()).blacklistToken(any(), any());
    }

    @Test
    @DisplayName("Logout Case 3: Fail - Already Blacklisted")
    void logout_WhenAlreadyBlacklisted_ShouldThrowException() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String token = "old.token";
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);

        when(jwtUtil.isTokenSignatureValid(token)).thenReturn(true);
        when(tokenValidationService.isTokenBlacklisted(token)).thenReturn(true); // <--- Already listed

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.TokenInvalidException.class,
                () -> authService.logout(httpRequest));

        verify(blacklistService, never()).blacklistToken(any(), any());
    }

    // Refresh Token Tests

    @Test
    @DisplayName("Refresh Case 1: Success - Valid used token generates new one")
    void refreshToken_WhenValid_ShouldReturnNewToken() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String oldToken = "expired.but.valid.token";
        String newToken = "new.fresh.token";
        String email = "refresh@test.com";
        Date expiresAt = new Date(System.currentTimeMillis() - 1000); // Expired 1 sec ago

        User user = User.builder().role(Role.TEACHER.name()).isActive(true).isVerified(true).build();
        UserAuth userAuth = UserAuth.builder().user(user).build();

        // 1. Setup Request
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + oldToken);

        // 2. Setup JWT Extraction
        when(jwtUtil.extractUsernameAllowExpired(oldToken)).thenReturn(email);
        when(jwtUtil.extractExpirationAllowExpired(oldToken)).thenReturn(expiresAt);

        // 3. Setup Security Checks (Passes)
        when(blacklistService.isTokenBlacklisted(oldToken)).thenReturn(false);
        when(blacklistService.areAllUserTokensBlacklisted(email)).thenReturn(false);
        // Important: markTokenUsedForRefresh returns TRUE (meaning it wasn't used before)
        when(blacklistService.markTokenUsedForRefresh(eq(oldToken), anyLong())).thenReturn(true);

        // 4. Setup User
        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.of(userAuth));
        when(jwtUtil.generateToken(email, Role.TEACHER.name())).thenReturn(newToken);

        // Act
        LoginResponse response = authService.refreshToken(httpRequest);

        // Assert
        assertEquals(newToken, response.token());
    }

    @Test
    @DisplayName("Refresh Case 2: Fail - Token Reuse Detected (Replay Attack)")
    void refreshToken_WhenTokenReused_ShouldThrowException() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String stolenToken = "stolen.token";

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + stolenToken);
        when(jwtUtil.extractUsernameAllowExpired(stolenToken)).thenReturn("victim@test.com");
        when(jwtUtil.extractExpirationAllowExpired(stolenToken)).thenReturn(new Date());

        // Mock checks
        when(blacklistService.isTokenBlacklisted(stolenToken)).thenReturn(false);

        // FAIL HERE: markTokenUsedForRefresh returns FALSE (Already used!)
        when(blacklistService.markTokenUsedForRefresh(eq(stolenToken), anyLong())).thenReturn(false);

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.TokenInvalidException.class,
                () -> authService.refreshToken(httpRequest));

        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Refresh Case 3: Fail - User Banned/Inactive")
    void refreshToken_WhenUserBanned_ShouldThrowException() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String token = "token";
        User user = User.builder().isActive(false).build(); // <--- Inactive
        UserAuth userAuth = UserAuth.builder().user(user).build();

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsernameAllowExpired(token)).thenReturn("banned@test.com");
        when(jwtUtil.extractExpirationAllowExpired(token)).thenReturn(new Date());

        // Validation passes
        when(blacklistService.isTokenBlacklisted(token)).thenReturn(false);
        when(blacklistService.markTokenUsedForRefresh(any(), anyLong())).thenReturn(true);

        // User lookup
        when(userAuthRepository.findByEmail("banned@test.com")).thenReturn(Optional.of(userAuth));

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.UserNotVerifiedException.class,
                () -> authService.refreshToken(httpRequest));
    }

    // Get Personal Info Tests

    @Test
    @DisplayName("Me Case 1: Success - Returns correct profile data")
    void getPersonalInfo_WhenValid_ShouldReturnProfile() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String token = "valid.token";
        String email = "me@test.com";
        UUID freeTierId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();

        // Setup Subscription Tiers
        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setId(freeTierId);

        SubscriptionTier proTier = new SubscriptionTier();
        proTier.setId(UUID.randomUUID());

        SubscriptionTier premiumTier = new SubscriptionTier();
        premiumTier.setId(UUID.randomUUID());

        // Setup User
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .firstName("John")
                .role(Role.TEACHER.name())
                .subscriptionTierId(freeTierId) // User is Free Tier
                .schoolId(schoolId)
                .isActive(true)
                .isVerified(true)
                .build();
        UserAuth userAuth = UserAuth.builder().user(user).build();

        // Mocks
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenReturn(email);
        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.of(userAuth));

        // Mock Tier Repo Lookups (Needed for boolean flags isFreeTier/isProTier etc)
        when(subscriptionTierRepository.findByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));
        when(subscriptionTierRepository.findByNameIgnoreCase("pro")).thenReturn(Optional.of(proTier));
        when(subscriptionTierRepository.findByNameIgnoreCase("premium")).thenReturn(Optional.of(premiumTier));

        // Mock School Sub Check
        when(schoolSubscriptionRepository.findActiveBySchoolId(schoolId)).thenReturn(Optional.empty());

        // Act
        SEP490.EduPrompt.dto.response.PersonalInfoResponse response = authService.getPersonalInfo(httpRequest);

        // Assert
        assertEquals(email, response.email());
        assertEquals("John", response.firstName());
        assertTrue(response.isTeacher()); // Role check
        assertTrue(response.isFreeTier()); // Tier check
        assertFalse(response.isProTier());
        assertFalse(response.hasSchoolSubscription());
    }

    @Test
    @DisplayName("Me Case 2: Fail - User Not Found (Deleted Account)")
    void getPersonalInfo_WhenUserNotFound_ShouldThrowException() {
        // Arrange
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        String token = "valid.token";
        String email = "deleted@test.com";

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenReturn(email);

        // Mock DB returning Empty
        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(SEP490.EduPrompt.exception.auth.TokenInvalidException.class,
                () -> authService.getPersonalInfo(httpRequest));
    }

    // Google Login Test
    @Test
    @DisplayName("Google Case 1: Success - Existing User Login")
    void googleLogin_WhenUserExists_ShouldLogin() throws Exception {
        // Arrange
        String googleTokenStr = "valid-google-token-string";
        String email = "existing@google.com";
        String googleId = "gid-12345";

        GoogleLoginRequeset request = new GoogleLoginRequeset();
        request.setTokenId(googleTokenStr);

        // 1. Prepare the Mock Google Token Data
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setSubject(googleId);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        when(mockIdToken.getPayload()).thenReturn(payload);

        // 2. Prepare Mock Database Data
        User user = User.builder().email(email).role(Role.TEACHER.name()).build();
        UserAuth existingAuth = UserAuth.builder().user(user).email(email).lastLogin(Instant.now()).build();

        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.of(existingAuth));
        when(jwtUtil.generateToken(email, Role.TEACHER.name())).thenReturn("app-jwt-token");

        // 3. INTERCEPT THE "NEW" CALL
        // We mock the Builder because the code calls: new GoogleIdTokenVerifier.Builder(...)
        try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder = mockConstruction(
                GoogleIdTokenVerifier.Builder.class,
                (mock, context) -> {
                    // Create a mock Verifier that will be returned by builder.build()
                    GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
                    when(mockVerifier.verify(googleTokenStr)).thenReturn(mockIdToken);

                    // Configure the Builder to return our mock Verifier
                    when(mock.setAudience(any())).thenReturn(mock);
                    when(mock.build()).thenReturn(mockVerifier);
                }
        )) {
            // Act
            LoginResponse response = authService.googleLogin(request);

            // Assert
            assertEquals("app-jwt-token", response.token());

            // Verify DB interactions
            verify(userAuthRepository).save(existingAuth); // Last login updated
            verify(userRepository, never()).save(any()); // No new user created
        }
    }

    @Test
    @DisplayName("Google Case 2: Success - New User Registration")
    void googleLogin_WhenNewUser_ShouldCreateAccount() throws Exception {
        // Arrange
        String googleTokenStr = "new-user-token";
        String email = "new@google.com";
        String googleId = "gid-99999";

        GoogleLoginRequeset request = new GoogleLoginRequeset();
        request.setTokenId(googleTokenStr);

        // 1. Prepare Mock Google Data
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setSubject(googleId);
        payload.set("given_name", "Google");
        payload.set("family_name", "User");

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        when(mockIdToken.getPayload()).thenReturn(payload);

        // 2. Prepare DB Data (User NOT found)
        when(userAuthRepository.findByEmail(email)).thenReturn(Optional.empty());

        // 3. Prepare Dependencies for Registration logic
        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setIndividualTokenLimit(1000); // Arbitrary limit
        when(subscriptionTierRepository.findByNameIgnoreCase("free")).thenReturn(Optional.of(freeTier));
        when(schoolEmailRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty()); // No school

        // Mock Saves to return the object passed
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userAuthRepository.save(any(UserAuth.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(eq(email), anyString())).thenReturn("new-jwt-token");

        // 4. INTERCEPT THE "NEW" CALL
        try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder = mockConstruction(
                GoogleIdTokenVerifier.Builder.class,
                (mock, context) -> {
                    GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
                    when(mockVerifier.verify(googleTokenStr)).thenReturn(mockIdToken);

                    when(mock.setAudience(any())).thenReturn(mock);
                    when(mock.build()).thenReturn(mockVerifier);
                }
        )) {
            // Act
            LoginResponse response = authService.googleLogin(request);

            // Assert
            assertEquals("new-jwt-token", response.token());

            // Check User Creation
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User createdUser = userCaptor.getValue();

            assertEquals(email, createdUser.getEmail());
            assertEquals("Google", createdUser.getFirstName());
            assertTrue(createdUser.getIsVerified()); // Google users should be verified

            // Check Auth Creation
            ArgumentCaptor<UserAuth> authCaptor = ArgumentCaptor.forClass(UserAuth.class);
            verify(userAuthRepository).save(authCaptor.capture());
            assertEquals(googleId, authCaptor.getValue().getGoogleUserId());
        }
    }

    @Test
    @DisplayName("Google Case 3: Fail - Invalid Google Token")
    void googleLogin_WhenTokenInvalid_ShouldThrowException() throws Exception {
        // Arrange
        String badToken = "invalid-token";
        GoogleLoginRequeset request = new GoogleLoginRequeset();
        request.setTokenId(badToken);

        // INTERCEPT THE "NEW" CALL
        try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder = mockConstruction(
                GoogleIdTokenVerifier.Builder.class,
                (mock, context) -> {
                    GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
                    // Google returns NULL for invalid tokens
                    when(mockVerifier.verify(badToken)).thenReturn(null);

                    when(mock.setAudience(any())).thenReturn(mock);
                    when(mock.build()).thenReturn(mockVerifier);
                }
        )) {
            // Act & Assert
            assertThrows(SEP490.EduPrompt.exception.auth.InvalidGoogleTokenException.class,
                    () -> authService.googleLogin(request));

            // Verify we stopped early
            verify(userRepository, never()).save(any());
        }
    }
}