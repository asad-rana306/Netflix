package com.Netfilx.User.Repository;

import com.Netfilx.User.Entity.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    Optional<RefreshToken> findById(String id);

    List<RefreshToken> findByUserId(String userId);

    void deleteByUserId(String userId);
}