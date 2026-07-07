package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import io.quarkus.logging.Log;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@ApplicationScoped
public class UpstreamHttpClient {

    private final Vertx vertx;
    private final WebClient webClient;
    private final io.vertx.core.http.HttpClient streamHttpClient;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CircuitBreaker circuitBreaker;

    @ConfigProperty(name = "damning-proxy.upstream.max-retries", defaultValue = "0")
    int maxRetries;

    @ConfigProperty(name = "damning-proxy.upstream.retry-base-delay-ms", defaultValue = "100")
    long retryBaseDelayMs;

    @ConfigProperty(name = "damning-proxy.upstream.retry-max-delay-ms", defaultValue = "5000")
    long retryMaxDelayMs;

    @ConfigProperty(name = "damning-proxy.upstream.retry-status-codes", defaultValue = "500,502,503,504")
    String retryStatusCodes;

    @ConfigProperty(name = "damning-proxy.upstream.retry-on-timeout", defaultValue = "true")
    boolean retryOnTimeout;

    private Set<Integer> retryableStatusCodes;

    @Inject
    public UpstreamHttpClient(Vertx vertx) {
        this.vertx = vertx;
        // Use Vert.x WebClient over HTTP/1.1: the low-level HttpClient has
        // repeatedly corrupted large non-streaming response bodies when
        // response.body() is consumed from a worker thread.
        this.webClient = WebClient.create(vertx, new WebClientOptions()
            .setProtocolVersion(HttpVersion.HTTP_1_1)
            .setUseAlpn(false)
            .setTryUseCompression(false));
        this.streamHttpClient = vertx.createHttpClient(new io.vertx.core.http.HttpClientOptions()
            .setProtocolVersion(HttpVersion.HTTP_1_1)
            .setUseAlpn(false)
            .setTryUseCompression(false));
    }

    @PostConstruct
    void init() {
        retryableStatusCodes = Arrays.stream(retryStatusCodes.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .collect(Collectors.toSet());
    }

    public UpstreamResponse send(String method, String baseUrl, String path,
                                  MultiMap headers, Object body, int timeoutMs,
                                  ProxyProfile profile) {
        if (!circuitBreaker.allowRequest(baseUrl)) {
            throw new WebApplicationException("Circuit breaker open for upstream: " + baseUrl,
                Response.Status.SERVICE_UNAVAILABLE);
        }

        URI uri = buildUri(baseUrl, path);
        Log.debugf("Upstream request: %s %s", method, uri);

        // Bound the wait so a stuck upstream connection (e.g. HTTP/1.1 keep-alive
        // socket that never closes) cannot pin a worker thread forever.
        int effectiveTimeoutMs = clampTimeoutMs(timeoutMs);

        WebApplicationException lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                long delay = retryDelayMs(attempt - 1);
                Log.debugf("Retrying upstream request after %d ms (attempt %d/%d): %s %s",
                    delay, attempt, maxRetries, method, uri);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            try {
                UpstreamResponse result = sendOnce(method, baseUrl, path, headers, body, effectiveTimeoutMs);
                if (result.statusCode >= 400 && retryableStatusCodes.contains(result.statusCode) && attempt < maxRetries) {
                    lastException = new WebApplicationException(
                        "Upstream returned " + result.statusCode,
                        Response.Status.fromStatusCode(result.statusCode));
                    continue;
                }
                if (result.statusCode < 400) {
                    circuitBreaker.recordSuccess(baseUrl);
                }
                return result;
            } catch (TimeoutException e) {
                if (retryOnTimeout && attempt < maxRetries) {
                    lastException = new WebApplicationException(
                        "Upstream request timed out after " + (effectiveTimeoutMs / 1000) + " s",
                        Response.Status.GATEWAY_TIMEOUT);
                    continue;
                }
                circuitBreaker.recordFailure(baseUrl, profile);
                throw new WebApplicationException(
                    "Upstream request timed out after " + (effectiveTimeoutMs / 1000) + " s",
                    Response.Status.GATEWAY_TIMEOUT);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (isRetryableError(cause) && attempt < maxRetries) {
                    lastException = new WebApplicationException(
                        "Upstream request failed: " + cause.getMessage(),
                        Response.Status.BAD_GATEWAY);
                    continue;
                }
                circuitBreaker.recordFailure(baseUrl, profile);
                throw new WebApplicationException(
                    "Upstream request failed: " + cause.getMessage(),
                    Response.Status.BAD_GATEWAY);
            } catch (Exception e) {
                circuitBreaker.recordFailure(baseUrl, profile);
                Log.errorf(e, "Upstream request failed: %s %s", method, uri);
                throw new WebApplicationException("Upstream request failed: " + e.getMessage(), Response.Status.BAD_GATEWAY);
            }
        }

        if (lastException != null) {
            circuitBreaker.recordFailure(baseUrl, profile);
            throw lastException;
        }
        circuitBreaker.recordFailure(baseUrl, profile);
        throw new WebApplicationException("Upstream request failed: max retries exceeded", Response.Status.BAD_GATEWAY);
    }

    private UpstreamResponse sendOnce(String method, String baseUrl, String path,
                                       MultiMap headers, Object body, int effectiveTimeoutMs) throws Exception {
        URI uri = buildUri(baseUrl, path);
        String bodyJson = null;

        io.vertx.ext.web.client.HttpRequest<Buffer> request = webClient.request(
                io.vertx.core.http.HttpMethod.valueOf(method),
                resolvePort(uri),
                uri.getHost(),
                uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
            .ssl(isSsl(uri))
            .timeout(effectiveTimeoutMs);

        if (headers != null) {
            headers.forEach(entry -> {
                if (!entry.getKey().equalsIgnoreCase("Host")) {
                    request.putHeader(entry.getKey(), entry.getValue());
                }
            });
        }

        if (body != null) {
            bodyJson = (body instanceof String) ? (String) body : objectMapper.writeValueAsString(body);
            request.putHeader(HttpHeaders.CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON);
        }

        io.vertx.ext.web.client.HttpResponse<Buffer> response =
            (bodyJson != null
                ? request.sendBuffer(Buffer.buffer(bodyJson))
                : request.send())
            .toCompletionStage().toCompletableFuture()
            .get(effectiveTimeoutMs, TimeUnit.MILLISECONDS);

        Buffer bodyBuffer = response.body();

        UpstreamResponse result = new UpstreamResponse();
        result.statusCode = response.statusCode();
        result.statusMessage = response.statusMessage();
        result.headers = toMultiMap(response.headers());
        result.body = bodyBuffer != null ? bodyBuffer.toString() : null;
        result.streaming = isStreamingResponse(result.headers);

        Log.debugf("Upstream response: status=%d, contentLength=%s, bodyLength=%d, first100=%s, last100=%s",
            result.statusCode,
            result.headers.get(HttpHeaders.CONTENT_LENGTH),
            result.body != null ? result.body.length() : 0,
            result.body != null ? result.body.substring(0, Math.min(100, result.body.length())) : "null",
            result.body != null ? result.body.substring(Math.max(0, result.body.length() - 100)) : "null");

        return result;
    }

    public Future<HttpClientResponse> sendStream(String method, String baseUrl, String path,
                                                  MultiMap headers, Object body, int timeoutMs) {
        return sendStreamWithRetry(method, baseUrl, path, headers, body, timeoutMs, 0);
    }

    private Future<HttpClientResponse> sendStreamWithRetry(String method, String baseUrl, String path,
                                                            MultiMap headers, Object body, int timeoutMs,
                                                            int attempt) {
        if (attempt > maxRetries) {
            return Future.failedFuture(new RuntimeException("Upstream request failed: max retries exceeded"));
        }
        return sendStreamOnce(method, baseUrl, path, headers, body, timeoutMs)
            .compose(response -> {
                if (response.statusCode() >= 400 && retryableStatusCodes.contains(response.statusCode()) && attempt < maxRetries) {
                    response.resume();
                    return delayedFuture(retryDelayMs(attempt))
                        .compose(v -> sendStreamWithRetry(method, baseUrl, path, headers, body, timeoutMs, attempt + 1));
                }
                return Future.succeededFuture(response);
            })
            .recover(err -> {
                if (isRetryableError(err) && attempt < maxRetries) {
                    return delayedFuture(retryDelayMs(attempt))
                        .compose(v -> sendStreamWithRetry(method, baseUrl, path, headers, body, timeoutMs, attempt + 1));
                }
                return Future.failedFuture(err);
            });
    }

    private Future<HttpClientResponse> sendStreamOnce(String method, String baseUrl, String path,
                                                       MultiMap headers, Object body, int timeoutMs) {
        URI uri = buildUri(baseUrl, path);
        return streamHttpClient.request(new io.vertx.core.http.RequestOptions()
                .setMethod(io.vertx.core.http.HttpMethod.valueOf(method))
                .setHost(uri.getHost())
                .setPort(resolvePort(uri))
                .setSsl(isSsl(uri))
                .setURI(uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                .setTimeout(timeoutMs > 0 ? timeoutMs : 0))
            .compose(req -> {
                if (headers != null) {
                    headers.forEach(entry -> {
                        if (!entry.getKey().equalsIgnoreCase("Host")) {
                            req.putHeader(entry.getKey(), entry.getValue());
                        }
                    });
                }
                if (body != null) {
                    try {
                        String bodyJson = (body instanceof String) ? (String) body : objectMapper.writeValueAsString(body);
                        req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                        return req.send(Buffer.buffer(bodyJson));
                    } catch (Exception e) {
                        return Future.failedFuture(e);
                    }
                }
                return req.send();
            });
    }

    private Future<Void> delayedFuture(long delayMs) {
        Promise<Void> promise = Promise.promise();
        vertx.setTimer(delayMs, id -> promise.complete());
        return promise.future();
    }

    private boolean isRetryableError(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof WebApplicationException wae) {
            return retryableStatusCodes.contains(wae.getResponse().getStatus());
        }
        if (t instanceof TimeoutException) {
            return retryOnTimeout;
        }
        if (t instanceof ExecutionException ee) {
            return isRetryableError(ee.getCause());
        }
        return true;
    }

    private long retryDelayMs(int attempt) {
        long delay = retryBaseDelayMs * (1L << attempt);
        return Math.min(delay, retryMaxDelayMs);
    }

    private URI buildUri(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + p);
    }

    private int resolvePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        return isSsl(uri) ? 443 : 80;
    }

    private boolean isSsl(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    private static int clampTimeoutMs(int timeoutMs) {
        // 0 means "no timeout configured" – still enforce a hard ceiling so a
        // runaway upstream cannot pin the worker pool indefinitely.
        int hardCeilingMs = 3_600_000; // 1 hour
        if (timeoutMs <= 0 || timeoutMs > hardCeilingMs) {
            return hardCeilingMs;
        }
        return timeoutMs;
    }

    private MultiMap toMultiMap(io.vertx.core.MultiMap source) {
        MultiMap result = MultiMap.caseInsensitiveMultiMap();
        if (source != null) {
            source.forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private boolean isStreamingResponse(MultiMap headers) {
        String contentType = headers.get(HttpHeaders.CONTENT_TYPE);
        return contentType != null && contentType.contains(MediaType.SERVER_SENT_EVENTS);
    }

    public static class UpstreamResponse {
        public int statusCode;
        public String statusMessage;
        public MultiMap headers;
        public String body;
        public boolean streaming;
    }
}
