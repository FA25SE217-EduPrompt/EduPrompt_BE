package SEP490.EduPrompt.service;

import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        User user = userAuth.getUser();
        
        if (user == null) {
            throw new UsernameNotFoundException("User details not found for email: " + email);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(userAuth.getEmail())
                .password(userAuth.getPasswordHash()) // We'll compare this as plain text
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                .accountExpired(!user.getIsActive())
                .accountLocked(!user.getIsActive())
                .credentialsExpired(false)
                .disabled(!user.getIsActive())
                .build();
    }

    // User management methods
    @Override
    @Transactional
    public User saveUser(User user) {
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        userRepository.deleteById(userId);
        log.info("User deleted with ID: {}", userId);
    }

    @Override
    public Optional<User> findUserById(UUID userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    // UserAuth management methods
    @Override
    @Transactional
    public UserAuth saveUserAuth(UserAuth userAuth) {
        userAuth.setCreatedAt(Instant.now());
        userAuth.setUpdatedAt(Instant.now());
        return userAuthRepository.save(userAuth);
    }

    @Override
    @Transactional
    public UserAuth updateUserAuth(UserAuth userAuth) {
        userAuth.setUpdatedAt(Instant.now());
        return userAuthRepository.save(userAuth);
    }

    @Override
    public Optional<UserAuth> findUserAuthByEmail(String email) {
        return userAuthRepository.findByEmail(email);
    }

    @Override
    public Optional<UserAuth> findUserAuthByUserId(UUID userId) {
        return userAuthRepository.findByUserId(userId);
    }

    @Override
    public boolean existsUserAuthByEmail(String email) {
        return userAuthRepository.existsByEmail(email);
    }

    // Authentication related methods
    @Override
    public boolean authenticateUser(String email, String password) {
        Optional<UserAuth> userAuthOpt = userAuthRepository.findByEmail(email);
        if (userAuthOpt.isPresent()) {
            UserAuth userAuth = userAuthOpt.get();
            User user = userAuth.getUser();
            
            // Check if user is active and password matches (plain text comparison)
            return user != null && 
                   user.getIsActive() && 
                   password.equals(userAuth.getPasswordHash());
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
}
