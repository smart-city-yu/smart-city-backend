package com.smartcity.backend.service;

import com.smartcity.backend.dto.AuthResponse;
import com.smartcity.backend.dto.LoginRequest;
import com.smartcity.backend.dto.RegisterRequest;
import com.smartcity.backend.exception.*;
import com.smartcity.backend.enums.Role;
import com.smartcity.backend.model.PasswordResetToken;
import com.smartcity.backend.model.User;
import com.smartcity.backend.repository.PasswordResetTokenRepository;
import com.smartcity.backend.repository.UserRepository;
import com.smartcity.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final PasswordResetTokenRepository resetTokenRepository;

//    public AuthService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, JwtUtil jwtUtil, EmailService emailService) {
//        this.userRepository = userRepository;
//        this.passwordEncoder = passwordEncoder;
//        this.jwtUtil = jwtUtil;
//        this.emailService = emailService;
//    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Email already registered: " + request.getEmail()
            );
        }

        if (userRepository.existsByNationalId(request.getNationalId())) {
            throw new NationalIdAlreadyExistsException(
                    "An account with this National ID already exists"
            );
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(hashedPassword)
                .phoneNumber(request.getPhoneNumber())
                .nationalId(request.getNationalId())
                .role(Role.USER)
                .enabled(false)
                .build();

        User savedUser = userRepository.save(user);
        emailService.sendVerificationEmail(savedUser);

        return AuthResponse.builder()
                .token(null)
                .userId(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .build();
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException(
                        "No account found with email: " + request.getEmail()
                ));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException(
                    "Please verify your email before logging in"
            );
        }

        String token = jwtUtil.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    // -------------------------------------------------------------------------
    // Forgot Password — always returns generic message (security best practice)
    // -------------------------------------------------------------------------
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            // Don't send reset link to unverified accounts
            if (!user.isEnabled()) return;

            // Delete any existing reset token for this user
            resetTokenRepository.deleteByUserId(user.getId());

            String token = UUID.randomUUID().toString();
            resetTokenRepository.save(PasswordResetToken.builder()
                    .token(token)
                    .userId(user.getId())
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .used(false)
                    .build());

            emailService.sendPasswordResetEmail(user, token);
        });
        // Always returns void — caller sends same generic response regardless
    }

    // -------------------------------------------------------------------------
    // Reset Password — validates token and updates password
    // -------------------------------------------------------------------------
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("INVALID_TOKEN"));

        if (resetToken.isUsed())
            throw new RuntimeException("USED_TOKEN");

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("EXPIRED_TOKEN");

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new RuntimeException("INVALID_TOKEN"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
    }
}