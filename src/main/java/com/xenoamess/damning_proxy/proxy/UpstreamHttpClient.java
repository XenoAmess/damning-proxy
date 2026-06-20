package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class UpstreamHttpClient {

    private final Vertx vertx;
    private final WebClient webClient;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    public UpstreamHttpClient(Vertx vertx) {
        this.vertx = vertx;
        // Use Vert.x WebClient over HTTP/1.1: the low-level HttpClient has
        // repeatedly corrupted large non-streaming response bodies when
        // response.body() is consumed from a worker thread, leaving plain
        // text from the LLM's reasoning concatenated before the JSON payload
        // (e.g. traffic logs #128, #132).
        this.webClient = WebClient.create(vertx, new WebClientOptions()
            .setProtocolVersion(HttpVersion.HTTP_1_1)
            .setUseAlpn(false)
            .setTryUseCompression(false)
            .setSsl(true));
    }

    public UpstreamResponse send(String method, String baseUrl, String path,
                                 MultiMap headers, Object body, int timeoutMs) {
        URI uri = buildUri(baseUrl, path);
        Log.debugf("Upstream request: %s %s", method, uri);

        // Bound the wait so a stuck upstream connection (e.g. HTTP/1.1 keep-alive
        // socket that never closes) cannot pin a worker thread forever.
        int effectiveTimeoutMs = clampTimeoutMs(timeoutMs);

        String bodyJson = null;
        try {
            io.vertx.ext.web.client.HttpRequest<Buffer> request = webClient.request(
                    io.vertx.core.http.HttpMethod.valueOf(method),
                    resolvePort(uri),
                    uri.getHost(),
                    uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                .ssl(isSsl(uri))
                .timeout(timeoutMs > 0 ? timeoutMs : 0);

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
                .get(effectiveTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

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
        } catch (java.util.concurrent.TimeoutException e) {
            Log.errorf("Upstream request timed out after %d ms (request=%d bytes, url=%s %s)",
                effectiveTimeoutMs,
                bodyJson != null ? bodyJson.length() : -1,
                method, uri);
            throw new WebApplicationException(
                "Upstream request timed out after " + (effectiveTimeoutMs / 1000) + " s (request body " +
                (bodyJson != null ? bodyJson.length() : 0) + " bytes)",
                Response.Status.GATEWAY_TIMEOUT);
        } catch (Exception e) {
            Log.errorf(e, "Upstream request failed: %s %s", method, uri);
            throw new WebApplicationException("Upstream request failed: " + e.getMessage(), Response.Status.BAD_GATEWAY);
        }
    }

    public Future<HttpClientResponse> sendStream(String method, String baseUrl, String path,
                                                  MultiMap headers, Object body, int timeoutMs) {
        // Streaming still uses the low-level HttpClient because we need
        // per-chunk access to the response body for SSE. We keep a separate
        // HttpClient instance for this case.
        return createStreamClient().request(new io.vertx.core.http.RequestOptions()
                .setMethod(io.vertx.core.http.HttpMethod.valueOf(method))
                .setHost(buildUri(baseUrl, path).getHost())
                .setPort(resolvePort(buildUri(baseUrl, path)))
                .setSsl(isSsl(buildUri(baseUrl, path)))
                .setURI(buildUri(baseUrl, path).getPath() + (buildUri(baseUrl, path).getQuery() != null ? "?" + buildUri(baseUrl, path).getQuery() : ""))
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

    private io.vertx.core.http.HttpClient createStreamClient() {
        return vertx.createHttpClient(new io.vertx.core.http.HttpClientOptions()
            .setProtocolVersion(HttpVersion.HTTP_1_1)
            .setUseAlpn(false)
            .setTryUseCompression(false));
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
        int hardCeilingMs = 1_800_000; // 30 minutes
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
