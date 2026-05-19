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

    // Keywords that indicate the user wants to execute a command
    private static final List<String> COMMAND_KEYWORDS = List.of("ls", "list", "file", "目录", "文件", "exec", "run", "command", "命令");

    // Tool names that semantically match "execute command" functionality
    private static final List<String> COMMAND_TOOL_PATTERNS = List.of(
        "bash", "shell", "exec", "run", "command", "cmd", "terminal", "sh",
        "execute", "system", "process", "spawn", "调用", "执行"
    );

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
                            // Find the best matching tool for command execution from request.tools
                            Tool matchedTool = findBestCommandTool(request);
                            boolean shouldTriggerToolCall = matchedTool != null && isCommandRequest(request);
                            
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
                                if (!streamToolCall(matchedTool)) return;
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

                /**
                 * Check if the user's last message indicates a command execution intent.
                 */
                private boolean isCommandRequest(ChatCompletionRequest request) {
                    if (request.getMessages() == null || request.getMessages().isEmpty()) return false;
                    String lastContent = request.getMessages().get(request.getMessages().size() - 1).getContent();
                    if (lastContent == null) return false;
                    String lower = lastContent.toLowerCase();
                    return COMMAND_KEYWORDS.stream().anyMatch(lower::contains);
                }

                /**
                 * Find the best matching tool from request.tools that semantically represents
                 * a "command execution" capability.
                 * Returns null if no suitable tool is found.
                 */
                private Tool findBestCommandTool(ChatCompletionRequest request) {
                    if (request.getTools() == null || request.getTools().isEmpty()) return null;
                    
                    Tool bestMatch = null;
                    int bestScore = -1;
                    
                    for (Tool tool : request.getTools()) {
                        if (tool == null || tool.getFunction() == null) continue;
                        
                        String toolName = tool.getFunction().getName();
                        String toolDescription = tool.getFunction().getDescription();
                        
                        if (toolName == null) continue;
                        
                        int score = calculateCommandToolScore(toolName, toolDescription);
                        
                        if (score > bestScore) {
                            bestScore = score;
                            bestMatch = tool;
                        }
                    }
                    
                    // Only return if we have a reasonable match (score > 0)
                    return bestScore > 0 ? bestMatch : null;
                }
                
                /**
                 * Calculate how well a tool matches "command execution" semantics.
                 * Score is based on name and description matching known command patterns.
                 */
                private int calculateCommandToolScore(String toolName, String toolDescription) {
                    int score = 0;
                    String nameLower = toolName.toLowerCase();
                    String descLower = toolDescription != null ? toolDescription.toLowerCase() : "";
                    
                    // Check tool name against command patterns
                    for (String pattern : COMMAND_TOOL_PATTERNS) {
                        if (nameLower.contains(pattern)) {
                            score += 10; // Strong match in name
                        }
                    }
                    
                    // Check tool description for command-related keywords
                    String[] descKeywords = {"command", "shell", "bash", "execute", "run", "terminal", "system", "exec", "process"};
                    for (String keyword : descKeywords) {
                        if (descLower.contains(keyword)) {
                            score += 3; // Weaker match in description
                        }
                    }
                    
                    // Bonus for exact matches of well-known command tools
                    if (nameLower.equals("bash") || nameLower.equals("shell") || nameLower.equals("exec")) {
                        score += 20;
                    }
                    
                    return score;
                }

                private boolean streamToolCall(Tool tool) throws JsonProcessingException, InterruptedException {
                    String toolCallId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                    String toolName = tool.getFunction().getName();
                    
                    // Build arguments based on the tool's expected parameters
                    // For command execution tools, we typically need a "command" parameter
                    String toolArguments = buildToolArguments(tool);
                    
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
                
                /**
                 * Build tool arguments based on the tool's parameter schema.
                 * Only includes parameters defined in the schema with correctly typed values.
                 */
                @SuppressWarnings("unchecked")
                private String buildToolArguments(Tool tool) {
                    Object parameters = tool.getFunction().getParameters();
                    Map<String, Object> properties = new LinkedHashMap<>();
                    List<String> requiredParams = new ArrayList<>();
                    
                    if (parameters instanceof Map) {
                        Map<String, Object> paramMap = (Map<String, Object>) parameters;
                        Object props = paramMap.get("properties");
                        if (props instanceof Map) {
                            properties = (Map<String, Object>) props;
                        }
                        Object req = paramMap.get("required");
                        if (req instanceof List) {
                            requiredParams = (List<String>) req;
                        }
                    }
                    
                    // Build JSON arguments respecting parameter types
                    Map<String, Object> args = new LinkedHashMap<>();
                    
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        String paramName = entry.getKey();
                        Map<String, Object> paramSchema = entry.getValue() instanceof Map 
                            ? (Map<String, Object>) entry.getValue() 
                            : new HashMap<>();
                        String paramType = paramSchema.get("type") != null ? paramSchema.get("type").toString() : "string";
                        
                        // Determine value based on parameter name and type
                        Object value = getParameterValue(paramName, paramType);
                        if (value != null) {
                            args.put(paramName, value);
                        }
                    }
                    
                    try {
                        return objectMapper.writeValueAsString(args);
                    } catch (JsonProcessingException e) {
                        // Fallback to simple JSON
                        return "{\"command\": \"ls -l\"}";
                    }
                }
                
                /**
                 * Get a properly typed value for a parameter based on its name and schema type.
                 */
                private Object getParameterValue(String paramName, String paramType) {
                    String nameLower = paramName.toLowerCase();
                    
                    // Command parameter - always string
                    if (nameLower.contains("command") || nameLower.contains("cmd") || 
                        nameLower.contains("shell") || nameLower.contains("script") ||
                        nameLower.contains("input") || nameLower.contains("code") ||
                        nameLower.contains("line") || nameLower.contains("query")) {
                        return "ls -l";
                    }
                    
                    // Timeout parameter - must be a number
                    if (nameLower.contains("timeout") || nameLower.contains("time") ||
                        nameLower.contains("limit") || nameLower.contains("duration")) {
                        if ("integer".equals(paramType) || "number".equals(paramType)) {
                            return 120; // 120 seconds default timeout
                        }
                        return null; // Skip if type doesn't match
                    }
                    
                    // Description parameter - string
                    if (nameLower.contains("desc") || nameLower.contains("explain") ||
                        nameLower.contains("reason") || nameLower.contains("purpose")) {
                        return "List files in current directory";
                    }
                    
                    // Boolean parameters
                    if ("boolean".equals(paramType)) {
                        return false;
                    }
                    
                    // Array parameters
                    if ("array".equals(paramType)) {
                        return new ArrayList<>();
                    }
                    
                    // Object parameters
                    if ("object".equals(paramType)) {
                        return new HashMap<>();
                    }
                    
                    // For string parameters that look like paths/directories
                    if (nameLower.contains("dir") || nameLower.contains("path") || 
                        nameLower.contains("workdir") || nameLower.contains("cwd") ||
                        nameLower.contains("folder") || nameLower.contains("location")) {
                        return "/tmp";
                    }
                    
                    // Default: null to skip unknown parameters (safer than guessing)
                    return null;
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
