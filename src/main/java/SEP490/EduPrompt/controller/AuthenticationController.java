package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.AuthenticationResponse;
import SEP490.EduPrompt.dto.LoginRequest;
import SEP490.EduPrompt.dto.RegisterRequest;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.SubscriptionRepository;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    //TODO: need to make constants here for each role: private static final ROLE_TEACHER =  "teacher", ROLE_sADMIN = "school_admin",
    //TODO: ROLE_ADMIN = "system_admin"

    //TODO: use ResponseDto and ErrorMessage instead

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            UserAuth userAuth = userAuthRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            User user = userAuth.getUser();
            String token = jwtUtil.generateToken(loginRequest.getEmail(), user.getRole());

            AuthenticationResponse response = AuthenticationResponse.builder()
                    .token(token)
                    .email(user.getEmail())
                    .role(user.getRole())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .build();

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", loginRequest.getEmail());
            return ResponseEntity.badRequest().body("Invalid credentials");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        try {
            // Check if user already exists
            if (userAuthRepository.existsByEmail(registerRequest.getEmail())) {
                return ResponseEntity.badRequest().body("User with this email already exists");
            }

            // Create User entity
            User user = new User();
            user.setFirstName(registerRequest.getFirstName());
            user.setLastName(registerRequest.getLastName());
            user.setPhoneNumber(registerRequest.getPhoneNumber());
            user.setEmail(registerRequest.getEmail());
            user.setRole("TEACHER");
            user.setIsActive(true);
            user.setIsVerified(false);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());

            User savedUser = userRepository.save(user);

            // Create UserAuth entity
            UserAuth userAuth = new UserAuth();
            userAuth.setUser(savedUser);
            userAuth.setEmail(registerRequest.getEmail());
            userAuth.setPasswordHash(registerRequest.getPassword()); // Storing as plain text as requested
            userAuth.setCreatedAt(Instant.now());
            userAuth.setUpdatedAt(Instant.now());

            userAuthRepository.save(userAuth);

            // Generate token
            String token = jwtUtil.generateToken(registerRequest.getEmail(), savedUser.getRole());

            AuthenticationResponse response = AuthenticationResponse.builder()
                    .token(token)
                    .email(savedUser.getEmail())
                    .role(savedUser.getRole())
                    .firstName(savedUser.getFirstName())
                    .lastName(savedUser.getLastName())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Registration failed: " + e.getMessage());
        }
    }
}
