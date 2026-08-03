package com.Netfilx.User.Service;

import com.Netfilx.User.Entity.Subscription;
import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers and persists a new user with an encoded password and initial inactive subscription.
     *
     * @param user The user entity to register.
     * @return The persisted User entity.
     */
    @Transactional
    public User saveNewUser(User user) {
        // 1. Defensive Input Validation
        if (user == null) {
            throw new IllegalArgumentException("User payload must not be null.");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("User email must not be null or empty.");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("User password must not be null or empty.");
        }

        // 2. Normalize email (trim whitespace & lowercase)
        String normalizedEmail = user.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration attempt failed: User with email '{}' already exists.", normalizedEmail);
            throw new IllegalArgumentException("User with email " + normalizedEmail + " already exists.");
        }

        // 3. Set normalized email, status, and encode raw password
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setStatus("ACTIVE");

        // 4. Attach default INACTIVE subscription record
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setStripeCustomerId(null);
        subscription.setStatus("INACTIVE");
        subscription.setPlanTier(null);

        user.setSubscription(subscription);

        User savedUser = userRepository.save(user);
        log.info("Successfully registered new user ID: {} with email: {}", savedUser.getId(), normalizedEmail);

        return savedUser;
    }

    /**
     * Retrieves all registered users in the system.
     */
    @Transactional(readOnly = true)
    public List<User> getAll() {
        return userRepository.findAll();
    }

    /**
     * Finds a user by unique identifier.
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    /**
     * Finds a user by email address after normalizing input.
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    /**
     * Updates an existing user record in PostgreSQL.
     */
    @Transactional
    public void saveUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User entity must not be null.");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("Cannot update user: User ID is required.");
        }

        userRepository.save(user);
        log.info("Updated user entity for ID: {}", user.getId());
    }

    /**
     * Deletes a user by unique identifier.
     */
    @Transactional
    public void deleteById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID must not be null.");
        }

        if (!userRepository.existsById(id)) {
            log.warn("Attempted to delete non-existent user ID: {}", id);
            throw new IllegalArgumentException("User not found with ID: " + id);
        }

        userRepository.deleteById(id);
        log.info("Successfully deleted user ID: {}", id);
    }
}