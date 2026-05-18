package com.smartcity.backend.repository;

import com.smartcity.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNationalId(String nationalId);

    // Used by scheduler to auto-delete unverified accounts older than 48h
    List<User> findAllByEnabledFalseAndCreatedAtBefore(LocalDateTime cutoff);

    /**
     * Finds users within a lat/lon bounding box who have an FCM token registered.
     * Used to send "new report near you" notifications (~1 km radius).
     * Excludes the report owner (excludeUserId).
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.fcmToken IS NOT NULL
              AND u.id != :excludeUserId
              AND u.lastKnownLat IS NOT NULL
              AND u.lastKnownLat  BETWEEN :minLat AND :maxLat
              AND u.lastKnownLon  BETWEEN :minLon AND :maxLon
            """)
    List<User> findNearbyUsersWithToken(
            @Param("minLat")        double minLat,
            @Param("maxLat")        double maxLat,
            @Param("minLon")        double minLon,
            @Param("maxLon")        double maxLon,
            @Param("excludeUserId") Long   excludeUserId);
}
