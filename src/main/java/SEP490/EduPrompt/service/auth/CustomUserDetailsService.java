package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAuthRepository userAuthRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserAuth userAuth = userAuthRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        User user = userAuth.getUser();

        if (user == null) {
            throw new UsernameNotFoundException("User details not found for email: " + email);
        }

        return UserPrincipal.builder()
                .userName(userAuth.getEmail())
                .email(userAuth.getEmail())
                .userId(user.getId())
                .schoolId(user.getSchoolId())
                .role(user.getRole())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                .accountExpired(!user.getIsActive())
                .accountLocked(!user.getIsActive())
                .credentialsExpired(false)
                .disabled(!user.getIsActive())
                .build();
    }


}
