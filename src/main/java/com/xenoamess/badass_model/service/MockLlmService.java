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
                            // Check if we should trigger a tool call
                            boolean shouldTriggerToolCall = shouldTriggerToolCall(request);
                            
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

                            if (shouldTriggerToolCall) {
                                // Stream a tool call instead of text
                                if (!streamToolCall()) return;
                            } else {
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
                            }

                            // Send finish reason
                            if (!cancelled && !completed) {
                                String finishReason = shouldTriggerToolCall ? "tool_calls" : "stop";
                                ChatCompletionChunk finishChunk = new ChatCompletionChunk(
                                    requestId, "chat.completion.chunk", created, model,
                                    List.of(new ChatCompletionChunk.Choice(0,
                                        new ChatCompletionChunk.Delta(null, null, null), finishReason)),
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

                private boolean shouldTriggerToolCall(ChatCompletionRequest request) {
                    if (request.getMessages() == null || request.getMessages().isEmpty()) return false;
                    String lastContent = request.getMessages().get(request.getMessages().size() - 1).getContent();
                    if (lastContent == null) return false;
                    String lower = lastContent.toLowerCase();
                    // Trigger tool call when user asks about files/directories
                    return lower.contains("ls") || lower.contains("list") || lower.contains("file") || lower.contains("目录") || lower.contains("文件");
                }

                private boolean streamToolCall() throws JsonProcessingException, InterruptedException {
                    String toolCallId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                    // OpenCode only supports these tools: bash, edit, glob, grep, invalid, question, read, skill, task, todowrite, webfetch, write
                    String toolName = "bash";
                    String toolArguments = "{\"command\": \"ls -l\", \"description\": \"List files in current directory\"}";
                    
                    // Stream tool_call delta with id and type
                    ChatCompletionChunk.ToolCallDelta toolCallDelta = new ChatCompletionChunk.ToolCallDelta();
                    toolCallDelta.setIndex(0);
                    toolCallDelta.setId(toolCallId);
                    toolCallDelta.setType("function");
                    
                    ChatCompletionChunk.FunctionDelta functionDelta = new ChatCompletionChunk.FunctionDelta();
                    functionDelta.setName(toolName);
                    toolCallDelta.setFunction(functionDelta);
                    
                    ChatCompletionChunk chunk1 = new ChatCompletionChunk(
                        requestId, "chat.completion.chunk", created, model,
                        List.of(new ChatCompletionChunk.Choice(0,
                            new ChatCompletionChunk.Delta(null, null, List.of(toolCallDelta)), null)),
                        null
                    );
                    if (!sendEvent(chunk1)) return false;
                    Thread.sleep(10);
                    
                    // Stream tool_call delta with arguments (split into chunks to simulate streaming)
                    String[] argChunks = splitIntoChunks(toolArguments, 5);
                    for (String argChunk : argChunks) {
                        if (cancelled || completed) break;
                        
                        ChatCompletionChunk.ToolCallDelta argDelta = new ChatCompletionChunk.ToolCallDelta();
                        argDelta.setIndex(0);
                        
                        ChatCompletionChunk.FunctionDelta funcDelta = new ChatCompletionChunk.FunctionDelta();
                        funcDelta.setArguments(argChunk);
                        argDelta.setFunction(funcDelta);
                        
                        ChatCompletionChunk chunk = new ChatCompletionChunk(
                            requestId, "chat.completion.chunk", created, model,
                            List.of(new ChatCompletionChunk.Choice(0,
                                new ChatCompletionChunk.Delta(null, null, List.of(argDelta)), null)),
                            null
                        );
                        if (!sendEvent(chunk)) return false;
                        Thread.sleep(10);
                    }
                    
                    return true;
                }
                
                private String[] splitIntoChunks(String str, int chunkSize) {
                    List<String> chunks = new ArrayList<>();
                    for (int i = 0; i < str.length(); i += chunkSize) {
                        chunks.add(str.substring(i, Math.min(i + chunkSize, str.length())));
                    }
                    return chunks.toArray(new String[0]);
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
