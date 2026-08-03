package com.Netfilx.User.Service;

import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads user credentials and security state by email address for Spring Security authentication filter chain.
     *
     * @param email The user's email address submitted during authentication.
     * @return Fully populated Spring Security UserDetails instance.
     * @throws UsernameNotFoundException If the email is null, blank, or does not exist in the database.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Defensive Input Validation
        if (email == null || email.isBlank()) {
            log.warn("Authentication failed: Null or blank email string provided.");
            throw new UsernameNotFoundException("Email address must not be empty.");
        }

        String sanitizedEmail = email.trim().toLowerCase();
        log.debug("Attempting to load UserDetails for email: {}", sanitizedEmail);

        // 2. Database Lookup
        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: No user account found for email: {}", sanitizedEmail);
                    return new UsernameNotFoundException("User not found with email: " + sanitizedEmail);
                });

        // 3. Enforce Account Status in Security Context
        boolean isAccountActive = "ACTIVE".equalsIgnoreCase(user.getStatus());

        // 4. Build Spring Security UserDetails Object
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!isAccountActive)        // Prevents login if status is INACTIVE or SUSPENDED
                .accountLocked(false)              // Can be wired to login attempt counter later
                .credentialsExpired(false)
                .accountExpired(false)
                .roles("USER")                     // Default Spring Security role
                .build();
    }
}