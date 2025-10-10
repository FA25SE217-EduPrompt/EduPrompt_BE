package SEP490.EduPrompt.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:";

    /**
     * Add token to blacklist with TTL based on token expiration
     */
    public void blacklistToken(String token, Date expiresAt) {
        String key = BLACKLIST_PREFIX + token;
        long ttl = expiresAt.getTime() - System.currentTimeMillis();

        if (ttl > 0) {
            redisTemplate.opsForValue().set(key, "revoked", ttl, TimeUnit.MILLISECONDS);
            log.info("Token blacklisted with TTL: {} ms", ttl);
        } else {
            log.warn("Token already expired, not adding to blacklist");
        }
    }

    /**
     * Check if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Remove token from blacklist (usually not needed due to TTL)
     */
    public void removeFromBlacklist(String token) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.delete(key);
        log.info("Token removed from blacklist");
    }

    /**
     * Blacklist all tokens for a user (logout from all devices)
     */
    public void blacklistAllUserTokens(String email, Duration duration) {
        String key = BLACKLIST_PREFIX + "user:" + email;
        redisTemplate.opsForValue().set(key, "all_revoked", duration);
        log.info("All tokens blacklisted for user: {}", email);
    }

    /**
     * Check if all user tokens are blacklisted
     */
    public boolean areAllUserTokensBlacklisted(String email) {
        String key = BLACKLIST_PREFIX + "user:" + email;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
