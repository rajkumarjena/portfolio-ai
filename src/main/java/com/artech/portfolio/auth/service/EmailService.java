package com.artech.portfolio.auth.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends the password-reset email (AUTH.md h).
 * If no SMTP server is configured the reset link is only logged, so the flow
 * stays testable in development without leaking a silent failure in production.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final String mailHost;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.from:noreply@portfolio.local}") String fromAddress,
                        @Value("${spring.mail.host:}") String mailHost) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
        this.mailHost = mailHost;
    }

    public void sendPasswordResetEmail(String toEmail, String clientName, String resetLink) {
        JavaMailSender sender = mailHost == null || mailHost.isBlank()
                ? null : mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("No SMTP server configured - password reset link for {} ({}): {}",
                    clientName, toEmail, resetLink);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Reset your Portfolio password");
            helper.setText(
                    "Hello " + clientName + ",\n\n"
                    + "Use the link below to reset your password. It expires in 30 minutes:\n"
                    + resetLink + "\n\n"
                    + "If you did not request this, you can safely ignore this email.\n", false);
            sender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            // Never fail the business flow silently - log the link so support can recover it
            log.error("Failed to send password reset email to {} - reset link: {}", toEmail, resetLink, e);
        }
    }
}
