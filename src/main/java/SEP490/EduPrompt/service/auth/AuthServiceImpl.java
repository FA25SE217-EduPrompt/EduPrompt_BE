package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.dto.request.*;
import SEP490.EduPrompt.dto.response.LoginResponse;
import SEP490.EduPrompt.dto.response.PersonalInfoResponse;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.exception.BaseException;
import SEP490.EduPrompt.exception.auth.*;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

//    private final static String ROLE_TEACHER = "TEACHER";
//    private final static String ROLE_SADMIN = "SCHOOL_ADMIN";
//    private final static String ROLE_ADMIN = "SYSTEM_ADMIN";

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final SchoolEmailRepository schoolEmailRepository;
    private final SubscriptionTierRepository subscriptionTierRepository;
    private final SchoolSubscriptionRepository schoolSubscriptionRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;
    private final TokenValidationService tokenValidationService;

    @Value("${google.client-id}")
    private String googleClientId;

    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        UserAuth userAuth = userAuthRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(loginRequest.getPassword(), userAuth.getPasswordHash())) {
            throw new AuthFailedException("Invalid password");
        }

        User user = userAuth.getUser();
        if (!user.getIsActive() || !user.getIsVerified()) {
            throw new UserNotVerifiedException();
        }

        String token = jwtUtil.generateToken(loginRequest.getEmail(), user.getRole());

        // Update last login timestamp
        userAuth.setLastLogin(Instant.now());
        userAuth.setUpdatedAt(Instant.now());
        userAuthRepository.save(userAuth);

        log.info("Login successful for user: {}", userAuth.getEmail());

        return LoginResponse.builder()
                .token(token)
                .build();
    }

    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userAuthRepository.existsByEmail(registerRequest.getEmail().toLowerCase())) {
            throw new EmailAlreadyExistedException();
        }

        Instant now = Instant.now();

        User user = User.builder()
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phoneNumber(registerRequest.getPhoneNumber())
                .email(registerRequest.getEmail().toLowerCase())
                .role(Role.TEACHER.name())
                .isActive(false)
                .isVerified(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        User savedUser = userRepository.save(user);

        // Generate verification token (longer expiration for email verification)
        String token = jwtUtil.generateTokenWithExpiration(registerRequest.getEmail(), 1440); // 24 hours

        UserAuth userAuth = UserAuth.builder()
                .user(savedUser)
                .email(registerRequest.getEmail().toLowerCase())
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
            log.error("Failed to send verification email: {}", e.getMessage());
            return RegisterResponse.builder()
                    .message("Registration successful but email sending failed. Please contact support.")
                    .build();
        }

        return RegisterResponse.builder()
                .message("Check your email to verify your account, if not found, please check your spam folder!")
                .build();
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token");

        String email;
        try {
            // Allow expired tokens for verification
            email = jwtUtil.extractUsernameAllowExpired(token);
        } catch (Exception e) {
            throw new InvalidInputException("Invalid verification token");
        }

        if (email == null || email.isBlank()) {
            throw new InvalidInputException("Invalid verification token");
        }

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found for email: " + email));

        if (!token.equals(userAuth.getVerificationToken())) {
            throw new InvalidInputException("Invalid verification token");
        }

        User user = userAuth.getUser();
        if (user.getIsVerified()) {
            log.info("User {} is already verified", email);
            return;
        }

        user.setIsVerified(true);
        user.setIsActive(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // clear verification token after use (just clear it, don't ask ('-') )
        userAuth.setVerificationToken(null);
        userAuthRepository.save(userAuth);

        //===================================================================//
        //================CHECK IF USER HAVE EMAIL IN A SCHOOL===============//

        // Set quota based on school email check
        Optional<SchoolEmail> schoolEmailOpt = schoolEmailRepository.findByEmailIgnoreCase(email);
        UserQuota userQuota = UserQuota.builder()
                .user(user)
                .quotaResetDate(Instant.now().plusSeconds(2592000)) // Default to 30 days, adjust as needed
                .build();
        if (schoolEmailOpt.isPresent()) {
            School school = schoolEmailOpt.get().getSchool();
            user.setSchoolId(school.getId());
            userRepository.save(user); // Save updated schoolId

            Optional<SchoolSubscription> schoolSubOpt = schoolSubscriptionRepository.findActiveBySchoolId(school.getId());
            if (schoolSubOpt.isPresent()) {
                SchoolSubscription schoolSub = schoolSubOpt.get();
                userQuota.setSchoolSubscription(schoolSub);
                // For school subscriptions, individual limits are typically 0 as they use the shared pool
                userQuota.setIndividualTokenLimit(0);
                userQuota.setIndividualTokenRemaining(0);
                userQuota.setTestingQuotaLimit(0);
                userQuota.setTestingQuotaRemaining(0);
                userQuota.setOptimizationQuotaLimit(0);
                userQuota.setOptimizationQuotaRemaining(0);
                userQuota.setPromptUnlockLimit(100); // equivalent to pro tier
                userQuota.setPromptUnlockRemaining(100);
                userQuota.setPromptActionLimit(100);
                userQuota.setPromptActionRemaining(2000);
                userQuota.setCollectionActionLimit(200);
                userQuota.setCollectionActionRemaining(200);
                userQuota.setUpdatedAt(Instant.now());
            } else {
                // Fallback to free if no active school subscription
                setFreeTierQuota(userQuota);
            }
            // set free tier if user not in a school
        } else {
            setFreeTierQuota(userQuota);
        }

        userQuotaRepository.save(userQuota);

        //========================END OFF NEW CHECK==========================//
        //===================================================================//

        // Send welcome email
        try {
            emailService.sendWelcomeEmail(user.getEmail(), user.getLastName());
        } catch (Exception e) {
            log.error("Failed to send welcome email: {}", e.getMessage());
        }

        log.info("User {} successfully verified", email);
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        log.info("Resending verification email to: {}", email);

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found with email: " + email));

        if (userAuth.getUser().getIsVerified()) {
            throw new UserVerifiedException("User is already verified");
        }

        String newToken = jwtUtil.generateTokenWithExpiration(email, 1440); // 24 hours

        userAuth.setVerificationToken(newToken);
        userAuthRepository.save(userAuth);

        emailService.sendVerificationEmail(
                email,
                userAuth.getUser().getLastName(),
                newToken);

        log.info("Verification email resent to {}", email);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        log.info("Changing password for user: {}", request.getEmail());

        UserAuth userAuth = userAuthRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("User not found with email: " + request.getEmail()));

        if (!passwordEncoder.matches(request.getOldPassword(), userAuth.getPasswordHash())) {
            throw new InvalidInputException("Old password is incorrect");
        }

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

        int expirationMin = 5; // 5 minutes for password reset

        UserAuth userAuth = userAuthRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("User not found with email: " + request.getEmail()));

        String token = jwtUtil.generateTokenWithExpiration(userAuth.getEmail(), expirationMin);

        userAuth.setVerificationToken(token);
        userAuthRepository.save(userAuth);

        try {
            emailService.sendPasswordResetEmail(
                    userAuth.getEmail(),
                    userAuth.getUser().getLastName(),
                    token,
                    expirationMin);

            log.info("Password reset email sent to {}", request.getEmail());
        } catch (Exception e) {
            log.error("Failed to send email to: {}, message: {}", request.getEmail(), e.getMessage(), e);
            throw new BaseException(
                    AuthExceptionCode.AUTH_FAILED.name(),
                    "Failed to send email to user " + request.getEmail(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Attempting to reset password using token");

        String email;
        try {
            // Allow expired tokens but warn user
            email = jwtUtil.extractUsernameAllowExpired(request.getToken());
            Date expiration = jwtUtil.extractExpirationAllowExpired(request.getToken());

            if (expiration.before(new Date())) {
                throw new InvalidInputException("Password reset link has expired. Please request a new one.");
            }
        } catch (InvalidInputException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidInputException("Invalid or malformed token");
        }

        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found for token"));

        if (userAuth.getVerificationToken() == null ||
                !userAuth.getVerificationToken().equals(request.getToken())) {
            throw new InvalidInputException("Invalid or expired token");
        }

        if (passwordEncoder.matches(request.getNewPassword(), userAuth.getPasswordHash())) {
            log.warn("User attempted to reset password to the same as the old one: {}", email);
            throw new DuplicatePasswordException("New password cannot be the same as the old password");
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
        String token = extractTokenFromRequest(request);

        // Validate token signature first
        if (!jwtUtil.isTokenSignatureValid(token)) {
            throw new TokenInvalidException("Invalid token signature");
        }

        // Check if already blacklisted
        if (tokenValidationService.isTokenBlacklisted(token)) {
            throw new TokenInvalidException("Token already invalidated");
        }

        String email = jwtUtil.extractUsernameAllowExpired(token);
        Date expiresAt = jwtUtil.extractExpirationAllowExpired(token);

        // Verify user exists
        userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new TokenInvalidException("User not found"));

        // Blacklist the token
        blacklistService.blacklistToken(token, expiresAt);

        log.info("User {} logged out successfully", email);
    }

    @Override
    @Transactional
    public LoginResponse refreshToken(HttpServletRequest request) {
        log.info("Refreshing token");

        String token = extractTokenFromRequest(request);

        // Extract claims (allow expired tokens for refresh for a period)
        String email;
        Date expiresAt;
        try {
            email = jwtUtil.extractUsernameAllowExpired(token);
            expiresAt = jwtUtil.extractExpirationAllowExpired(token);
        } catch (Exception e) {
            throw new TokenInvalidException("Invalid token: unable to extract claims");
        }

        if (email == null || email.isBlank()) {
            throw new TokenInvalidException("Invalid token: unable to extract email");
        }

        // Check if token is blacklisted
        if (blacklistService.isTokenBlacklisted(token)) {
            throw new TokenInvalidException("Token has been revoked");
        }

        // Check if all user tokens are blacklisted
        if (blacklistService.areAllUserTokensBlacklisted(email)) {
            throw new TokenInvalidException("All user sessions have been terminated");
        }

        // ensure token hasn't been used for refresh already
        long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
        // Add minimum 60 seconds time-to-live even for expired tokens
        long guardTtl = Math.max(ttlMillis, 60000);

        if (!blacklistService.markTokenUsedForRefresh(token, guardTtl)) {
            throw new TokenInvalidException("Token has already been used for refresh");
        }

        // Verify user
        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new TokenInvalidException("User not found"));

        User user = userAuth.getUser();
        if (!user.getIsActive() || !user.getIsVerified()) {
            throw new UserNotVerifiedException();
        }

        // Generate new token
        String newToken = jwtUtil.generateToken(email, user.getRole());

        log.info("Token successfully refreshed for user: {}", email);

        return LoginResponse.builder()
                .token(newToken)
                .build();
    }

    @Override
    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequeset request) throws GeneralSecurityException, IOException {
        log.info("Attempting Google login");

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

        Optional<UserAuth> existingAuth = userAuthRepository.findByGoogleUserId(googleId);

        User user;
        if (existingAuth.isPresent()) {
            user = existingAuth.get().getUser();

            // Update last login
            UserAuth auth = existingAuth.get();
            auth.setLastLogin(Instant.now());
            userAuthRepository.save(auth);
        } else {
            user = User.builder()
                    .email(email)
                    .firstName((String) payload.get("given_name"))
                    .lastName((String) payload.get("family_name"))
                    .isActive(true)
                    .isVerified(true)
                    .role(Role.TEACHER.name())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            userRepository.save(user);

            UserAuth auth = UserAuth.builder()
                    .user(user)
                    .googleUserId(googleId)
                    .email(email)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .lastLogin(Instant.now())
                    .build();

            userAuthRepository.save(auth);

            //TODO: Set free tier for new user. New and sync quota with subscription tier
            //===================================================================//
            //================CHECK IF USER HAVE EMAIL IN A SCHOOL===============//

            // Set quota based on school email check
            Optional<SchoolEmail> schoolEmailOpt = schoolEmailRepository.findByEmailIgnoreCase(email);
            UserQuota userQuota = UserQuota.builder()
                    .user(user)
                    .quotaResetDate(Instant.now().plusSeconds(2592000)) // Default to 30 days, adjust as needed
                    .build();
            if (schoolEmailOpt.isPresent()) {
                School school = schoolEmailOpt.get().getSchool();
                user.setSchoolId(school.getId());
                userRepository.save(user); // Save updated schoolId

                Optional<SchoolSubscription> schoolSubOpt = schoolSubscriptionRepository.findActiveBySchoolId(school.getId());
                if (schoolSubOpt.isPresent()) {
                    SchoolSubscription schoolSub = schoolSubOpt.get();
                    userQuota.setSchoolSubscription(schoolSub);
                    // For school subscriptions, individual limits are typically 0 as they use the shared pool
                    userQuota.setIndividualTokenLimit(0);
                    userQuota.setIndividualTokenRemaining(0);
                    userQuota.setTestingQuotaLimit(0);
                    userQuota.setTestingQuotaRemaining(0);
                    userQuota.setOptimizationQuotaLimit(0);
                    userQuota.setOptimizationQuotaRemaining(0);
                    userQuota.setPromptUnlockLimit(100);
                    userQuota.setPromptUnlockRemaining(100);
                    userQuota.setPromptActionLimit(100);
                    userQuota.setPromptActionRemaining(2000);
                    userQuota.setCollectionActionLimit(200);
                    userQuota.setCollectionActionRemaining(200);
                    userQuota.setUpdatedAt(Instant.now());
                } else {
                    // Fallback to free if no active school subscription
                    setFreeTierQuota(userQuota);
                }
                // set free tier if user not in a school
            } else {
                setFreeTierQuota(userQuota);
            }

            userQuotaRepository.save(userQuota);

            //========================END OFF NEW CHECK==========================//
            //===================================================================//
        }
        assert user != null;
        String token = jwtUtil.generateToken(email, user.getRole());

        return LoginResponse.builder()
                .token(token)
                .build();
    }

    @Override
    public PersonalInfoResponse getPersonalInfo(HttpServletRequest request) {
        log.info("Attempting to get personal info");

        String token = extractTokenFromRequest(request);

        String email = jwtUtil.extractUsername(token);

        // Verify user exists
        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new TokenInvalidException("User not found"));

        User user = userAuth.getUser();
        Role userRole = Role.parseRole(user.getRole());

        log.info("Return personal info for user : {}", user.getEmail());
        return PersonalInfoResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .isActive(user.getIsActive())
                .isVerified(user.getIsVerified())
                .isTeacher(userRole.equals(Role.TEACHER))
                .isSchoolAdmin(userRole.equals(Role.SCHOOL_ADMIN))
                .isSystemAdmin(userRole.equals(Role.SYSTEM_ADMIN))
                .build();
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new TokenInvalidException("Authorization header missing or malformed");
        }
        return header.substring(7);
    }

    /**
     * remember to save user quota after using this method
     *
     * @param userQuota
     */
    private void setFreeTierQuota(UserQuota userQuota) {
        Optional<SubscriptionTier> freeTier = subscriptionTierRepository.findByNameIgnoreCase("free");
        SubscriptionTier subscriptionTier;
        if (freeTier.isPresent()) {
            subscriptionTier = freeTier.get();
            userQuota.setSubscriptionTier(subscriptionTier);
            userQuota.setIndividualTokenLimit(subscriptionTier.getIndividualTokenLimit());
            userQuota.setIndividualTokenRemaining(subscriptionTier.getIndividualTokenLimit());
            userQuota.setTestingQuotaLimit(subscriptionTier.getTestingQuotaLimit());
            userQuota.setTestingQuotaRemaining(subscriptionTier.getTestingQuotaLimit());
            userQuota.setOptimizationQuotaLimit(subscriptionTier.getOptimizationQuotaLimit());
            userQuota.setOptimizationQuotaRemaining(subscriptionTier.getOptimizationQuotaLimit());
            userQuota.setPromptUnlockLimit(subscriptionTier.getPromptUnlockLimit());
            userQuota.setPromptUnlockRemaining(subscriptionTier.getPromptUnlockLimit());
            userQuota.setPromptActionLimit(subscriptionTier.getPromptActionLimit());
            userQuota.setPromptActionRemaining(subscriptionTier.getPromptActionLimit());
            userQuota.setCollectionActionLimit(subscriptionTier.getCollectionActionLimit());
            userQuota.setCollectionActionRemaining(subscriptionTier.getCollectionActionLimit());
            userQuota.setUpdatedAt(Instant.now());
        } else throw new ResourceNotFoundException("No free tier found");
    }
}
