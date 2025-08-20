package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic message for messages API.
 */
public class AnthropicMessage {
    
    @JsonProperty("role")
    private String role; // "user" or "assistant"
    
    @JsonProperty("content")
    private String content;
    
    public AnthropicMessage() {}
    
    public AnthropicMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
    
    // Getters and setters
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
}