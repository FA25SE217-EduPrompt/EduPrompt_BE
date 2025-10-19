package SEP490.EduPrompt.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return (String) claims.get("role");
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsernameAllowExpired(String token) {
        try {
            return extractUsername(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims().getSubject();
        }
    }

    public Date extractExpirationAllowExpired(String token) {
        try {
            return extractExpiration(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims().getExpiration();
        }
    }

    public String extractJtiAllowExpired(String token) {
        try {
            return extractJti(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims().getId();
        } catch (Exception ex) {
            // Token might not have JTI (old token), return null
            log.debug("Token does not contain JTI claim");
            return null;
        }
    }

    public Date extractIssuedAtAllowExpired(String token) {
        try {
            return extractIssuedAt(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims().getIssuedAt();
        }
    }

    //  Token Generation

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.toUpperCase());
        return createToken(claims, username);
    }

    public String generateTokenWithExpiration(String email, int expirationMinutes) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .subject(email)
                .id(jti)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    private String createToken(Map<String, Object> claims, String subject) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date(System.currentTimeMillis());
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .id(jti) // Add JTI to every token
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // ========== Token Validation (Signature & Expiration ONLY) ==========

    /**
     * Validate token signature and expiration.
     * Does NOT check blacklist - that should be done separately.
     */
    public boolean isTokenSignatureValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("Token signature validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    // ========== Utility Methods ==========

    public long getExpirationMillis() {
        return expiration;
    }

//    public Boolean validateToken(String token, UserDetails userDetails) {
//        final String username = extractUsername(token);
//        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
//    }
//
//    public Boolean validateToken(String token) {
//        try {
//            return !isTokenExpired(token);
//        } catch (Exception e) {
//            log.error("JWT validation failed: {}", e.getMessage());
//            return false;
//        }
//    }
//
//    public String refreshExpiredToken(String oldToken, int newExpirationMinutes) {
//        try {
//            Claims claims = Jwts.parser()
//                    .verifyWith(getSigningKey())
//                    .build()
//                    .parseSignedClaims(oldToken)
//                    .getPayload();
//
//            String email = claims.getSubject();
//            return generateTokenWithExpiration(email, newExpirationMinutes);
//
//        } catch (ExpiredJwtException e) {
//            String email = e.getClaims().getSubject();
//            return generateTokenWithExpiration(email, newExpirationMinutes);
//
//        } catch (Exception ex) {
//            throw new TokenInvalidException("Invalid token: " + ex.getMessage());
//        }
//    }
}