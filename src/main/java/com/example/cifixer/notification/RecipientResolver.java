package com.example.cifixer.notification;

import com.example.cifixer.store.Build;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for resolving email recipients for notifications.
 * Combines commit authors with configured recipient lists.
 */
@Component
public class RecipientResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(RecipientResolver.class);
    
    @Value("${notification.recipients.default:}")
    private String defaultRecipients;
    
    @Value("${notification.recipients.success:}")
    private String successRecipients;
    
    @Value("${notification.recipients.failure:}")
    private String failureRecipients;
    
    @Value("${notification.recipients.manual:}")
    private String manualInterventionRecipients;
    
    @Value("${notification.include-commit-authors:true}")
    private boolean includeCommitAuthors;
    
    /**
     * Resolves recipients for success notifications.
     */
    public List<String> resolveSuccessRecipients(Build build) {
        Set<String> recipients = new LinkedHashSet<>();
        
        // Add configured success recipients
        recipients.addAll(parseRecipientList(successRecipients));
        recipients.addAll(parseRecipientList(defaultRecipients));
        
        // Add commit authors if enabled
        if (includeCommitAuthors) {
            recipients.addAll(getCommitAuthors(build));
        }
        
        return new ArrayList<>(recipients);
    }
    
    /**
     * Resolves recipients for failure notifications.
     */
    public List<String> resolveFailureRecipients(Build build) {
        Set<String> recipients = new LinkedHashSet<>();
        
        // Add configured failure recipients
        recipients.addAll(parseRecipientList(failureRecipients));
        recipients.addAll(parseRecipientList(defaultRecipients));
        
        // Add commit authors if enabled
        if (includeCommitAuthors) {
            recipients.addAll(getCommitAuthors(build));
        }
        
        return new ArrayList<>(recipients);
    }
    
    /**
     * Resolves recipients for manual intervention notifications.
     */
    public List<String> resolveManualInterventionRecipients(Build build) {
        Set<String> recipients = new LinkedHashSet<>();
        
        // Add configured manual intervention recipients
        recipients.addAll(parseRecipientList(manualInterventionRecipients));
        recipients.addAll(parseRecipientList(defaultRecipients));
        
        // Add commit authors if enabled
        if (includeCommitAuthors) {
            recipients.addAll(getCommitAuthors(build));
        }
        
        return new ArrayList<>(recipients);
    }
    
    /**
     * Gets email addresses of commit authors from the Git repository.
     */
    private List<String> getCommitAuthors(Build build) {
        List<String> authors = new ArrayList<>();
        
        try {
            String workingDir = "/work/" + build.getId();
            File gitDir = new File(workingDir);
            
            if (!gitDir.exists()) {
                logger.warn("Working directory does not exist for build {}: {}", build.getId(), workingDir);
                return authors;
            }
            
            try (Git git = Git.open(gitDir)) {
                ObjectId commitId = git.getRepository().resolve(build.getCommitSha());
                if (commitId == null) {
                    logger.warn("Commit {} not found in repository for build {}", build.getCommitSha(), build.getId());
                    return authors;
                }
                
                try (RevWalk revWalk = new RevWalk(git.getRepository())) {
                    RevCommit commit = revWalk.parseCommit(commitId);
                    
                    // Get author email
                    PersonIdent author = commit.getAuthorIdent();
                    if (author != null && author.getEmailAddress() != null && !author.getEmailAddress().isEmpty()) {
                        String email = author.getEmailAddress().toLowerCase();
                        if (isValidEmail(email)) {
                            authors.add(email);
                        }
                    }
                    
                    // Get committer email if different from author
                    PersonIdent committer = commit.getCommitterIdent();
                    if (committer != null && committer.getEmailAddress() != null && !committer.getEmailAddress().isEmpty()) {
                        String email = committer.getEmailAddress().toLowerCase();
                        if (isValidEmail(email) && !authors.contains(email)) {
                            authors.add(email);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("Failed to get commit authors for build {}: {}", build.getId(), e.getMessage());
        }
        
        return authors;
    }
    
    /**
     * Parses a comma-separated list of email recipients.
     */
    private List<String> parseRecipientList(String recipientString) {
        if (recipientString == null || recipientString.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(recipientString.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty() && isValidEmail(email))
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }
    
    /**
     * Basic email validation.
     */
    private boolean isValidEmail(String email) {
        return email != null && 
               email.contains("@") && 
               email.contains(".") && 
               !email.startsWith("@") && 
               !email.endsWith("@") &&
               !email.contains("noreply") &&
               !email.contains("no-reply");
    }
}