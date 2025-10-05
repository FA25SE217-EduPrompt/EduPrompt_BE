package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.exception.DuplicatePasswordException;
import SEP490.EduPrompt.exception.TokenInvalidException;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

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
            if (!user.getIsActive() || !user.getIsVerified()) throw new Exception("User not verified");

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
    public void resendVerificationEmail(String email) {
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

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Attempting to reset password using token: {}", request.getToken());

        String email;
        try {
            email = jwtUtil.extractUsername(request.getToken());
        } catch (Exception e) {
            throw new RuntimeException("Invalid or malformed token");
        }

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found for token"));

        if (userAuth.getVerificationToken() == null ||
                !userAuth.getVerificationToken().equals(request.getToken())) {
            throw new RuntimeException("Invalid or expired token");
        }

        if (passwordEncoder.matches(request.getNewPassword(), userAuth.getPasswordHash())) {
            log.warn("User attempted to reset password to the same as the old one: {}", email);
            throw new DuplicatePasswordException("New password cannot be the same as the old password.");
        }

        String hashedPassword = passwordEncoder.encode(request.getNewPassword());
        userAuth.setPasswordHash(hashedPassword);

        userAuth.setVerificationToken(null);
        userAuthRepository.save(userAuth);

        log.info("Password successfully reset for user: {}", email);
    }

    @Override
    @Transactional
    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new TokenInvalidException("Authorization header missing or malformed");
        }

        String token = header.substring(7);

        try {
            String email = jwtUtil.extractUsername(token);

            UserAuth userAuth = userAuthRepository.findByEmail(email)
                    .orElseThrow(() -> new TokenInvalidException("User not found"));

            Date issuedAt = jwtUtil.extractIssuedAt(token);
            if (userAuth.getLastLogin() != null &&
                    !issuedAt.toInstant().isAfter(userAuth.getLastLogin())) {
                throw new TokenInvalidException("Token already invalidated");
            }

            userAuth.setLastLogin(Instant.now());
            userAuthRepository.save(userAuth);

            log.info("User {} logged out. Tokens issued before now are invalid.", email);

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            throw new TokenInvalidException("Invalid or expired token");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse refreshToken(HttpServletRequest request) throws Exception {
        log.info("Refreshing token");

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new TokenInvalidException("Authorization header missing or malformed");
        }

        String token = header.substring(7);

        try {
            String email = jwtUtil.extractUsername(token);

            if (email == null || email.isBlank()) {
                throw new TokenInvalidException("Invalid token: unable to extract email");
            }

            UserAuth userAuth = userAuthRepository.findByEmail(email)
                    .orElseThrow(() -> new TokenInvalidException("User not found"));

            User user = userAuth.getUser();

            if (!user.getIsActive() || !user.getIsVerified()) {
                throw new Exception("User account is not active or verified");
            }

            if (!jwtUtil.validateToken(token)) {
                throw new TokenInvalidException("Invalid or expired token");
            }

            Date issuedAt = jwtUtil.extractIssuedAt(token);
            if (userAuth.getLastLogin() != null &&
                    !issuedAt.toInstant().isAfter(userAuth.getLastLogin())) {
                throw new TokenInvalidException("Token has been invalidated by logout");
            }

            String newToken = jwtUtil.generateToken(email, ROLE_TEACHER);

            log.info("Token successfully refreshed for user: {}", email);

            return LoginResponse.builder()
                    .token(newToken)
                    .build();

        } catch (TokenInvalidException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during token refresh: {}", e.getMessage());
            throw new Exception("Token refresh failed");
        }
    }
}
