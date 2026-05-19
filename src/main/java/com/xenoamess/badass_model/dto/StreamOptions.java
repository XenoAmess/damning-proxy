package com.xenoamess.badass_model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamOptions {

    @JsonProperty("include_usage")
    private Boolean includeUsage;

    public StreamOptions() {
    }

    public StreamOptions(Boolean includeUsage) {
        this.includeUsage = includeUsage;
    }

    public Boolean getIncludeUsage() {
        return includeUsage;
    }

    public void setIncludeUsage(Boolean includeUsage) {
        this.includeUsage = includeUsage;
    }
}
