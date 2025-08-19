package com.example.cifixer.agents;

import com.example.cifixer.core.Task;
import com.example.cifixer.core.TaskResult;
import com.example.cifixer.core.TaskStatus;
import com.example.cifixer.core.TaskType;
import com.example.cifixer.notification.EmailTemplate;
import com.example.cifixer.notification.RecipientResolver;
import com.example.cifixer.store.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationAgentTest {
    
    @Mock
    private JavaMailSender mailSender;
    
    @Mock
    private EmailTemplate emailTemplate;
    
    @Mock
    private RecipientResolver recipientResolver;
    
    @Mock
    private NotificationRepository notificationRepository;
    
    @Mock
    private MimeMessage mimeMessage;
    
    @InjectMocks
    private NotificationAgent notificationAgent;
    
    private Build testBuild;
    private Task testTask;
    private NotifyPayload testPayload;
    
    @BeforeEach
    void setUp() {
        testBuild = new Build();
        testBuild.setId(123L);
        testBuild.setJob("my-spring-app");
        testBuild.setBuildNumber(456);
        
        testTask = new Task(testBuild, TaskType.NOTIFY);
        testTask.setId(789L);
        
        testPayload = new NotifyPayload();
        testPayload.setNotificationType(NotificationType.SUCCESS);
        testPayload.setPrUrl("https://github.com/owner/repo/pull/123");
        testPayload.setPrNumber(123);
        
        // Set default configuration
        ReflectionTestUtils.setField(notificationAgent, "fromEmail", "ci-fixer@example.com");
        ReflectionTestUtils.setField(notificationAgent, "notificationEnabled", true);
        
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }
    
    @Test
    void shouldSendSuccessNotification() throws Exception {
        // Arrange
        List<String> recipients = Arrays.asList("dev1@example.com", "dev2@example.com");
        when(recipientResolver.resolveSuccessRecipients(testBuild)).thenReturn(recipients);
        when(notificationRepository.existsByBuildIdAndNotificationType(123L, NotificationType.SUCCESS)).thenReturn(false);
        when(emailTemplate.generateSubject(testBuild, NotificationType.SUCCESS, 123)).thenReturn("Test Subject");
        when(emailTemplate.generateSuccessContent(eq(testBuild), eq("https://github.com/owner/repo/pull/123"), 
                eq(123), any(), any(), any())).thenReturn("Test Content");
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Successfully sent notifications to 2 recipients");
        assertThat(result.getMetadata().get("recipientCount")).isEqualTo(2);
        assertThat(result.getMetadata().get("successCount")).isEqualTo(2);
        assertThat(result.getMetadata().get("failureCount")).isEqualTo(0);
        
        verify(mailSender, times(2)).send(any(MimeMessage.class));
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }
    
    @Test
    void shouldSkipIfNotificationAlreadySent() {
        // Arrange
        when(notificationRepository.existsByBuildIdAndNotificationType(123L, NotificationType.SUCCESS)).thenReturn(true);
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Notification already sent");
        
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
    
    @Test
    void shouldSkipIfNotificationsDisabled() {
        // Arrange
        ReflectionTestUtils.setField(notificationAgent, "notificationEnabled", false);
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("Notifications disabled");
        
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
    
    @Test
    void shouldSkipIfNoRecipients() {
        // Arrange
        when(recipientResolver.resolveSuccessRecipients(testBuild)).thenReturn(Collections.emptyList());
        when(notificationRepository.existsByBuildIdAndNotificationType(123L, NotificationType.SUCCESS)).thenReturn(false);
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).isEqualTo("No recipients configured");
        
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
    
    @Test
    void shouldHandlePartialFailures() throws Exception {
        // Arrange
        List<String> recipients = Arrays.asList("dev1@example.com", "dev2@example.com");
        when(recipientResolver.resolveSuccessRecipients(testBuild)).thenReturn(recipients);
        when(notificationRepository.existsByBuildIdAndNotificationType(123L, NotificationType.SUCCESS)).thenReturn(false);
        when(emailTemplate.generateSubject(testBuild, NotificationType.SUCCESS, 123)).thenReturn("Test Subject");
        when(emailTemplate.generateSuccessContent(eq(testBuild), eq("https://github.com/owner/repo/pull/123"), 
                eq(123), any(), any(), any())).thenReturn("Test Content");
        
        // Make the second email fail
        doNothing().doThrow(new MailException("SMTP error") {}).when(mailSender).send(any(MimeMessage.class));
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getMessage()).contains("Sent 1/2 notifications successfully");
        assertThat(result.getMetadata().get("successCount")).isEqualTo(1);
        assertThat(result.getMetadata().get("failureCount")).isEqualTo(1);
        
        verify(mailSender, times(2)).send(any(MimeMessage.class));
        
        // Verify both success and failure notifications are saved
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        
        List<Notification> savedNotifications = notificationCaptor.getAllValues();
        assertThat(savedNotifications.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(savedNotifications.get(1).getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
    
    @Test
    void shouldHandleFailureNotification() {
        // Arrange
        testPayload.setNotificationType(NotificationType.FAILURE);
        testPayload.setErrorMessage("Build compilation failed");
        
        List<String> recipients = Arrays.asList("dev@example.com");
        when(recipientResolver.resolveFailureRecipients(testBuild)).thenReturn(recipients);
        when(notificationRepository.existsByBuildIdAndNotificationType(123L, NotificationType.FAILURE)).thenReturn(false);
        when(emailTemplate.generateSubject(testBuild, NotificationType.FAILURE, null)).thenReturn("Failure Subject");
        when(emailTemplate.generateFailureContent(testBuild, "Build compilation failed", null)).thenReturn("Failure Content");
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(recipientResolver).resolveFailureRecipients(testBuild);
        verify(emailTemplate).generateFailureContent(testBuild, "Build compilation failed", null);
    }
    
    @Test
    void shouldHandleManualInterventionNotification() {
        // Arrange
        testPayload.setNotificationType(NotificationType.MANUAL_INTERVENTION);
        testPayload.setErrorMessage("Manual intervention required");
        
        List<String> recipients = Arrays.asList("admin@example.com");
        when(recipientResolver.resolveManualInterventionRecipients(testBuild)).thenReturn(recipients);
        when(notificationRepository.existsByBuildIdAndNotificationType(123L, NotificationType.MANUAL_INTERVENTION)).thenReturn(false);
        when(emailTemplate.generateSubject(testBuild, NotificationType.MANUAL_INTERVENTION, null)).thenReturn("Manual Subject");
        when(emailTemplate.generateManualInterventionContent(testBuild, "Manual intervention required", null)).thenReturn("Manual Content");
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(recipientResolver).resolveManualInterventionRecipients(testBuild);
        verify(emailTemplate).generateManualInterventionContent(testBuild, "Manual intervention required", null);
    }
    
    @Test
    void shouldHandleUnexpectedError() {
        // Arrange
        when(recipientResolver.resolveSuccessRecipients(testBuild)).thenThrow(new RuntimeException("Unexpected error"));
        when(notificationRepository.existsByBuildIdAndNotificationType(123L, NotificationType.SUCCESS)).thenReturn(false);
        
        // Act
        TaskResult result = notificationAgent.handle(testTask, testPayload);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getMessage()).contains("Unexpected error");
    }
}