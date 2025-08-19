package com.example.cifixer.agents;

import com.example.cifixer.core.Agent;
import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.notification.EmailTemplate;
import com.example.cifixer.notification.RecipientResolver;
import com.example.cifixer.store.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent responsible for sending email notifications to stakeholders about build fix results.
 * Handles success, failure, and manual intervention notifications with Spring project context.
 */
@Component
public class NotificationAgent implements Agent<NotifyPayload> {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationAgent.class);
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private EmailTemplate emailTemplate;
    
    @Autowired
    private RecipientResolver recipientResolver;
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Value("${spring.mail.from:ci-fixer@example.com}")
    private String fromEmail;
    
    @Value("${notification.enabled:true}")
    private boolean notificationEnabled;
    
    @Override
    public TaskResult handle(Task task, NotifyPayload payload) {
        logger.info("Processing notification task for build {} with type {}", 
                task.getBuild().getId(), payload.getNotificationType());
        
        if (!notificationEnabled) {
            logger.info("Notifications are disabled, skipping notification for build {}", task.getBuild().getId());
            return TaskResult.success("Notifications disabled");
        }
        
        try {
            Build build = task.getBuild();
            
            // Check if notification already sent
            if (notificationRepository.existsByBuildIdAndNotificationType(build.getId(), payload.getNotificationType())) {
                logger.warn("Notification of type {} already sent for build {}", 
                        payload.getNotificationType(), build.getId());
                return TaskResult.success("Notification already sent");
            }
            
            // Resolve recipients
            List<String> recipients = resolveRecipients(build, payload.getNotificationType());
            if (recipients.isEmpty()) {
                logger.warn("No recipients found for notification type {} on build {}", 
                        payload.getNotificationType(), build.getId());
                return TaskResult.success("No recipients configured");
            }
            
            // Generate email content
            String subject = emailTemplate.generateSubject(build, payload.getNotificationType(), payload.getPrNumber());
            String content = generateEmailContent(build, payload);
            
            // Send notifications to all recipients
            int successCount = 0;
            int failureCount = 0;
            
            for (String recipient : recipients) {
                try {
                    sendEmail(recipient, subject, content);
                    
                    // Save notification record
                    Notification notification = new Notification(build, payload.getNotificationType(), 
                            recipient, subject, content);
                    notification.setStatus(NotificationStatus.SENT);
                    notificationRepository.save(notification);
                    
                    successCount++;
                    logger.info("Sent {} notification to {} for build {}", 
                            payload.getNotificationType(), recipient, build.getId());
                    
                } catch (Exception e) {
                    logger.error("Failed to send {} notification to {} for build {}: {}", 
                            payload.getNotificationType(), recipient, build.getId(), e.getMessage());
                    
                    // Save failed notification record
                    Notification notification = new Notification(build, payload.getNotificationType(), 
                            recipient, subject, content);
                    notification.setStatus(NotificationStatus.FAILED);
                    notificationRepository.save(notification);
                    
                    failureCount++;
                }
            }
            
            // Prepare result metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("recipientCount", recipients.size());
            metadata.put("successCount", successCount);
            metadata.put("failureCount", failureCount);
            metadata.put("notificationType", payload.getNotificationType().toString());
            
            if (failureCount > 0) {
                String message = String.format("Sent %d/%d notifications successfully", successCount, recipients.size());
                return TaskResult.success(message, metadata);
            } else {
                String message = String.format("Successfully sent notifications to %d recipients", successCount);
                return TaskResult.success(message, metadata);
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error while sending notifications for build {}: {}", 
                    task.getBuild().getId(), e.getMessage(), e);
            return TaskResult.failure("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Resolves recipients based on notification type and build information.
     */
    private List<String> resolveRecipients(Build build, NotificationType notificationType) {
        switch (notificationType) {
            case SUCCESS:
                return recipientResolver.resolveSuccessRecipients(build);
            case FAILURE:
                return recipientResolver.resolveFailureRecipients(build);
            case MANUAL_INTERVENTION:
                return recipientResolver.resolveManualInterventionRecipients(build);
            default:
                logger.warn("Unknown notification type: {}", notificationType);
                return recipientResolver.resolveFailureRecipients(build);
        }
    }
    
    /**
     * Generates email content based on notification type and payload data.
     */
    private String generateEmailContent(Build build, NotifyPayload payload) {
        switch (payload.getNotificationType()) {
            case SUCCESS:
                return emailTemplate.generateSuccessContent(
                        build, 
                        payload.getPrUrl(), 
                        payload.getPrNumber(),
                        payload.getPlanSummary(),
                        payload.getPatchedFiles(),
                        payload.getValidationResults()
                );
            case FAILURE:
                return emailTemplate.generateFailureContent(
                        build,
                        payload.getErrorMessage(),
                        payload.getPlanSummary()
                );
            case MANUAL_INTERVENTION:
                return emailTemplate.generateManualInterventionContent(
                        build,
                        payload.getErrorMessage(),
                        payload.getPlanSummary()
                );
            default:
                throw new IllegalArgumentException("Unsupported notification type: " + payload.getNotificationType());
        }
    }
    
    /**
     * Sends an HTML email to the specified recipient.
     */
    private void sendEmail(String recipient, String subject, String htmlContent) throws MessagingException, MailException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true indicates HTML content
        
        mailSender.send(message);
    }
}