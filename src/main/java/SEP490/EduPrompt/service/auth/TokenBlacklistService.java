package SEP490.EduPrompt.service.auth;

import SEP490.EduPrompt.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
@Slf4j
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blk:";
    private static final String USED_SUFFIX = ":used";
    private static final String USER_BLACKLIST_PREFIX = "jwt:blk:user:";

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtUtil jwtUtil;

    public TokenBlacklistService(RedisTemplate<String, String> redisTemplate, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Generate Redis key for token using JTI (preferred) or SHA-256 hash (fallback)
     */
    private String keyForToken(String token) {
        try {
            String jti = jwtUtil.extractJtiAllowExpired(token);
            if (jti != null && !jti.isBlank()) {
                return BLACKLIST_PREFIX + "jti:" + jti;
            }
        } catch (Exception e) {
            log.debug("Failed to extract JTI from token: {}", e.getMessage());
        }

        // Fallback to SHA-256 hash for tokens without JTI (legacy tokens)
        log.debug("Token missing JTI, using SHA-256 hash as key");
        return BLACKLIST_PREFIX + "hash:" + DigestUtils.sha256Hex(token);
    }

    /**
     * Add token to blacklist with TTL based on token expiration
     */
    public void blacklistToken(String token, Date expiresAt) {
        long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();

        if (ttlMillis <= 0) {
            log.debug("Token already expired, skipping blacklist");
            return;
        }

        String key = keyForToken(token);
        redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(ttlMillis));
        log.info("Token blacklisted with TTL: {} ms", ttlMillis);
    }

    /**
     * Check if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String key = keyForToken(token);
            Boolean exists = redisTemplate.hasKey(key);
            return exists;
        } catch (Exception e) {
            log.error("Error checking token blacklist: {}", e.getMessage());
            // Fail secure: if we can't check, assume blacklisted
            return true;
        }
    }

    /**
     * Remove token from blacklist (usually not needed due to TTL)
     */
    public void removeFromBlacklist(String token) {
        String key = keyForToken(token);
        redisTemplate.delete(key);
        log.info("Token removed from blacklist");
    }

    /**
     * mark token as used for refresh. Returns true if successfully marked
     * Prevents double-refresh
     */
    public boolean markTokenUsedForRefresh(String token, long ttlMillis) {
        if (ttlMillis <= 0) {
            log.debug("Token expired, cannot mark as used");
            return false;
        }

        String usedKey = keyForToken(token) + USED_SUFFIX;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(usedKey, "1", Duration.ofMillis(ttlMillis));

        boolean marked = Boolean.TRUE.equals(success);
        if (marked) {
            log.info("Token marked as used for refresh");
        } else {
            log.warn("Token already used for refresh (replay attempt detected)");
        }
        return marked;
    }

    /**
     * Blacklist all tokens for a user (logout from all devices)
     */
    public void blacklistAllUserTokens(String email, Duration duration) {
        String key = USER_BLACKLIST_PREFIX + email;
        redisTemplate.opsForValue().set(key, "all_revoked", duration);
        log.info("All tokens blacklisted for user: {}", email);
    }

    /**
     * Check if all user tokens are blacklisted
     */
    public boolean areAllUserTokensBlacklisted(String email) {
        String key = USER_BLACKLIST_PREFIX + email;
        Boolean exists = redisTemplate.hasKey(key);
        return exists;
    }
}