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
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class MockLlmService {

    @Inject
    ObjectMapper objectMapper;

    private static final String MOCK_RESPONSE = "Hello! I am a mock AI assistant. I can help you with various tasks, though I am just a demo implementation running on Quarkus and GraalVM native.";

    public Flow.Publisher<String> streamChatCompletion(ChatCompletionRequest request) {
        return subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                private volatile boolean cancelled = false;
                private volatile boolean completed = false;
                private final AtomicBoolean started = new AtomicBoolean(false);
                private final String requestId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
                private final long created = Instant.now().getEpochSecond();
                private final String model = request.getModel() != null ? request.getModel() : "mock-model";
                private int tokenCount = 0;

                @Override
                public void request(long n) {
                    if (cancelled || completed) return;
                    // Only start streaming once, regardless of how many times request() is called
                    if (!started.compareAndSet(false, true)) return;
                    
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

                                // Small delay for streaming effect
                                Thread.sleep(10);
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

                private boolean sendEvent(ChatCompletionChunk chunk) throws JsonProcessingException {
                    if (cancelled || completed) {
                        return false;
                    }
                    try {
                        String json = objectMapper.writeValueAsString(chunk);
                        // Prefix with space so Quarkus produces "data: {...}" instead of "data:{...}"
                        subscriber.onNext(" " + json);
                        return true;
                    } catch (IllegalStateException e) {
                        completed = true;
                        return false;
                    }
                }

                private String selectResponse(ChatCompletionRequest request) {
                    return MOCK_RESPONSE;
                }

                private int estimateTokens(String text) {
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

        return new ChatCompletionChunk(
            requestId, "chat.completion", created, model,
            List.of(new ChatCompletionChunk.Choice(0,
                new ChatCompletionChunk.Delta(null, MOCK_RESPONSE, null), "stop")),
            null
        );
    }
}
