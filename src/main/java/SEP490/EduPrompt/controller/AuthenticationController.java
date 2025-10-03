package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.AuthenticationResponse;
import SEP490.EduPrompt.dto.request.LoginRequest;
import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.SubscriptionRepository;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final static String ROLE_TEACHER = "teacher";
    private final static String ROLE_sADMIN = "school_admin";
    private final static String ROLE_ADMIN = "system_admin";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public ResponseDto<?> login(@Valid @RequestBody LoginRequest loginRequest) {
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

            return ResponseDto.success(response);

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", loginRequest.getEmail());
            return ResponseDto.error("401", "Invalid email or password" + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseDto<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            // Check if user already exists
            if (userAuthRepository.existsByEmail(registerRequest.getEmail())) {
                return ResponseDto.error("400", "User with this email already exists");
            }
            Instant now = Instant.now();

            // Create User entity
            //TODO Change into builder - Everything
            User user = User.builder()
                    .firstName(registerRequest.getFirstName())
                    .lastName(registerRequest.getLastName())
                    .phoneNumber(registerRequest.getPhoneNumber())
                    .email(registerRequest.getEmail())
                    .role(ROLE_TEACHER)
                    .isActive(true)
                    .isVerified(false)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            User savedUser = userRepository.save(user);

            // Create UserAuth entity
            UserAuth userAuth = UserAuth.builder()
                    .user(savedUser)
                    .email(registerRequest.getEmail())
                    .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

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

            return ResponseDto.success(response);

        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseDto.error("500", "Registration failed: " + e.getMessage());
        }
    }
}
