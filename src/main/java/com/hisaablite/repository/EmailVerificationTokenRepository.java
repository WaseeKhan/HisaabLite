package com.hisaablite.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hisaablite.entity.EmailVerificationToken;

public interface EmailVerificationTokenRepository 
        extends JpaRepository<EmailVerificationToken, Long> {

    EmailVerificationToken findByToken(String token);

}