package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.LoginRequest;
import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.exception.auth.AuthFailedException;
import SEP490.EduPrompt.exception.auth.EmailAlreadyExistedException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.auth.UserNotVerifiedException;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;
    private UserAuth sampleUserAuth;
    private LoginRequest validLoginRequest;
    private RegisterRequest validRegisterRequest;

    @BeforeEach
    void setUp() {
        // sample user
        sampleUser = User.builder()
                .id(java.util.UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("test@email.com")
                .role("teacher")
                .isActive(true)
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // sample userAuth
        sampleUserAuth = UserAuth.builder()
                .id(java.util.UUID.randomUUID())
                .user(sampleUser)
                .email("test@email.com")
                .passwordHash("$2a$10$l0rd2%tr1@nguy3n...") // just sample for test
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // sample valid requests
        validLoginRequest = new LoginRequest("test@email.com", "validPassword");
        validRegisterRequest = new RegisterRequest(
                "new@email.com", "Jane", "Doe", "validPassword", "1234567890"
        );
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

        assertEquals("Invalid email or password", exception.getMessage()); // Assuming default msg from exception class
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
}
