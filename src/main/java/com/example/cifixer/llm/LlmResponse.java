package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload from LLM API calls.
 */
public class LlmResponse {
    
    @JsonProperty("text")
    private String text;
    
    @JsonProperty("choices")
    private Choice[] choices;
    
    @JsonProperty("usage")
    private Usage usage;
    
    @JsonProperty("error")
    private String error;
    
    public LlmResponse() {}
    
    public Choice[] getChoices() {
        return choices;
    }
    
    public void setChoices(Choice[] choices) {
        this.choices = choices;
    }
    
    public Usage getUsage() {
        return usage;
    }
    
    public void setUsage(Usage usage) {
        this.usage = usage;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getText() {
        // Try direct text field first (for Ollama-style responses)
        if (text != null && !text.trim().isEmpty()) {
            return text;
        }
        
        // Fall back to choices array (for OpenAI-style responses)
        if (choices != null && choices.length > 0) {
            return choices[0].getText();
        }
        
        return null;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public boolean hasError() {
        return error != null && !error.trim().isEmpty();
    }
    
    public static class Choice {
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("finish_reason")
        private String finishReason;
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public String getFinishReason() {
            return finishReason;
        }
        
        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }
    
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        
        @JsonProperty("total_tokens")
        private Integer totalTokens;
        
        public Integer getPromptTokens() {
            return promptTokens;
        }
        
        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }
        
        public Integer getCompletionTokens() {
            return completionTokens;
        }
        
        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }
        
        public Integer getTotalTokens() {
            return totalTokens;
        }
        
        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}