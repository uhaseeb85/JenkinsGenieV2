package com.example.cifixer.store;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a notification sent to stakeholders.
 */
@Entity
@Table(name = "notifications")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private Build build;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;
    
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;
    
    @Column(nullable = false, length = 500)
    private String subject;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    
    public Notification() {
        this.sentAt = LocalDateTime.now();
        this.status = NotificationStatus.SENT;
    }
    
    public Notification(Build build, NotificationType notificationType, String recipientEmail, 
                       String subject, String content) {
        this();
        this.build = build;
        this.notificationType = notificationType;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.content = content;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Build getBuild() {
        return build;
    }
    
    public void setBuild(Build build) {
        this.build = build;
    }
    
    public NotificationType getNotificationType() {
        return notificationType;
    }
    
    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }
    
    public String getRecipientEmail() {
        return recipientEmail;
    }
    
    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public LocalDateTime getSentAt() {
        return sentAt;
    }
    
    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
    
    public NotificationStatus getStatus() {
        return status;
    }
    
    public void setStatus(NotificationStatus status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "Notification{" +
                "id=" + id +
                ", buildId=" + (build != null ? build.getId() : null) +
                ", notificationType=" + notificationType +
                ", recipientEmail='" + recipientEmail + '\'' +
                ", subject='" + subject + '\'' +
                ", sentAt=" + sentAt +
                ", status=" + status +
                '}';
    }
}