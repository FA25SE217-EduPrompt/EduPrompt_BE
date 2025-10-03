package SEP490.EduPrompt.service;

import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService extends UserDetailsService {
    
    // Inherits loadUserByUsername from UserDetailsService
    
    // User management methods
    User saveUser(User user);
    User updateUser(User user);
    void deleteUser(UUID userId);
    Optional<User> findUserById(UUID userId);
    Optional<User> findUserByEmail(String email);
    List<User> findAllUsers();
    boolean existsByEmail(String email);
    
    // UserAuth management methods
    UserAuth saveUserAuth(UserAuth userAuth);
    UserAuth updateUserAuth(UserAuth userAuth);
    Optional<UserAuth> findUserAuthByEmail(String email);
    Optional<UserAuth> findUserAuthByUserId(UUID userId);
    boolean existsUserAuthByEmail(String email);
    
    // Authentication related methods
    boolean authenticateUser(String email, String password);
    void updateLastLogin(String email);
}
