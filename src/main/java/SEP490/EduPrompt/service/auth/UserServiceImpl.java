package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.ChangePasswordRequest;
import SEP490.EduPrompt.dto.request.ForgotPasswordRequest;
import SEP490.EduPrompt.dto.request.LoginRequest;
import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService{

    private final static String ROLE_TEACHER = "teacher";
    private final static String ROLE_sADMIN = "school_admin";
    private final static String ROLE_ADMIN = "system_admin";

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Authentication related methods
    @Override
    public boolean authenticateUser(String email, String password) {
        Optional<UserAuth> userAuthOpt = userAuthRepository.findByEmail(email);
        if (userAuthOpt.isPresent()) {
            UserAuth userAuth = userAuthOpt.get();
            User user = userAuth.getUser();

            // Check if user is active and password matches (hashed comparison)
            return user != null && 
                   user.getIsActive() && 
                   passwordEncoder.matches(password, userAuth.getPasswordHash());
        }
        return false;
    }

    @Override
    @Transactional
    public void updateLastLogin(String email) {
        Optional<UserAuth> userAuthOpt = userAuthRepository.findByEmail(email);
        if (userAuthOpt.isPresent()) {
            UserAuth userAuth = userAuthOpt.get();
            userAuth.setLastLogin(Instant.now());
            userAuth.setUpdatedAt(Instant.now());
            userAuthRepository.save(userAuth);
            log.info("Last login updated for user: {}", email);
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest loginRequest) throws Exception {
        try {
            // Simple authentication check using our own authenticateUser method
            if (!authenticateUser(loginRequest.getEmail(), loginRequest.getPassword())) {
                throw new Exception("Invalid credentials");
            }
            
            // Update last login time
            updateLastLogin(loginRequest.getEmail());

            UserAuth userAuth = userAuthRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // check account isActive and isVerified field
            User user = userAuth.getUser();
            if(!user.getIsActive() || !user.getIsVerified()) throw new Exception("User not verified");

            String token = jwtUtil.generateToken(loginRequest.getEmail(), user.getRole());

            return LoginResponse.builder()
                    .token(token)
                    .build();

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", loginRequest.getEmail());
            throw new Exception("Login failed");
        }
    }

    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) throws RuntimeException {
        try {
            if (userAuthRepository.existsByEmail(registerRequest.getEmail())) {
                return RegisterResponse.builder()
                        .message("User with this email already exists")
                        .build();
            }

            Instant now = Instant.now();

            // Create User
            User user = User.builder()
                    .firstName(registerRequest.getFirstName())
                    .lastName(registerRequest.getLastName())
                    .phoneNumber(registerRequest.getPhoneNumber())
                    .email(registerRequest.getEmail())
                    .role(ROLE_TEACHER)
                    .isActive(false)
                    .isVerified(false)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            User savedUser = userRepository.save(user);
            String token = jwtUtil.generateToken(registerRequest.getEmail(), user.getRole());
            // Create UserAuth
            UserAuth userAuth = UserAuth.builder()
                    .user(savedUser)
                    .email(registerRequest.getEmail())
                    .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                    .verificationToken(token)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            userAuthRepository.save(userAuth);
            try {
                emailService.sendVerificationEmail(
                        registerRequest.getEmail(),
                        registerRequest.getLastName(),
                        token);
            } catch (Exception e) {
                return RegisterResponse.builder()
                        .message("Check your email again or email not Exist")
                        .build();
            }

            return RegisterResponse.builder()
                    .message("Check your email to verify your account")
                    .build();

        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage());
            throw new RuntimeException("Registration failed");
        }
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token: {}", token);

        try {

            String email = jwtUtil.extractUsername(token);
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Invalid or expired token");
            }

            UserAuth userAuth = userAuthRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found for email: " + email));

            if (!token.equals(userAuth.getVerificationToken())) {
                throw new IllegalArgumentException("Invalid verification token");
            }

            User user = userAuth.getUser();
            user.setIsVerified(true);
            user.setIsActive(true);
            user.setUpdatedAt(Instant.now());

            userRepository.save(user);
            log.info("User {} successfully verified", email);

        } catch (IllegalArgumentException e) {
            log.error("Email verification failed: {}", e.getMessage());
            throw e; // rethrow or wrap in custom exception
        } catch (Exception e) {
            log.error("Unexpected error during email verification", e);
            throw new RuntimeException("Email verification failed", e);
        }
    }

    @Override
    public void resendVerificationEmail(String email){
        log.info("Resending verification email to: {}", email);

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (userAuth.getUser().getIsVerified()) {
            throw new IllegalStateException("User is already verified");
        }

        String newToken = jwtUtil.generateToken(email, ROLE_TEACHER);

        userAuth.setVerificationToken(newToken);
        userAuthRepository.save(userAuth);

        emailService.sendVerificationEmail(
                email,
                userAuth.getUser().getLastName(),
                newToken);

        log.info("Verification email resent to {}", email);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) throws Exception {
        log.info("Changing password for user: {}", request.getEmail());

        UserAuth userAuth = userAuthRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check old password
        if (!passwordEncoder.matches(request.getOldPassword(), userAuth.getPasswordHash())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        // Encode and update new password
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        userAuth.setPasswordHash(encodedNewPassword);
        userAuth.setUpdatedAt(Instant.now());

        userAuthRepository.save(userAuth);

        log.info("Password changed successfully for {}", request.getEmail());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Processing forgot password for email: {}", request.getEmail());

        int expirationMin = 5;

        UserAuth userAuth = userAuthRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email not found"));

        String token = jwtUtil.generateToken(userAuth.getEmail(), ROLE_TEACHER);

        userAuth.setVerificationToken(token);
        userAuthRepository.save(userAuth);

        emailService.sendResetPasswordEmail(
                userAuth.getEmail(),
                userAuth.getUser().getLastName(),
                token,
                expirationMin);

        log.info("Password reset email sent to {}", request.getEmail());
    }
}
