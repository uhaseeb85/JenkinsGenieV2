package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for LLM API calls.
 */
public class LlmRequest {
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("prompt")
    private String prompt;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("stop")
    private String[] stopSequences;
    
    public LlmRequest() {}
    
    public LlmRequest(String model, String prompt, Integer maxTokens, Double temperature) {
        this.model = model;
        this.prompt = prompt;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }
    
    // Getters and setters
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public Integer getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public Double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
    
    public String[] getStopSequences() {
        return stopSequences;
    }
    
    public void setStopSequences(String[] stopSequences) {
        this.stopSequences = stopSequences;
    }
}