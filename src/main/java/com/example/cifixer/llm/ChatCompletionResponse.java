package com.example.cifixer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI-compatible chat completion response.
 */
public class ChatCompletionResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("created")
    private Long created;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("choices")
    private ChatChoice[] choices;
    
    @JsonProperty("usage")
    private TokenUsage usage;
    
    @JsonProperty("error")
    private ApiError error;
    
    public ChatCompletionResponse() {}
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getObject() {
        return object;
    }
    
    public void setObject(String object) {
        this.object = object;
    }
    
    public Long getCreated() {
        return created;
    }
    
    public void setCreated(Long created) {
        this.created = created;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public ChatChoice[] getChoices() {
        return choices;
    }
    
    public void setChoices(ChatChoice[] choices) {
        this.choices = choices;
    }
    
    public TokenUsage getUsage() {
        return usage;
    }
    
    public void setUsage(TokenUsage usage) {
        this.usage = usage;
    }
    
    public ApiError getError() {
        return error;
    }
    
    public void setError(ApiError error) {
        this.error = error;
    }
    
    public static class ChatChoice {
        @JsonProperty("index")
        private Integer index;
        
        @JsonProperty("message")
        private ChatMessage message;
        
        @JsonProperty("finish_reason")
        private String finishReason;
        
        public Integer getIndex() {
            return index;
        }
        
        public void setIndex(Integer index) {
            this.index = index;
        }
        
        public ChatMessage getMessage() {
            return message;
        }
        
        public void setMessage(ChatMessage message) {
            this.message = message;
        }
        
        public String getFinishReason() {
            return finishReason;
        }
        
        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }
    
    public static class TokenUsage {
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
    
    public static class ApiError {
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("code")
        private String code;
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
    }
}