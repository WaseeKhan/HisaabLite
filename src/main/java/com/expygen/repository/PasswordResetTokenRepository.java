package com.expygen.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.expygen.entity.PasswordResetToken;
import com.expygen.entity.User;

import jakarta.transaction.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);
    
    @Transactional
    void deleteByUser(User user);
}