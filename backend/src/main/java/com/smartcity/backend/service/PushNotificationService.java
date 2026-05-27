package com.smartcity.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

/**
 * Sends Firebase Cloud Messaging (FCM) push notifications.
 *
 * Requires FIREBASE_SERVICE_ACCOUNT_PATH in .env to point to a valid
 * Firebase service-account JSON file. When the path is empty/missing,
 * FCM is silently disabled so the app still works without notifications.
 */
@Slf4j
@Service
public class PushNotificationService {

    private static final String CHANNEL_ID = "smartcity_notifications";

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    private boolean fcmEnabled = false;

    @PostConstruct
    public void init() {
        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            log.warn("FCM disabled — set FIREBASE_SERVICE_ACCOUNT_PATH in .env to enable push notifications.");
            return;
        }
        try {
            FileInputStream serviceAccount = new FileInputStream(serviceAccountPath);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            fcmEnabled = true;
            log.info("Firebase Admin SDK initialised — FCM push notifications enabled.");
        } catch (Exception e) {
            log.error("Failed to initialise Firebase Admin SDK: {} — FCM disabled.", e.getMessage());
        }
    }

    // ── Single-device notification ────────────────────────────────────────────

    /**
     * Sends a push notification to one device.
     *
     * @param fcmToken  FCM registration token stored on the User entity
     * @param title     Notification title
     * @param body      Notification body text
     * @param data      Extra key-value pairs (e.g. reportId, type) for deep linking
     */
    public void sendToUser(String fcmToken, String title, String body,
                           Map<String, String> data) {
        if (!fcmEnabled || fcmToken == null || fcmToken.isBlank()) return;

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(CHANNEL_ID)
                                    .setIcon("@mipmap/ic_launcher")
                                    .build())
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .build();

            String msgId = FirebaseMessaging.getInstance().send(message);
            log.debug("FCM sent (msgId={})", msgId);

        } catch (FirebaseMessagingException e) {
            // Stale token — log only; don't crash the main request flow
            log.warn("FCM send failed for token ...{}: {}",
                    fcmToken.substring(Math.max(0, fcmToken.length() - 6)), e.getMessage());
        }
    }

    // ── Multi-device notification ─────────────────────────────────────────────

    /**
     * Sends the same notification to multiple devices (e.g. nearby users).
     * FCM multicast supports up to 500 tokens per call.
     */
    public void sendToMultiple(List<String> fcmTokens, String title, String body,
                               Map<String, String> data) {
        if (!fcmEnabled || fcmTokens == null || fcmTokens.isEmpty()) return;

        // FCM limits multicast to 500 tokens; chunk if necessary
        int chunkSize = 500;
        for (int i = 0; i < fcmTokens.size(); i += chunkSize) {
            List<String> chunk = fcmTokens.subList(i, Math.min(i + chunkSize, fcmTokens.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .addAllTokens(chunk)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setChannelId(CHANNEL_ID)
                                        .build())
                                .build())
                        .putAllData(data != null ? data : Map.of())
                        .build();

                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                log.info("FCM multicast: {}/{} delivered.", response.getSuccessCount(), chunk.size());

            } catch (FirebaseMessagingException e) {
                log.warn("FCM multicast failed: {}", e.getMessage());
            }
        }
    }
}
