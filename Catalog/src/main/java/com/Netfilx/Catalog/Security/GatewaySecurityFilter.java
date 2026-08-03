package com.Netfilx.Catalog.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GatewaySecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Allow internal health checks or Swagger docs to bypass security
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract headers injected by the API Gateway
        String userId = request.getHeader("X-User-Id");
        String userPlan = request.getHeader("X-User-Plan");

        // 3. The Concrete Wall: Block direct access
        if (userId == null || userId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized. Request must pass through API Gateway.\"}");
            return;
        }

        // 4. Populate the ThreadLocal context
        try {
            UserContextHolder.setUserId(userId);
            UserContextHolder.setUserPlan(userPlan != null ? userPlan : "BASIC");

            // 5. Allow the request to reach the CatalogController
            filterChain.doFilter(request, response);

        } finally {
            // 6. 🔥 DESTROY THE DATA 🔥
            // Guarantees Virtual Threads are wiped clean before returning to the pool.
            UserContextHolder.clear();
        }
    }
}