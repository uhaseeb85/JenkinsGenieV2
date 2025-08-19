package com.example.cifixer.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response object from GitHub API when creating a pull request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPullRequestResponse {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("number")
    private Integer number;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("html_url")
    private String htmlUrl;
    
    @JsonProperty("state")
    private String state;
    
    @JsonProperty("draft")
    private boolean draft;
    
    public GitHubPullRequestResponse() {
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Integer getNumber() {
        return number;
    }
    
    public void setNumber(Integer number) {
        this.number = number;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getHtmlUrl() {
        return htmlUrl;
    }
    
    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public boolean isDraft() {
        return draft;
    }
    
    public void setDraft(boolean draft) {
        this.draft = draft;
    }
    
    @Override
    public String toString() {
        return "GitHubPullRequestResponse{" +
                "id=" + id +
                ", number=" + number +
                ", title='" + title + '\'' +
                ", htmlUrl='" + htmlUrl + '\'' +
                ", state='" + state + '\'' +
                ", draft=" + draft +
                '}';
    }
}