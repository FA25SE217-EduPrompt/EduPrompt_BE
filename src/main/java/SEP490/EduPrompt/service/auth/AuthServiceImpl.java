package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.exception.BaseException;
import SEP490.EduPrompt.exception.DuplicatePasswordException;
import SEP490.EduPrompt.exception.InvalidGoogleTokenException;
import SEP490.EduPrompt.exception.TokenInvalidException;
import SEP490.EduPrompt.exception.auth.*;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.util.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Value("${google.client-id}")
    private String googleClientId;

    private final static String ROLE_TEACHER = "teacher";
    private final static String ROLE_sADMIN = "school_admin";
    private final static String ROLE_ADMIN = "system_admin";

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {

        UserAuth userAuth = userAuthRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (userAuth==null ||
                !passwordEncoder.matches(loginRequest.getPassword(), userAuth.getPasswordHash())) {
            throw new AuthFailedException();
        }

        // check account isActive and isVerified field
        User user = userAuth.getUser();
        if (!user.getIsActive() || !user.getIsVerified()) throw new UserNotVerifiedException();

        String token = jwtUtil.generateToken(loginRequest.getEmail(), user.getRole());

        // update last login
        userAuth.setLastLogin(Instant.now());
        userAuth.setUpdatedAt(Instant.now());
        userAuthRepository.save(userAuth);
        log.info("Login successful by user : {}", userAuth.getEmail());

        return LoginResponse.builder()
                .token(token)
                .build();
    }

    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {

        if (userAuthRepository.existsByEmail(registerRequest.getEmail())) {
            throw new EmailAlreadyExistedException();
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


    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token: {}", token);


        String email = jwtUtil.extractUsername(token);
        if (email == null || email.isBlank()) {
            throw new InvalidInputException();
        }

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email));

        if (!token.equals(userAuth.getVerificationToken())) {
            throw new InvalidInputException("Invalid verification token");
        }

        User user = userAuth.getUser();
        user.setIsVerified(true);
        user.setIsActive(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        //send welcome email
        emailService.sendWelcomeEmail(user.getEmail(), user.getLastName());

        log.info("User {} successfully verified", email);
    }

    @Override
    public void resendVerificationEmail(String email) {
        log.info("Resending verification email to: {}", email);

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

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
    public void changePassword(ChangePasswordRequest request) {
        log.info("Changing password for user: {}", request.getEmail());

        UserAuth userAuth = userAuthRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        // Check old password
        if (!passwordEncoder.matches(request.getOldPassword(), userAuth.getPasswordHash())) {
            throw new InvalidInputException("Old password is incorrect");
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        String token = jwtUtil.generateToken(userAuth.getEmail(), ROLE_TEACHER);

        userAuth.setVerificationToken(token);
        userAuthRepository.save(userAuth);

        try {
            emailService.sendResetPasswordEmail(
                    userAuth.getEmail(),
                    userAuth.getUser().getLastName(),
                    token,
                    expirationMin);

            log.info("Password reset email sent to {}", request.getEmail());

        } catch (Exception e) {
            log.error("Failed to send email to : {}, message : {}", request.getEmail(), e.getMessage(), e);
            throw new BaseException(
                    AuthExceptionCode.AUTH_FAILED.name(),
                    "Fail to send email to user " + request.getEmail(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Attempting to reset password using token: {}", request.getToken());

        String email;
        try {
            email = jwtUtil.extractUsername(request.getToken());
        } catch (Exception e) {
            throw new InvalidInputException("Invalid or malformed token");
        }

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for token"));

        if (userAuth.getVerificationToken() == null ||
                !userAuth.getVerificationToken().equals(request.getToken())) {
            throw new InvalidInputException("Invalid or expired token");
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
    }

    @Override
    @Transactional
    public LoginResponse refreshToken(HttpServletRequest request) {
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
                throw new UserNotVerifiedException();
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

            // this try/catch may be redundant, yet it's for reference to know there's still another way of handle exception :)
        } catch (TokenInvalidException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw e;
        } catch (UserNotVerifiedException e) {
            log.error("User not verified: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during token refresh: {}", e.getMessage());
            throw new BaseException(
                    AuthExceptionCode.AUTH_FAILED.name(),
                    "Token refresh failed due to an unexpected error",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Override
    public LoginResponse googleLogin(GoogleLoginRequeset request) {
        log.info("Attempting Google login");

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(List.of(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getTokenId());
            if (idToken == null) {
                throw new InvalidGoogleTokenException();
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String googleId = payload.getSubject();

            log.info("Google login - email: {}, googleId: {}", email, googleId);

            Optional<UserAuth> existingAuth = userAuthRepository
                    .findByGoogleUserId(googleId);

            User user;
            if (existingAuth.isPresent()) {
                user = existingAuth.get().getUser();
            } else {
                user = User.builder()
                        .email(email)
                        .firstName((String) payload.get("given_name"))
                        .lastName((String) payload.get("family_name"))
                        .isActive(true)
                        .isVerified(true)
                        .role(ROLE_TEACHER)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                userRepository.save(user);

                UserAuth auth = UserAuth.builder()
                        .user(user)
                        .googleUserId("google")
                        .email(email)
                        .createdAt(Instant.now())
                        .lastLogin(Instant.now())
                        .build();

                userAuthRepository.save(auth);
            }

            String token = jwtUtil.generateToken(email, ROLE_TEACHER);

            return LoginResponse.builder()
                    .token(token)
                    .build();

        } catch (Exception ex) {
            log.error("Google login failed", ex);
            throw new InvalidGoogleTokenException();
        }
    }
}
