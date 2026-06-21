package com.xenoamess.damning_proxy.dto;

/**
 * One message in a chat-style conversation. Used by {@link TrafficLogFriendlyDto}
 * to expose every entry in the request {@code messages} array and every choice
 * in the response {@code choices} array, so the UI can render the full
 * conversation rather than just the first user/assistant turn.
 *
 * Content may be {@code null} when the upstream emitted only tool calls or
 * the streaming response produced no textual content.
 */
public class ChatMessage {

    public String role;

    /** Plain text content (null if the message only carries tool calls / image parts). */
    public String content;

    /**
     * Name of the assistant speaker if the upstream populated one (e.g.
     * {@code "MiniMax AI"}). Null for non-assistant messages or when absent.
     */
    public String name;

    /**
     * Reasoning/thinking content emitted by some upstream models (e.g. MiniMax).
     * Null when not present.
     */
    public String reasoningContent;

    /**
     * Tool call IDs produced by the assistant, surfaced verbatim so the UI can
     * show what tools the upstream invoked. Null for non-assistant messages.
     */
    public java.util.List<String> toolCallIds;

    /**
     * Tool call function names, parallel to {@link #toolCallIds}.
     * Null for non-assistant messages.
     */
    public java.util.List<String> toolCallFunctions;

    /**
     * Tool call function arguments (JSON strings), parallel to {@link #toolCallIds}.
     * Null for non-assistant messages.
     */
    public java.util.List<String> toolCallArguments;

    /**
     * The {@code tool_call_id} from a tool-result message (role {@code "tool"}).
     * Null for non-tool messages.
     */
    public String toolResultCallId;

    /**
     * Raw text length (for very long messages, the UI may want to collapse
     * the bubble and offer an expand toggle).
     */
    public Integer contentLength;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}