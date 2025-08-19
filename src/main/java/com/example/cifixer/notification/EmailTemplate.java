package com.example.cifixer.notification;

import com.example.cifixer.store.Build;
import com.example.cifixer.store.NotificationType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating email templates for different notification types.
 */
@Component
public class EmailTemplate {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Generates email subject for the notification.
     */
    public String generateSubject(Build build, NotificationType notificationType, Integer prNumber) {
        String shortSha = build.getCommitSha().substring(0, 7);
        
        switch (notificationType) {
            case SUCCESS:
                return String.format("✅ CI Fix: Build #%d fixed - PR #%d created (%s)", 
                        build.getBuildNumber(), prNumber, shortSha);
            case FAILURE:
                return String.format("❌ CI Fix: Build #%d failed to fix (%s)", 
                        build.getBuildNumber(), shortSha);
            case MANUAL_INTERVENTION:
                return String.format("⚠️ CI Fix: Build #%d requires manual intervention (%s)", 
                        build.getBuildNumber(), shortSha);
            default:
                return String.format("CI Fix: Build #%d notification (%s)", 
                        build.getBuildNumber(), shortSha);
        }
    }
    
    /**
     * Generates HTML email content for success notifications.
     */
    public String generateSuccessContent(Build build, String prUrl, Integer prNumber, 
                                       String planSummary, List<String> patchedFiles, 
                                       String validationResults) {
        String shortSha = build.getCommitSha().substring(0, 7);
        
        return String.format(SUCCESS_TEMPLATE,
                build.getJob(),
                build.getBuildNumber(),
                build.getBranch(),
                shortSha,
                build.getCommitSha(),
                build.getCreatedAt().format(DATE_FORMATTER),
                prUrl,
                prNumber,
                planSummary != null ? planSummary : "No plan summary available",
                formatPatchedFiles(patchedFiles),
                validationResults != null ? validationResults : "No validation results available"
        );
    }
    
    /**
     * Generates HTML email content for failure notifications.
     */
    public String generateFailureContent(Build build, String errorMessage, String planSummary) {
        String shortSha = build.getCommitSha().substring(0, 7);
        
        return String.format(FAILURE_TEMPLATE,
                build.getJob(),
                build.getBuildNumber(),
                build.getBranch(),
                shortSha,
                build.getCommitSha(),
                build.getCreatedAt().format(DATE_FORMATTER),
                errorMessage != null ? errorMessage : "Unknown error occurred",
                planSummary != null ? planSummary : "No plan was generated"
        );
    }
    
    /**
     * Generates HTML email content for manual intervention notifications.
     */
    public String generateManualInterventionContent(Build build, String errorMessage, String planSummary) {
        String shortSha = build.getCommitSha().substring(0, 7);
        
        return String.format(MANUAL_INTERVENTION_TEMPLATE,
                build.getJob(),
                build.getBuildNumber(),
                build.getBranch(),
                shortSha,
                build.getCommitSha(),
                build.getCreatedAt().format(DATE_FORMATTER),
                errorMessage != null ? errorMessage : "Automated fix attempts exhausted",
                planSummary != null ? planSummary : "No plan was generated"
        );
    }
    
    private String formatPatchedFiles(List<String> patchedFiles) {
        if (patchedFiles == null || patchedFiles.isEmpty()) {
            return "<li>No files were patched</li>";
        }
        
        StringBuilder sb = new StringBuilder();
        for (String file : patchedFiles) {
            sb.append("<li><code>").append(file).append("</code></li>");
        }
        return sb.toString();
    }
    
    private static final String SUCCESS_TEMPLATE = 
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>CI Fix Success</title>\n" +
            "    <style>\n" +
            "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
            "        .header { background-color: #28a745; color: white; padding: 15px; border-radius: 5px; }\n" +
            "        .content { background-color: #f8f9fa; padding: 20px; border-radius: 5px; margin: 10px 0; }\n" +
            "        .build-info { background-color: #e9ecef; padding: 15px; border-radius: 5px; margin: 10px 0; }\n" +
            "        .pr-link { background-color: #007bff; color: white; padding: 10px 15px; text-decoration: none; border-radius: 5px; display: inline-block; }\n" +
            "        .files-list { background-color: #fff; padding: 15px; border-left: 4px solid #28a745; }\n" +
            "        code { background-color: #f1f3f4; padding: 2px 4px; border-radius: 3px; font-family: monospace; }\n" +
            "        ul { padding-left: 20px; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"header\">\n" +
            "            <h2>✅ CI Build Fixed Successfully</h2>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"build-info\">\n" +
            "            <h3>Build Information</h3>\n" +
            "            <ul>\n" +
            "                <li><strong>Job:</strong> %s</li>\n" +
            "                <li><strong>Build Number:</strong> #%d</li>\n" +
            "                <li><strong>Branch:</strong> %s</li>\n" +
            "                <li><strong>Commit:</strong> %s (%s)</li>\n" +
            "                <li><strong>Build Time:</strong> %s</li>\n" +
            "            </ul>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"content\">\n" +
            "            <h3>🎉 Good News!</h3>\n" +
            "            <p>The automated CI fixer has successfully resolved the build failure and created a pull request for review.</p>\n" +
            "            \n" +
            "            <p><strong>Pull Request:</strong> <a href=\"%s\" class=\"pr-link\">PR #%d - Review Changes</a></p>\n" +
            "            \n" +
            "            <h4>Fix Summary</h4>\n" +
            "            <div class=\"files-list\">\n" +
            "                <p>%s</p>\n" +
            "            </div>\n" +
            "            \n" +
            "            <h4>Files Modified</h4>\n" +
            "            <div class=\"files-list\">\n" +
            "                <ul>%s</ul>\n" +
            "            </div>\n" +
            "            \n" +
            "            <h4>Validation Results</h4>\n" +
            "            <div class=\"files-list\">\n" +
            "                <pre>%s</pre>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"content\">\n" +
            "            <h4>Next Steps</h4>\n" +
            "            <ol>\n" +
            "                <li>Review the pull request changes</li>\n" +
            "                <li>Run additional tests if needed</li>\n" +
            "                <li>Merge the PR if the fix looks good</li>\n" +
            "                <li>Monitor the build to ensure stability</li>\n" +
            "            </ol>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";
    
    private static final String FAILURE_TEMPLATE = 
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>CI Fix Failed</title>\n" +
            "    <style>\n" +
            "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
            "        .header { background-color: #dc3545; color: white; padding: 15px; border-radius: 5px; }\n" +
            "        .content { background-color: #f8f9fa; padding: 20px; border-radius: 5px; margin: 10px 0; }\n" +
            "        .build-info { background-color: #e9ecef; padding: 15px; border-radius: 5px; margin: 10px 0; }\n" +
            "        .error-info { background-color: #fff; padding: 15px; border-left: 4px solid #dc3545; }\n" +
            "        code { background-color: #f1f3f4; padding: 2px 4px; border-radius: 3px; font-family: monospace; }\n" +
            "        ul { padding-left: 20px; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"header\">\n" +
            "            <h2>❌ CI Build Fix Failed</h2>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"build-info\">\n" +
            "            <h3>Build Information</h3>\n" +
            "            <ul>\n" +
            "                <li><strong>Job:</strong> %s</li>\n" +
            "                <li><strong>Build Number:</strong> #%d</li>\n" +
            "                <li><strong>Branch:</strong> %s</li>\n" +
            "                <li><strong>Commit:</strong> %s (%s)</li>\n" +
            "                <li><strong>Build Time:</strong> %s</li>\n" +
            "            </ul>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"content\">\n" +
            "            <h3>⚠️ Automated Fix Failed</h3>\n" +
            "            <p>The automated CI fixer was unable to resolve the build failure. Manual intervention may be required.</p>\n" +
            "            \n" +
            "            <h4>Error Details</h4>\n" +
            "            <div class=\"error-info\">\n" +
            "                <pre>%s</pre>\n" +
            "            </div>\n" +
            "            \n" +
            "            <h4>Analysis Summary</h4>\n" +
            "            <div class=\"error-info\">\n" +
            "                <p>%s</p>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"content\">\n" +
            "            <h4>Recommended Actions</h4>\n" +
            "            <ol>\n" +
            "                <li>Review the build logs manually</li>\n" +
            "                <li>Check for complex dependency issues</li>\n" +
            "                <li>Verify Spring configuration problems</li>\n" +
            "                <li>Consider updating the CI fixer rules</li>\n" +
            "            </ol>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";
    
    private static final String MANUAL_INTERVENTION_TEMPLATE = 
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>CI Fix - Manual Intervention Required</title>\n" +
            "    <style>\n" +
            "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n" +
            "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
            "        .header { background-color: #ffc107; color: #212529; padding: 15px; border-radius: 5px; }\n" +
            "        .content { background-color: #f8f9fa; padding: 20px; border-radius: 5px; margin: 10px 0; }\n" +
            "        .build-info { background-color: #e9ecef; padding: 15px; border-radius: 5px; margin: 10px 0; }\n" +
            "        .warning-info { background-color: #fff; padding: 15px; border-left: 4px solid #ffc107; }\n" +
            "        code { background-color: #f1f3f4; padding: 2px 4px; border-radius: 3px; font-family: monospace; }\n" +
            "        ul { padding-left: 20px; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"header\">\n" +
            "            <h2>⚠️ Manual Intervention Required</h2>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"build-info\">\n" +
            "            <h3>Build Information</h3>\n" +
            "            <ul>\n" +
            "                <li><strong>Job:</strong> %s</li>\n" +
            "                <li><strong>Build Number:</strong> #%d</li>\n" +
            "                <li><strong>Branch:</strong> %s</li>\n" +
            "                <li><strong>Commit:</strong> %s (%s)</li>\n" +
            "                <li><strong>Build Time:</strong> %s</li>\n" +
            "            </ul>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"content\">\n" +
            "            <h3>🔧 Manual Review Needed</h3>\n" +
            "            <p>The automated CI fixer has exhausted all retry attempts and requires manual intervention to resolve the build failure.</p>\n" +
            "            \n" +
            "            <h4>Issue Details</h4>\n" +
            "            <div class=\"warning-info\">\n" +
            "                <pre>%s</pre>\n" +
            "            </div>\n" +
            "            \n" +
            "            <h4>Analysis Summary</h4>\n" +
            "            <div class=\"warning-info\">\n" +
            "                <p>%s</p>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "        \n" +
            "        <div class=\"content\">\n" +
            "            <h4>Next Steps</h4>\n" +
            "            <ol>\n" +
            "                <li>Investigate the build failure manually</li>\n" +
            "                <li>Check for complex architectural issues</li>\n" +
            "                <li>Review Spring Boot configuration</li>\n" +
            "                <li>Update dependencies if needed</li>\n" +
            "                <li>Consider improving the automated fix rules</li>\n" +
            "            </ol>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";
}