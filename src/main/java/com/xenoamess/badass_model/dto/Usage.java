package com.xenoamess.badass_model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Usage {

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;

    @JsonProperty("prompt_tokens_details")
    private TokenDetails promptTokensDetails;

    @JsonProperty("completion_tokens_details")
    private TokenDetails completionTokensDetails;

    public Usage() {
    }

    public Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

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

    public TokenDetails getPromptTokensDetails() {
        return promptTokensDetails;
    }

    public void setPromptTokensDetails(TokenDetails promptTokensDetails) {
        this.promptTokensDetails = promptTokensDetails;
    }

    public TokenDetails getCompletionTokensDetails() {
        return completionTokensDetails;
    }

    public void setCompletionTokensDetails(TokenDetails completionTokensDetails) {
        this.completionTokensDetails = completionTokensDetails;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokenDetails {
        @JsonProperty("cached_tokens")
        private Integer cachedTokens;

        @JsonProperty("reasoning_tokens")
        private Integer reasoningTokens;

        public TokenDetails() {
        }

        public TokenDetails(Integer cachedTokens, Integer reasoningTokens) {
            this.cachedTokens = cachedTokens;
            this.reasoningTokens = reasoningTokens;
        }

        public Integer getCachedTokens() {
            return cachedTokens;
        }

        public void setCachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer getReasoningTokens() {
            return reasoningTokens;
        }

        public void setReasoningTokens(Integer reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
        }
    }
}
