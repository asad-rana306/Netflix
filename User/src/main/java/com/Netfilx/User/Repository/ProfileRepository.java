package com.Netfilx.User.Repository;

import com.Netfilx.User.Entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    List<Profile> findByUserId(UUID userId);
    long countByUserId(UUID userId);
}