package com.expygen.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expygen.entity.EmailVerificationToken;

public interface EmailVerificationTokenRepository 
        extends JpaRepository<EmailVerificationToken, Long> {

    EmailVerificationToken findByToken(String token);

}