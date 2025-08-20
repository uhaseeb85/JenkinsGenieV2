package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic messages API response.
 */
public class AnthropicResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("content")
    private ContentBlock[] content;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("stop_reason")
    private String stopReason;
    
    @JsonProperty("stop_sequence")
    private String stopSequence;
    
    @JsonProperty("usage")
    private AnthropicUsage usage;
    
    @JsonProperty("error")
    private AnthropicError error;
    
    public AnthropicResponse() {}
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public ContentBlock[] getContent() {
        return content;
    }
    
    public void setContent(ContentBlock[] content) {
        this.content = content;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getStopReason() {
        return stopReason;
    }
    
    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }
    
    public String getStopSequence() {
        return stopSequence;
    }
    
    public void setStopSequence(String stopSequence) {
        this.stopSequence = stopSequence;
    }
    
    public AnthropicUsage getUsage() {
        return usage;
    }
    
    public void setUsage(AnthropicUsage usage) {
        this.usage = usage;
    }
    
    public AnthropicError getError() {
        return error;
    }
    
    public void setError(AnthropicError error) {
        this.error = error;
    }
    
    public static class ContentBlock {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("text")
        private String text;
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
    }
    
    public static class AnthropicUsage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
        
        @JsonProperty("output_tokens")
        private Integer outputTokens;
        
        public Integer getInputTokens() {
            return inputTokens;
        }
        
        public void setInputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
        }
        
        public Integer getOutputTokens() {
            return outputTokens;
        }
        
        public void setOutputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
        }
    }
    
    public static class AnthropicError {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("message")
        private String message;
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}