package com.smartcity.backend.controller;

import com.smartcity.backend.dto.AuthResponse;
import com.smartcity.backend.dto.LoginRequest;
import com.smartcity.backend.dto.RegisterRequest;
import com.smartcity.backend.model.EmailVerificationToken;
import com.smartcity.backend.model.User;
import com.smartcity.backend.repository.EmailVerificationTokenRepository;
import com.smartcity.backend.repository.UserRepository;
import com.smartcity.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        Optional<EmailVerificationToken> optToken = tokenRepository.findByToken(token);

        if (optToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(htmlPage("Invalid Link",
                            "This verification link is invalid or has already been used.",
                            false));
        }

        EmailVerificationToken verificationToken = optToken.get();

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(verificationToken);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(htmlPage("Link Expired",
                            "This verification link has expired. Please register again to get a new link.",
                            false));
        }

        Optional<User> optUser = userRepository.findById(verificationToken.getUserId());
        if (optUser.isEmpty()) {
            tokenRepository.delete(verificationToken);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(htmlPage("Error", "Account not found.", false));
        }

        User user = optUser.get();
        user.setEnabled(true);
        userRepository.save(user);
        tokenRepository.delete(verificationToken);

        return ResponseEntity.ok(htmlPage("Email Verified!",
                "Your account is now active. You can close this tab and sign in to the SmartCity app.",
                true));
    }

    // ── POST /api/auth/forgot-password ───────────────────────────────────────
    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "Email is required."));

        authService.forgotPassword(email.trim().toLowerCase());

        // Always same response — never reveal if email exists
        return ResponseEntity.ok(java.util.Map.of(
                "message", "If this email is registered and verified, a reset link has been sent."));
    }

    // ── GET /api/auth/reset-password?token=xxx — serves the HTML reset form ─
    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> showResetForm(@RequestParam String token) {
        return ResponseEntity.ok(buildResetFormHtml(token));
    }

    // ── POST /api/auth/reset-password — processes the form submission ────────
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> processResetForm(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword) {

        if (!password.equals(confirmPassword))
            return ResponseEntity.badRequest()
                    .body(htmlPage("Passwords Don't Match",
                            "The passwords you entered do not match. Please go back and try again.", false));

        if (password.length() < 8)
            return ResponseEntity.badRequest()
                    .body(htmlPage("Password Too Short",
                            "Password must be at least 8 characters long.", false));

        try {
            authService.resetPassword(token, password);
            return ResponseEntity.ok(htmlPage("Password Reset!",
                    "Your password has been updated successfully. You can now log in to the SmartCity app with your new password.",
                    true));
        } catch (RuntimeException e) {
            return switch (e.getMessage()) {
                case "EXPIRED_TOKEN" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(htmlPage("Link Expired",
                                "This password reset link has expired. Please request a new one from the app.", false));
                case "USED_TOKEN" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(htmlPage("Link Already Used",
                                "This reset link has already been used. Please request a new one if needed.", false));
                default -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(htmlPage("Invalid Link",
                                "This reset link is invalid. Please request a new one from the app.", false));
            };
        }
    }

    private String buildResetFormHtml(String token) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Reset Password — SmartCity</title>
                </head>
                <body style="font-family: Arial, sans-serif; background: #f4f4f4;
                             display: flex; justify-content: center; align-items: center;
                             min-height: 100vh; margin: 0;">
                  <div style="background: #fff; border-radius: 12px; padding: 48px 40px;
                              max-width: 420px; width: 90%%; box-shadow: 0 2px 12px #0001;">
                    <div style="font-size: 40px; text-align:center;">🔑</div>
                    <h2 style="color: #2e7d32; text-align: center; margin: 12px 0 4px;">Reset Password</h2>
                    <p style="color: #777; text-align: center; font-size: 14px; margin-bottom: 28px;">
                      Enter your new password below.
                    </p>
                    <form method="POST" action="/api/auth/reset-password">
                      <input type="hidden" name="token" value="%s">
                      <div style="margin-bottom: 16px;">
                        <label style="display:block; font-size:13px; color:#444; margin-bottom:6px;">
                          New Password (min. 8 characters)
                        </label>
                        <input type="password" name="password" required minlength="8"
                               style="width: 100%%; padding: 12px; border: 1.5px solid #ddd;
                                      border-radius: 8px; font-size: 15px; box-sizing: border-box;"/>
                      </div>
                      <div style="margin-bottom: 24px;">
                        <label style="display:block; font-size:13px; color:#444; margin-bottom:6px;">
                          Confirm New Password
                        </label>
                        <input type="password" name="confirmPassword" required minlength="8"
                               style="width: 100%%; padding: 12px; border: 1.5px solid #ddd;
                                      border-radius: 8px; font-size: 15px; box-sizing: border-box;"/>
                      </div>
                      <button type="submit"
                              style="width: 100%%; background: #2e7d32; color: #fff;
                                     padding: 14px; border: none; border-radius: 8px;
                                     font-size: 16px; cursor: pointer; font-weight: bold;">
                        Set New Password
                      </button>
                    </form>
                  </div>
                </body>
                </html>
                """.formatted(token);
    }

    private String htmlPage(String title, String message, boolean success) {
        String color = success ? "#2e7d32" : "#c62828";
        String icon  = success ? "✅" : "❌";
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f4f4f4;
                             display: flex; justify-content: center; align-items: center;
                             min-height: 100vh; margin: 0;">
                  <div style="background: #fff; border-radius: 12px; padding: 48px 40px;
                              max-width: 460px; text-align: center; box-shadow: 0 2px 12px #0001;">
                    <div style="font-size: 52px;">%s</div>
                    <h2 style="color: %s; margin: 16px 0 8px;">%s</h2>
                    <p style="color: #555; line-height: 1.6;">%s</p>
                  </div>
                </body>
                </html>
                """.formatted(icon, color, title, message);
    }
}