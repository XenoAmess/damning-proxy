package com.xenoamess.badass_model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.badass_model.dto.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Flow;

@ApplicationScoped
public class MockLlmService {

    @Inject
    ObjectMapper objectMapper;

    private static final String[] MOCK_RESPONSES = {
        "Hello! I'm a mock AI assistant running on Quarkus + GraalVM native.",
        "I can help you with various tasks, though I'm just a demo implementation.",
        "This server implements the OpenAI-compatible chat completions API.",
        "You can stream responses, use tools, and authenticate with Bearer tokens.",
        "Built with Java 21, Quarkus 3.15, and ready for GraalVM native compilation!"
    };

    public Flow.Publisher<String> streamChatCompletion(ChatCompletionRequest request) {
        return subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                private volatile boolean cancelled = false;
                private volatile boolean completed = false;
                private final Random random = new Random();
                private final String requestId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
                private final long created = Instant.now().getEpochSecond();
                private final String model = request.getModel() != null ? request.getModel() : "mock-model";
                private int tokenCount = 0;

                @Override
                public void request(long n) {
                    if (cancelled || completed) return;
                    // Process in a virtual thread
                    Thread.startVirtualThread(() -> {
                        try {
                            // Send role delta
                            if (!cancelled && !completed) {
                                ChatCompletionChunk roleChunk = new ChatCompletionChunk(
                                    requestId, "chat.completion.chunk", created, model,
                                    List.of(new ChatCompletionChunk.Choice(0,
                                        new ChatCompletionChunk.Delta("assistant", null, null), null)),
                                    null
                                );
                                if (!sendEvent(roleChunk)) return;
                            }

                            // Send content chunks word by word
                            String response = selectResponse(request);
                            String[] words = response.split(" ");

                            for (String word : words) {
                                if (cancelled || completed) break;
                                String content = word + " ";
                                tokenCount += estimateTokens(content);

                                ChatCompletionChunk chunk = new ChatCompletionChunk(
                                    requestId, "chat.completion.chunk", created, model,
                                    List.of(new ChatCompletionChunk.Choice(0,
                                        new ChatCompletionChunk.Delta(null, content, null), null)),
                                    null
                                );
                                if (!sendEvent(chunk)) return;

                                // Simulate network latency
                                Thread.sleep(random.nextInt(20, 80));
                            }

                            // Send finish reason
                            if (!cancelled && !completed) {
                                ChatCompletionChunk finishChunk = new ChatCompletionChunk(
                                    requestId, "chat.completion.chunk", created, model,
                                    List.of(new ChatCompletionChunk.Choice(0,
                                        new ChatCompletionChunk.Delta(null, null, null), "stop")),
                                    null
                                );
                                if (!sendEvent(finishChunk)) return;
                            }

                            // Send usage if requested
                            if (!cancelled && !completed
                                && request.getStreamOptions() != null
                                && Boolean.TRUE.equals(request.getStreamOptions().getIncludeUsage())) {
                                int promptTokens = estimatePromptTokens(request);
                                Usage usage = new Usage(promptTokens, tokenCount, promptTokens + tokenCount);
                                ChatCompletionChunk usageChunk = new ChatCompletionChunk(
                                    requestId, "chat.completion.chunk", created, model,
                                    List.of(),
                                    usage
                                );
                                if (!sendEvent(usageChunk)) return;
                            }

                            if (!completed) {
                                completed = true;
                                subscriber.onComplete();
                            }
                        } catch (Exception e) {
                            if (!completed) {
                                Log.error("Error streaming response", e);
                                subscriber.onError(e);
                            }
                        }
                    });
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }

                /**
                 * Sends an event to the subscriber. Returns false if the subscriber was cancelled
                 * or the send failed (e.g. connection closed), indicating the caller should stop sending.
                 */
                private boolean sendEvent(ChatCompletionChunk chunk) throws JsonProcessingException {
                    if (cancelled || completed) {
                        return false;
                    }
                    try {
                        String json = objectMapper.writeValueAsString(chunk);
                        subscriber.onNext("data: " + json + "\n\n");
                        return true;
                    } catch (IllegalStateException e) {
                        // Connection already closed — mark as completed to prevent further errors
                        completed = true;
                        return false;
                    }
                }

                private String selectResponse(ChatCompletionRequest request) {
                    // Simple logic: pick based on message content keywords
                    String content = "";
                    if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                        ChatMessage lastMessage = request.getMessages().get(request.getMessages().size() - 1);
                        content = lastMessage.getContent() != null ? lastMessage.getContent().toLowerCase() : "";
                    }

                    if (content.contains("tool") || content.contains("function")) {
                        return "I can use tools! Though this mock implementation doesn't actually execute them. In a real implementation, you would define tools in your request and I'd stream back tool_calls deltas.";
                    }
                    if (content.contains("native") || content.contains("graal")) {
                        return "GraalVM native image compilation produces fast-starting, low-memory binaries. This Quarkus app is designed to compile natively!";
                    }
                    if (content.contains("hello") || content.contains("hi")) {
                        return "Hello there! Welcome to the badass-model OpenAI-compatible server. I'm ready to help!";
                    }

                    return MOCK_RESPONSES[random.nextInt(MOCK_RESPONSES.length)];
                }

                private int estimateTokens(String text) {
                    // Very rough estimation: ~4 chars per token
                    return Math.max(1, text.length() / 4);
                }

                private int estimatePromptTokens(ChatCompletionRequest request) {
                    int total = 0;
                    if (request.getMessages() != null) {
                        for (ChatMessage msg : request.getMessages()) {
                            if (msg.getContent() != null) {
                                total += msg.getContent().length() / 4;
                            }
                        }
                    }
                    return Math.max(1, total);
                }
            });
        };
    }

    public ChatCompletionChunk createNonStreamingResponse(ChatCompletionRequest request) {
        String requestId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        long created = Instant.now().getEpochSecond();
        String model = request.getModel() != null ? request.getModel() : "mock-model";

        String response = "This is a non-streaming mock response. Set stream=true for SSE streaming.";

        return new ChatCompletionChunk(
            requestId, "chat.completion", created, model,
            List.of(new ChatCompletionChunk.Choice(0,
                new ChatCompletionChunk.Delta(null, response, null), "stop")),
            null
        );
    }
}
