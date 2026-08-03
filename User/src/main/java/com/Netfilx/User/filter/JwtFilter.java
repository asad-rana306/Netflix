package com.Netfilx.User.filter;

import com.Netfilx.User.Utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Bypasses JWT filter execution for CORS preflight OPTIONS requests, public auth endpoints, and OpenAPI docs.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        return pathMatcher.match("/public/**", path)
                || pathMatcher.match("/api/v1/auth/**", path)
                || pathMatcher.match("/swagger-ui/**", path)
                || pathMatcher.match("/v3/api-docs/**", path)
                || pathMatcher.match("/actuator/health", path);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        String email = null;
        String jwt = null;

        // 1. Extract Bearer Token safely
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7).trim();

            try {
                email = jwtUtil.extractEmail(jwt);
            } catch (ExpiredJwtException e) {
                log.warn("JWT validation failed for URI '{}': Token has expired.", request.getRequestURI());
            } catch (SignatureException e) {
                log.warn("JWT validation failed for URI '{}': Invalid HMAC signature.", request.getRequestURI());
            } catch (MalformedJwtException e) {
                log.warn("JWT validation failed for URI '{}': Malformed token structure.", request.getRequestURI());
            } catch (UnsupportedJwtException e) {
                log.warn("JWT validation failed for URI '{}': Unsupported JWT token format.", request.getRequestURI());
            } catch (IllegalArgumentException e) {
                log.warn("JWT validation failed for URI '{}': Claims string is empty or null.", request.getRequestURI());
            } catch (Exception e) {
                log.error("Unexpected failure parsing JWT token for URI '{}': {}", request.getRequestURI(), e.getMessage());
            }
        }

        // 2. Populate SecurityContext if token is valid and context is unauthenticated
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtUtil.validateToken(jwt, userDetails.getUsername())) {
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                    log.debug("Successfully authenticated user: {} for request URI: {}", email, request.getRequestURI());
                }
            } catch (Exception ex) {
                log.warn("Failed to set user authentication in SecurityContext for email '{}': {}", email, ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        // 3. Proceed along the filter chain
        chain.doFilter(request, response);
    }
}