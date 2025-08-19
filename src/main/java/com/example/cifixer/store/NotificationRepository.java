package com.example.cifixer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for managing Notification entities.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /**
     * Find all notifications for a specific build.
     */
    List<Notification> findByBuildIdOrderBySentAtDesc(Long buildId);
    
    /**
     * Find notifications by type and build.
     */
    List<Notification> findByBuildIdAndNotificationType(Long buildId, NotificationType notificationType);
    
    /**
     * Check if a notification of a specific type has already been sent for a build.
     */
    boolean existsByBuildIdAndNotificationType(Long buildId, NotificationType notificationType);
    
    /**
     * Find notifications sent to a specific recipient.
     */
    List<Notification> findByRecipientEmailOrderBySentAtDesc(String recipientEmail);
    
    /**
     * Find failed notifications for retry.
     */
    List<Notification> findByStatusAndSentAtBefore(NotificationStatus status, LocalDateTime before);
    
    /**
     * Count notifications by type for reporting.
     */
    @Query("SELECT n.notificationType, COUNT(n) FROM Notification n GROUP BY n.notificationType")
    List<Object[]> countByNotificationType();
}