package com.expygen.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expygen.entity.EmailVerificationToken;
import com.expygen.entity.User;

public interface EmailVerificationTokenRepository 
        extends JpaRepository<EmailVerificationToken, Long> {

    EmailVerificationToken findByToken(String token);

    EmailVerificationToken findByUser(User user);

}
