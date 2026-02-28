package com.hisaablite.service;

import javax.swing.text.html.HTML;

import org.aspectj.weaver.ast.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.Trigger;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendResetEmail(String to, String resetLink) {

    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Reset Your Password - HisaabLite");
        helper.setFrom("waseemk.aws@gmail.com");

        String htmlContent = """
                <div style="font-family: Arial, sans-serif; padding:20px;">
                    <h2 style="color:#2c3e50;">HisaabLite Password Reset</h2>
                    
                    <p>Hello,</p>
                    
                    <p>You requested to reset your password. Click the button below:</p>
                    
                    <a href="%s"
                       style="
                       display:inline-block;
                       padding:12px 20px;
                       margin:15px 0;
                       font-size:16px;
                       color:#ffffff;
                       background-color:#3498db;
                       text-decoration:none;
                       border-radius:5px;">
                       Reset Password
                    </a>
                    
                    <p>This link is valid for 15 minutes.</p>
                    
                    <p style="color:#888;">If you did not request this, please ignore this email.</p>
                    
                    <hr/>
                    <p style="font-size:12px; color:#aaa;">
                        © 2026 HisaabLite. All rights reserved.
                    </p>
                </div>
                """.formatted(resetLink);

        helper.setText(htmlContent, true); // true = HTML

        mailSender.send(message);

    } catch (Exception e) {
        e.printStackTrace();
    }
}



}
