package com.xenoamess.badass_model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionChunk {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    public ChatCompletionChunk() {
    }

    public ChatCompletionChunk(String id, String object, Long created, String model, List<Choice> choices, Usage usage) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
    }

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

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {
        private Integer index;
        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;

        public Choice() {
        }

        public Choice(Integer index, Delta delta, String finishReason) {
            this.index = index;
            this.delta = delta;
            this.finishReason = finishReason;
        }

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public Delta getDelta() {
            return delta;
        }

        public void setDelta(Delta delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        private String role;
        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCallDelta> toolCalls;

        public Delta() {
        }

        public Delta(String role, String content, List<ToolCallDelta> toolCalls) {
            this.role = role;
            this.content = content;
            this.toolCalls = toolCalls;
        }

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

        public List<ToolCallDelta> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCallDelta> toolCalls) {
            this.toolCalls = toolCalls;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallDelta {
        private Integer index;
        private String id;
        private String type;
        private FunctionDelta function;

        public ToolCallDelta() {
        }

        public ToolCallDelta(Integer index, String id, String type, FunctionDelta function) {
            this.index = index;
            this.id = id;
            this.type = type;
            this.function = function;
        }

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public FunctionDelta getFunction() {
            return function;
        }

        public void setFunction(FunctionDelta function) {
            this.function = function;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionDelta {
        private String name;
        private String arguments;

        public FunctionDelta() {
        }

        public FunctionDelta(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }
}
