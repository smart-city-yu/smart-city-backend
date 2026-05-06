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