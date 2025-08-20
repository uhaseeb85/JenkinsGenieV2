package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Chat message for OpenAI-compatible APIs.
 */
public class ChatMessage {
    
    @JsonProperty("role")
    private String role; // "system", "user", "assistant"
    
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("name")
    private String name;
    
    public ChatMessage() {}
    
    public ChatMessage(String role, String content) {
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}