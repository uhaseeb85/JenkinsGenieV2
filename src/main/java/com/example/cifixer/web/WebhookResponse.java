package com.example.cifixer.web;

/**
 * Response object for webhook endpoints.
 */
public class WebhookResponse {
    
    private Long buildId;
    private String status;
    private String message;
    
    public WebhookResponse() {
    }
    
    public WebhookResponse(Long buildId, String status, String message) {
        this.buildId = buildId;
        this.status = status;
        this.message = message;
    }
    
    public static WebhookResponse success(Long buildId) {
        return new WebhookResponse(buildId, "success", "Build failure received and queued for processing");
    }
    
    public static WebhookResponse error(String message) {
        return new WebhookResponse(null, "error", message);
    }
    
    public Long getBuildId() {
        return buildId;
    }
    
    public void setBuildId(Long buildId) {
        this.buildId = buildId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}