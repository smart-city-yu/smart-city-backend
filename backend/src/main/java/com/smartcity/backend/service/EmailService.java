package com.smartcity.backend.service;

import com.smartcity.backend.model.EmailVerificationToken;
import com.smartcity.backend.model.User;
import com.smartcity.backend.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailVerificationTokenRepository tokenRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendVerificationEmail(User user) {
        tokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(verificationToken);

        String verifyUrl = baseUrl + "/api/auth/verify?token=" + token;
        String html = buildEmailHtml(user.getFullName(), verifyUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("Verify your SmartCity account");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send verification email: " + e.getMessage(), e);
        }
    }

    private String buildEmailHtml(String fullName, String verifyUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f4f4f4; margin: 0; padding: 20px;">
                  <div style="max-width: 520px; margin: auto; background: #ffffff; border-radius: 12px; padding: 40px;">
                    <h2 style="color: #2e7d32; margin-bottom: 8px;">Welcome to SmartCity!</h2>
                    <p style="color: #333;">Hi %s,</p>
                    <p style="color: #555;">Click the button below to verify your email address. This link expires in 24 hours.</p>
                    <div style="text-align: center; margin: 32px 0;">
                      <a href="%s"
                         style="background: #2e7d32; color: #fff; padding: 14px 32px;
                                border-radius: 8px; text-decoration: none; font-size: 16px;">
                        Verify Email
                      </a>
                    </div>
                    <p style="color: #999; font-size: 12px;">If you did not create an account, you can safely ignore this email.</p>
                  </div>
                </body>
                </html>
                """.formatted(fullName, verifyUrl);
    }
}
