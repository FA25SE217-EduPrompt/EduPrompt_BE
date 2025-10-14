package SEP490.EduPrompt.filter;

import SEP490.EduPrompt.model.UserAuth;
import SEP490.EduPrompt.repo.UserAuthRepository;
import SEP490.EduPrompt.service.auth.CustomUserDetailsService;
import SEP490.EduPrompt.service.auth.TokenValidationService;
import SEP490.EduPrompt.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserAuthRepository userAuthRepository;
    private final CustomUserDetailsService userDetailsService;
    private final TokenValidationService tokenValidationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String path = request.getRequestURI();

        // Skip auth for public endpoints
        if (path.startsWith("/api/auth/")) {
            log.debug("Skipping JWT validation for public endpoint: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        String email = null;

        try {
            // Check blacklist (Redis check)
            if (tokenValidationService.isTokenBlacklisted(jwt)) {
                log.warn("Blacklisted token attempted access: {}", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            email = jwtUtil.extractUsername(jwt);

            // Check if all user tokens are blacklisted
            if (email != null && tokenValidationService.areAllUserTokensBlacklisted(email)) {
                log.warn("User {} has all tokens blacklisted", email);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserAuth userAuth = userAuthRepository.findByEmail(email).orElse(null);

            if (userAuth != null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // blacklist check
                if (tokenValidationService.isTokenValid(jwt, userAuth)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.warn("Invalid or blacklisted token for user: {}", email);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}