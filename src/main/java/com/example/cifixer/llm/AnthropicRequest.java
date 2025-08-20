package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic messages API request.
 */
public class AnthropicRequest {
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    @JsonProperty("messages")
    private AnthropicMessage[] messages;
    
    @JsonProperty("system")
    private String system;
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("top_p")
    private Double topP;
    
    @JsonProperty("stop_sequences")
    private String[] stopSequences;
    
    public AnthropicRequest() {}
    
    // Getters and setters
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public Integer getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public AnthropicMessage[] getMessages() {
        return messages;
    }
    
    public void setMessages(AnthropicMessage[] messages) {
        this.messages = messages;
    }
    
    public String getSystem() {
        return system;
    }
    
    public void setSystem(String system) {
        this.system = system;
    }
    
    public Double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
    
    public Double getTopP() {
        return topP;
    }
    
    public void setTopP(Double topP) {
        this.topP = topP;
    }
    
    public String[] getStopSequences() {
        return stopSequences;
    }
    
    public void setStopSequences(String[] stopSequences) {
        this.stopSequences = stopSequences;
    }
}