package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


//i should call this service a helper yet i don't want to ?
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidationService {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    /**
     * Complete token validation including signature, expiration, and blacklist checks
     * 4 things to check :3
     */
    public boolean isTokenValid(String token, UserAuth userAuth) {
        try {
            // 1. Check signature and expiration
            if (!jwtUtil.isTokenSignatureValid(token)) {
                log.debug("Token signature invalid");
                return false;
            }

            // 2. Check if token is blacklisted
            if (blacklistService.isTokenBlacklisted(token)) {
                log.debug("Token is blacklisted");
                return false;
            }

            // 3. Check if all user tokens are blacklisted
            String email = jwtUtil.extractUsername(token);
            if (blacklistService.areAllUserTokensBlacklisted(email)) {
                log.debug("All user tokens are blacklisted");
                return false;
            }

            // 4. Verify email matches
            if (!email.equals(userAuth.getEmail())) {
                log.debug("Token email doesn't match user");
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Quick check if token is blacklisted (without full validation)
     */
    public boolean isTokenBlacklisted(String token) {
        return blacklistService.isTokenBlacklisted(token);
    }

    /**
     * Check if all user tokens are blacklisted
     */
    public boolean areAllUserTokensBlacklisted(String email) {
        return blacklistService.areAllUserTokensBlacklisted(email);
    }
}