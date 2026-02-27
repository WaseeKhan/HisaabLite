package com.hisaablite.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hisaablite.entity.PasswordResetToken;
import com.hisaablite.entity.User;

import jakarta.transaction.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);
    
    @Transactional
    void deleteByUser(User user);
}