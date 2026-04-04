package com.hisaablite.service;

import com.hisaablite.entity.User;
import com.hisaablite.config.AppConfig;
import com.hisaablite.entity.SubscriptionPlan;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final AppConfig appConfig;
    private final UrlService urlService;

    /**
     * Send password reset email
     */
    public void sendResetEmail(String to, String resetLink) {
        try {
            String htmlContent = String.format(
                    """
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
                            """,
                    resetLink);

            sendHtmlEmail(to, "Reset Your Password - HisaabLite", htmlContent);
            log.info("Password reset email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Send verification email with fallback
     */
    public void sendVerificationEmail(User user, String verificationLink, SubscriptionPlan plan) {
        try {
            String to = user.getUsername();
            String subject = "Verify Your Email - HisaabLite";

            String htmlContent;
            try {
                Context context = new Context();
                context.setVariable("name", user.getName());
                context.setVariable("planName", plan.getPlanName());
                context.setVariable("shopName", user.getShop().getName());
                context.setVariable("verificationLink", verificationLink);
                context.setVariable("supportEmail", urlService.getSupportEmail());
                htmlContent = templateEngine.process("email/verification-email", context);
                log.debug("Using email template for verification email");
            } catch (Exception e) {
                log.warn("Verification email template not found, using fallback HTML");
                htmlContent = generateFallbackVerificationEmail(user, verificationLink, plan);
            }

            sendHtmlEmail(to, subject, htmlContent);
            log.info("Verification email sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getUsername(), e.getMessage());
        }
    }

    /**
     * Send welcome email with fallback
     */
    public void sendWelcomeEmail(User user, SubscriptionPlan plan) {
        try {
            String to = user.getUsername();
            String subject = "🎉 Welcome to HisaabLite " + plan.getPlanName() + " Plan!";

            String htmlContent;
            try {
                Context context = new Context();
                String dashboardUrl = urlService.getDashboardUrl();
                context.setVariable("name", user.getName());
                context.setVariable("planName", plan.getPlanName());
                context.setVariable("email", user.getUsername());
                context.setVariable("maxUsers", plan.getMaxUsers() == -1 ? "Unlimited" : plan.getMaxUsers());
                context.setVariable("maxProducts", plan.getMaxProducts() == -1 ? "Unlimited" : plan.getMaxProducts());
                context.setVariable("expiryDate",
                        user.getSubscriptionEndDate() != null ? user.getSubscriptionEndDate().toLocalDate().toString()
                                : null);
                context.setVariable("dashboardUrl", dashboardUrl);
                htmlContent = templateEngine.process("email/welcome-email", context);
                log.debug("Using email template for welcome email");
            } catch (Exception e) {
                log.warn("Welcome email template not found, using fallback HTML");
                htmlContent = generateFallbackWelcomeEmail(user, plan);
            }

            sendHtmlEmail(to, subject, htmlContent);
            log.info("Welcome email sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send welcome email: {}", e.getMessage());
        }
    }

    /**
     * Send approval email with fallback
     */
    public void sendApprovalEmail(User user, SubscriptionPlan plan) {
        try {
            String to = user.getUsername();
            String subject = "✅ Your HisaabLite Account Has Been Approved!";

            String htmlContent;
            try {
                Context context = new Context();
                context.setVariable("name", user.getName());
                context.setVariable("planName", plan.getPlanName());
                context.setVariable("price", plan.getPrice());
                context.setVariable("durationDays", plan.getDurationInDays() != null ? plan.getDurationInDays() : 0);
                context.setVariable("maxUsers", plan.getMaxUsers() == -1 ? "Unlimited" : plan.getMaxUsers());
                context.setVariable("maxProducts", plan.getMaxProducts() == -1 ? "Unlimited" : plan.getMaxProducts());
                context.setVariable("features",
                        plan.getDescription() != null ? plan.getDescription() : "No description available");
                context.setVariable("expiryDate",
                        user.getSubscriptionEndDate() != null ? user.getSubscriptionEndDate().toLocalDate().toString()
                                : "No expiry");
                context.setVariable("email", user.getUsername());
                context.setVariable("dashboardUrl", urlService.getDashboardUrl());

                htmlContent = templateEngine.process("email/approval-email", context);

                log.debug("Using email template for approval email");
            } catch (Exception e) {
                log.warn("Approval email template not found, using fallback HTML");
                htmlContent = generateFallbackApprovalEmail(user, plan);
            }

            sendHtmlEmail(to, subject, htmlContent);
            log.info("Approval email sent successfully to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send approval email to {}: {}", user.getUsername(), e.getMessage());
        }
    }

    /**
     * Send expiry reminder email with fallback
     */
    public void sendExpiryReminderEmail(User user, long daysLeft, SubscriptionPlan plan) {
        try {
            String to = user.getUsername();
            String subject = "⚠️ Your Subscription Expires in " + daysLeft + " Days";

            String htmlContent;
            try {
                Context context = new Context();
                context.setVariable("name", user.getName());
                context.setVariable("daysLeft", daysLeft);
                context.setVariable("planName", plan.getPlanName());
                context.setVariable("price", plan.getPrice());
                context.setVariable("expiryDate",
                        user.getSubscriptionEndDate() != null ? user.getSubscriptionEndDate().toLocalDate().toString()
                                : "");
                context.setVariable("renewUrl", urlService.getRenewUrl());
                context.setVariable("upgradeUrl", urlService.getUpgradeUrl());
                context.setVariable("supportEmail", urlService.getSupportEmail());
                htmlContent = templateEngine.process("email/expiry-reminder", context);
                log.debug("Using email template for expiry reminder");
            } catch (Exception e) {
                log.warn("Expiry reminder template not found, using fallback HTML");
                htmlContent = generateFallbackExpiryReminderEmail(user, daysLeft);
            }

            sendHtmlEmail(to, subject, htmlContent);
            log.info("Expiry reminder sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send expiry reminder: {}", e.getMessage());
        }
    }

    /**
     * Send subscription expired email with fallback
     */
    public void sendSubscriptionExpiredEmail(User user, SubscriptionPlan oldPlan) {
        try {
            String to = user.getUsername();
            String subject = "❌ Your HisaabLite Subscription Has Expired";

            String htmlContent;
            try {
                Context context = new Context();
                context.setVariable("name", user.getName());
                context.setVariable("oldPlan", oldPlan.getPlanName());
                context.setVariable("oldMaxUsers", oldPlan.getMaxUsers() == -1 ? "Unlimited" : oldPlan.getMaxUsers());
                context.setVariable("oldMaxProducts",
                        oldPlan.getMaxProducts() == -1 ? "Unlimited" : oldPlan.getMaxProducts());
                context.setVariable("renewUrl", urlService.getRenewUrl());
                context.setVariable("dashboardUrl", urlService.getDashboardUrl());
                htmlContent = templateEngine.process("email/expired-email", context);
                log.debug("Using email template for expired email");
            } catch (Exception e) {
                log.warn("Expired email template not found, using fallback HTML");
                htmlContent = generateFallbackExpiredEmail(user);
            }

            sendHtmlEmail(to, subject, htmlContent);
            log.info("Expiration email sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send expiration email: {}", e.getMessage());
        }
    }

    /**
     * Notify admin about pending approval - USING APPCONFIG
     */
    public void notifyAdminAboutPendingApproval(User user, SubscriptionPlan plan) {
        try {

            String adminEmail = appConfig.getAdminEmail();
            String subject = "🔔 New Registration Pending Approval";

            String pendingUrl = urlService.getPendingApprovalsUrl();

            String htmlContent = String.format(
                    """
                            <div style="font-family: Arial, sans-serif; padding:20px;">
                                <h2 style="color:#2c3e50;">New User Pending Approval</h2>
                                <p>A new user has registered and completed email verification.</p>

                                <div style="background:#f8fafc; padding:15px; border-radius:8px; margin:15px 0;">
                                    <h3>User Details:</h3>
                                    <table style="width:100%%;">
                                        <tr><td><strong>Name:</strong></td><td>%s</td></tr>
                                        <tr><td><strong>Email:</strong></td><td>%s</td></tr>
                                        <tr><td><strong>Phone:</strong></td><td>%s</td></tr>
                                        <tr><td><strong>Selected Plan:</strong></td><td>%s</td></tr>
                                        <tr><td><strong>Shop Name:</strong></td><td>%s</td></tr>
                                    </table>
                                </div>

                                <a href="%s"
                                   style="display:inline-block; padding:12px 20px; background:#4361ee; color:white; text-decoration:none; border-radius:5px;">
                                    Review in Admin Panel
                                </a>
                            </div>
                            """,
                    user.getName(),
                    user.getUsername(),
                    user.getPhone(),
                    plan.getPlanName(),
                    user.getShop().getName(),
                    pendingUrl);

            sendHtmlEmail(adminEmail, subject, htmlContent);
            log.info("Admin notification sent for user: {}", user.getUsername());

        } catch (Exception e) {
            log.error("Failed to notify admin: {}", e.getMessage());
        }
    }

    /**
     * Generic method to send HTML email - USING APPCONFIG
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);

            helper.setFrom(appConfig.getFromEmail());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.debug("HTML email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send support email with proper HTML formatting - USING APPCONFIG
     */
    public void sendSupportEmail(String to, String subject, String content) {
        try {
            String htmlContent = String.format(
                    """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="UTF-8">
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            </head>
                            <body style="font-family: 'Inter', 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; margin: 0; padding: 20px;">
                                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1);">

                                    <!-- Header -->
                                    <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 24px;">🔧 HisaabLite Support</h1>
                                    </div>

                                    <!-- Content -->
                                    <div style="padding: 30px;">
                                        <div style="background-color: #f8fafc; border-radius: 8px; padding: 20px; border: 1px solid #e2e8f0; white-space: pre-line; font-family: 'Inter', monospace; line-height: 1.6;">
                                            %s
                                        </div>

                                        <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 30px 0;">

                                        <p style="color: #94a3b8; font-size: 12px; text-align: center;">
                                            This is an automated message from HisaabLite Support.<br>
                                            © 2026 HisaabLite. All rights reserved.
                                        </p>
                                    </div>
                                </div>
                            </body>
                            </html>
                            """,
                    content.replace("\n", "<br>"));

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);

            helper.setFrom(appConfig.getFromEmail());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Support email sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send support email to {}: {}", to, e.getMessage());
        }
    }

    // ==================== FALLBACK METHODS (No changes needed)
    // ====================

    private String generateFallbackVerificationEmail(User user, String verificationLink, SubscriptionPlan plan) {
        return String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                            <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                                <h1 style="color: white; margin: 0;">Welcome to HisaabLite!</h1>
                            </div>
                            <div style="background: #ffffff; padding: 30px; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 10px 10px;">
                                <h2 style="color: #1e293b;">Hello %s!</h2>
                                <p style="color: #475569;">Thank you for registering with HisaabLite. Please verify your email address to activate your account.</p>

                                <div style="background: #f8fafc; padding: 15px; border-radius: 8px; margin: 20px 0;">
                                    <p><strong>Plan:</strong> %s</p>
                                    <p><strong>Shop:</strong> %s</p>
                                </div>

                                <div style="text-align: center; margin: 30px 0;">
                                    <a href="%s" style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                                        Verify Email Address
                                    </a>
                                </div>

                                <p style="color: #64748b; font-size: 14px;">This verification link will expire in 24 hours.</p>
                                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                                <p style="color: #94a3b8; font-size: 12px; text-align: center;">© 2026 HisaabLite. All rights reserved.</p>
                            </div>
                        </div>
                        """,
                user.getName(), plan.getPlanName(), user.getShop().getName(), verificationLink);
    }

    private String generateFallbackWelcomeEmail(User user, SubscriptionPlan plan) {
        String loginUrl = urlService.getLoginUrl();
        String expiryText = "";
        if (plan.getDurationInDays() != null && plan.getDurationInDays() > 0 && user.getSubscriptionEndDate() != null) {
            expiryText = String.format("<p><strong>Valid until:</strong> %s</p>",
                    user.getSubscriptionEndDate().toLocalDate());
        }

        return String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                            <div style="background: linear-gradient(135deg, #10b981 0%%, #059669 100%%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                                <h1 style="color: white; margin: 0;">🎉 Welcome Aboard!</h1>
                            </div>
                            <div style="background: #ffffff; padding: 30px; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 10px 10px;">
                                <h2 style="color: #1e293b;">Hello %s!</h2>
                                <p style="color: #475569;">Your %s plan is now active and ready to use.</p>

                                <div style="background: #f8fafc; padding: 20px; border-radius: 8px; margin: 20px 0;">
                                    <h3 style="color: #1e293b; margin-top: 0;">Plan Features:</h3>
                                    <p><strong>Max Users:</strong> %s</p>
                                    <p><strong>Max Products:</strong> %s</p>
                                    %s
                                </div>

                                <div style="text-align: center; margin: 30px 0;">
                                    <a href="%s" style="background: #10b981; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                                        Go to Dashboard
                                    </a>
                                </div>

                                <p style="color: #64748b; font-size: 14px;">Login with your email: <strong>%s</strong></p>
                                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                                <p style="color: #94a3b8; font-size: 12px; text-align: center;">© 2026 HisaabLite. All rights reserved.</p>
                            </div>
                        </div>
                        """,
                user.getName(),
                plan.getPlanName(),
                plan.getMaxUsers() == -1 ? "Unlimited" : plan.getMaxUsers(),
                plan.getMaxProducts() == -1 ? "Unlimited" : plan.getMaxProducts(),
                expiryText,
                loginUrl,
                user.getUsername());
    }

    private String generateFallbackApprovalEmail(User user, SubscriptionPlan plan) {
        String loginUrl = urlService.getLoginUrl();
        String expiryInfo = "";
        if (plan.getDurationInDays() != null && plan.getDurationInDays() > 0 && user.getSubscriptionEndDate() != null) {
            expiryInfo = String.format("<p><strong>Valid until:</strong> %s</p>",
                    user.getSubscriptionEndDate().toLocalDate());
        }

        return String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                            <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                                <h1 style="color: white; margin: 0;">✅ Account Approved!</h1>
                            </div>
                            <div style="background: #ffffff; padding: 30px; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 10px 10px;">
                                <h2 style="color: #1e293b;">Hello %s!</h2>
                                <p style="color: #475569;">Great news! Your HisaabLite account has been approved.</p>

                                <div style="background: #f8fafc; padding: 20px; border-radius: 8px; margin: 20px 0;">
                                    <h3 style="color: #1e293b; margin-top: 0;">Your %s Plan</h3>
                                    <p><strong>Price:</strong> ₹%.2f/month</p>
                                    <p><strong>Duration:</strong> %d days</p>
                                    <p><strong>Max Users:</strong> %s</p>
                                    <p><strong>Max Products:</strong> %s</p>
                                    %s
                                    <p><strong>Features:</strong> %s</p>
                                </div>

                                <div style="text-align: center; margin: 30px 0;">
                                    <a href="%s" style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                                        Go to Dashboard
                                    </a>
                                </div>

                                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                                <p style="color: #94a3b8; font-size: 12px; text-align: center;">© 2026 HisaabLite. All rights reserved.</p>
                            </div>
                        </div>
                        """,
                user.getName(),
                plan.getPlanName(),
                plan.getPrice(),
                plan.getDurationInDays() != null ? plan.getDurationInDays() : 0,
                plan.getMaxUsers() == -1 ? "Unlimited" : plan.getMaxUsers(),
                plan.getMaxProducts() == -1 ? "Unlimited" : plan.getMaxProducts(),
                expiryInfo,
                plan.getDescription() != null ? plan.getDescription() : "No description",
                loginUrl);
    }

    private String generateFallbackExpiryReminderEmail(User user, long daysLeft) {
        String renewUrl = urlService.getRenewUrl();
        return String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                            <div style="background: #f59e0b; padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                                <h1 style="color: white; margin: 0;">⚠️ Subscription Expiring Soon</h1>
                            </div>
                            <div style="background: #ffffff; padding: 30px; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 10px 10px;">
                                <h2 style="color: #1e293b;">Dear %s,</h2>
                                <p style="color: #475569;">Your HisaabLite subscription will expire in <strong>%d days</strong> on <strong>%s</strong>.</p>

                                <div style="text-align: center; margin: 30px 0;">
                                    <a href="%s" style="background: #f59e0b; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                                        Renew Now
                                    </a>
                                </div>

                                <p style="color: #64748b; font-size: 14px;">Don't lose access to premium features. Renew today!</p>
                                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                                <p style="color: #94a3b8; font-size: 12px; text-align: center;">© 2026 HisaabLite. All rights reserved.</p>
                            </div>
                        </div>
                        """,
                user.getName(),
                daysLeft,
                user.getSubscriptionEndDate() != null ? user.getSubscriptionEndDate().toLocalDate() : "Unknown",
                renewUrl);
    }

    private String generateFallbackExpiredEmail(User user) {
        String plansUrl = urlService.getPlansUrl();
        return String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                            <div style="background: #ef4444; padding: 30px; text-align: center; border-radius: 10px 10px 0 0;">
                                <h1 style="color: white; margin: 0;">❌ Subscription Expired</h1>
                            </div>
                            <div style="background: #ffffff; padding: 30px; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 10px 10px;">
                                <h2 style="color: #1e293b;">Dear %s,</h2>
                                <p style="color: #475569;">Your HisaabLite subscription has expired. You've been downgraded to the FREE plan.</p>

                                <div style="text-align: center; margin: 30px 0;">
                                    <a href="%s" style="background: #ef4444; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block;">
                                        View Plans
                                    </a>
                                </div>

                                <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;">
                                <p style="color: #94a3b8; font-size: 12px; text-align: center;">© 2026 HisaabLite. All rights reserved.</p>
                            </div>
                        </div>
                        """,
                user.getName(),
                plansUrl);
    }
}
