package com.example.cifixer.github;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request object for creating a GitHub pull request.
 */
public class GitHubCreatePullRequestRequest {
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("body")
    private String body;
    
    @JsonProperty("head")
    private String head;
    
    @JsonProperty("base")
    private String base;
    
    @JsonProperty("draft")
    private boolean draft = false;
    
    public GitHubCreatePullRequestRequest() {
    }
    
    public GitHubCreatePullRequestRequest(String title, String body, String head, String base) {
        this.title = title;
        this.body = body;
        this.head = head;
        this.base = base;
    }
    
    // Getters and Setters
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public String getHead() {
        return head;
    }
    
    public void setHead(String head) {
        this.head = head;
    }
    
    public String getBase() {
        return base;
    }
    
    public void setBase(String base) {
        this.base = base;
    }
    
    public boolean isDraft() {
        return draft;
    }
    
    public void setDraft(boolean draft) {
        this.draft = draft;
    }
    
    @Override
    public String toString() {
        return "GitHubCreatePullRequestRequest{" +
                "title='" + title + '\'' +
                ", head='" + head + '\'' +
                ", base='" + base + '\'' +
                ", draft=" + draft +
                '}';
    }
}