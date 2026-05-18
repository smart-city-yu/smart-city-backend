package com.smartcity.backend.controller;

import com.smartcity.backend.dto.ChangePasswordRequest;
import com.smartcity.backend.dto.FcmTokenRequest;
import com.smartcity.backend.dto.LocationUpdateRequest;
import com.smartcity.backend.dto.ProfileResponse;
import com.smartcity.backend.dto.UpdateProfileRequest;
import com.smartcity.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getProfile());
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }

    /** Called by the Flutter app on every launch to register / refresh the FCM token. */
    @PutMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@RequestBody FcmTokenRequest request) {
        userService.updateFcmToken(request.token());
        return ResponseEntity.ok().build();
    }

    /** Called by the Flutter app whenever it gets a fresh GPS fix. */
    @PutMapping("/location")
    public ResponseEntity<Void> updateLocation(@RequestBody LocationUpdateRequest request) {
        userService.updateLocation(request.lat(), request.lon());
        return ResponseEntity.ok().build();
    }
}