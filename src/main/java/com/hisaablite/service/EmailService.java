package com.hisaablite.service;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
                           style="display:inline-block; padding:12px 20px; margin:15px 0; font-size:16px; color:#ffffff; background-color:#3498db; text-decoration:none; border-radius:5px;">
                           Reset Password
                        </a>
                        <p>This link is valid for 15 minutes.</p>
                        <p style="color:#888;">If you did not request this, please ignore this email.</p>
                        <hr/>
                        <p style="font-size:12px; color:#aaa;">© 2026 HisaabLite. All rights reserved.</p>
                    </div>
                    """.formatted(resetLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendVerificationEmail(String to, String verifyLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Verify Your Email - HisaabLite");
            helper.setFrom("waseemk.aws@gmail.com");

            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; padding:20px;">
                        <h2 style="color:#2c3e50;">Welcome to HisaabLite</h2>
                        <p>Your shop account has been created successfully.</p>
                        <p>Please verify your email to activate your account.</p>
                        <a href="%s"
                           style="display:inline-block; padding:12px 20px; margin:15px 0; font-size:16px; color:#ffffff; background-color:#27ae60; text-decoration:none; border-radius:5px;">
                           Verify Account
                        </a>
                        <p>This verification link is valid for 24 hours.</p>
                        <hr/>
                        <p style="font-size:12px; color:#aaa;">© 2026 HisaabLite</p>
                    </div>
                    """.formatted(verifyLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

   
    public void sendSupportEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("support@hisaablite.com");

            // Convert plain text to HTML with template
            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; padding:20px; max-width: 600px;">
                        <div style="background: #f8fafc; padding: 20px; border-radius: 8px;">
                            <h2 style="color: #0f172a; margin-bottom: 16px;">HisaabLite Support</h2>
                            <div style="background: white; padding: 20px; border-radius: 8px; border: 1px solid #e2e8f0;">
                                %s
                            </div>
                            <p style="color: #64748b; font-size: 12px; margin-top: 20px;">
                                This is an automated message from HisaabLite Support. Please do not reply directly to this email.
                            </p>
                            <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                            <p style="color: #94a3b8; font-size: 11px;">
                                © 2026 HisaabLite. All rights reserved.<br>
                                For any queries, visit our <a href="https://hisaablite.com/support" style="color: #2563eb;">Support Center</a>.
                            </p>
                        </div>
                    </div>
                    """.formatted(content.replace("\n", "<br>"));

            helper.setText(htmlContent, true);
            mailSender.send(message);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("support@hisaablite.com"); //will change ltr to avoid unnessory mail
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}