package com.smartcity.backend.service;

import com.smartcity.backend.dto.ChangePasswordRequest;
import com.smartcity.backend.dto.ProfileResponse;
import com.smartcity.backend.dto.UpdateProfileRequest;
import com.smartcity.backend.exception.InvalidCredentialsException;
import com.smartcity.backend.exception.PasswordMismatchException;
import com.smartcity.backend.exception.SamePasswordException;
import com.smartcity.backend.exception.UserNotFoundException;
import com.smartcity.backend.model.User;
import com.smartcity.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private User getCurrentUser() {
        Object principal = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        if (!(principal instanceof User)) {
            throw new UserNotFoundException("Authenticated user not found");
        }
        return (User) principal;
    }

    public ProfileResponse getProfile() {
        User user = getCurrentUser();

        return ProfileResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .nationalId(user.getNationalId())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();

        user.setFullName(request.getFullName());

        if (request.getPhoneNumber() != null
                && !request.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        User updatedUser = userRepository.save(user);
        return ProfileResponse.builder()
                .userId(updatedUser.getId())
                .fullName(updatedUser.getFullName())
                .email(updatedUser.getEmail())
                .phoneNumber(updatedUser.getPhoneNumber())
                .nationalId(user.getNationalId())
                .role(updatedUser.getRole())
                .build();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUser();

        if (!passwordEncoder.matches(
                request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException(
                    "Current password is incorrect"
            );
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException(
                    "New password and confirmation do not match"
            );
        }

        if (passwordEncoder.matches(
                request.getNewPassword(), user.getPassword())) {
            throw new SamePasswordException(
                    "New password must be different from current password"
            );
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ── FCM / Location ────────────────────────────────────────────────────────

    /**
     * Stores (or replaces) the Firebase Cloud Messaging token for the
     * currently authenticated user. Called by the app on every launch.
     */
    @Transactional
    public void updateFcmToken(String token) {
        User user = getCurrentUser();
        user.setFcmToken(token != null && !token.isBlank() ? token.trim() : null);
        userRepository.save(user);
    }

    /**
     * Updates the user's last known location (used for "new report near you" notifications).
     * The app sends this whenever it gets a fresh GPS fix.
     */
    @Transactional
    public void updateLocation(double lat, double lon) {
        User user = getCurrentUser();
        user.setLastKnownLat(lat);
        user.setLastKnownLon(lon);
        userRepository.save(user);
    }
}