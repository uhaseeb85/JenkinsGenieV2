package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI-compatible chat completion request.
 */
public class ChatCompletionRequest {
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("messages")
    private ChatMessage[] messages;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("top_p")
    private Double topP;
    
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    
    @JsonProperty("presence_penalty")
    private Double presencePenalty;
    
    @JsonProperty("stop")
    private String[] stop;
    
    public ChatCompletionRequest() {}
    
    // Getters and setters
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public ChatMessage[] getMessages() {
        return messages;
    }
    
    public void setMessages(ChatMessage[] messages) {
        this.messages = messages;
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
    
    public Double getTopP() {
        return topP;
    }
    
    public void setTopP(Double topP) {
        this.topP = topP;
    }
    
    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }
    
    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }
    
    public Double getPresencePenalty() {
        return presencePenalty;
    }
    
    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }
    
    public String[] getStop() {
        return stop;
    }
    
    public void setStop(String[] stop) {
        this.stop = stop;
    }
}