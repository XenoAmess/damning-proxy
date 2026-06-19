package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.RequestOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@ApplicationScoped
public class UpstreamHttpClient {

    private final HttpClient httpClient;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    public UpstreamHttpClient(Vertx vertx) {
        this.httpClient = vertx.createHttpClient(new io.vertx.core.http.HttpClientOptions()
            .setTryUseCompression(false));
    }

    public UpstreamResponse send(String method, String baseUrl, String path,
                                 MultiMap headers, Object body, int timeoutMs) {
        URI uri = buildUri(baseUrl, path);
        Log.debugf("Upstream request: %s %s", method, uri);

        try {
            Future<HttpClientResponse> future = httpClient.request(new RequestOptions()
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

            HttpClientResponse response = future.toCompletionStage().toCompletableFuture().get();
            Buffer bodyBuffer = response.body().toCompletionStage().toCompletableFuture().get();

            UpstreamResponse result = new UpstreamResponse();
            result.statusCode = response.statusCode();
            result.statusMessage = response.statusMessage();
            result.headers = response.headers();
            result.body = bodyBuffer != null ? bodyBuffer.toString() : null;
            result.streaming = isStreamingResponse(result.headers);

            return result;
        } catch (Exception e) {
            Log.errorf(e, "Upstream request failed: %s %s", method, uri);
            throw new WebApplicationException("Upstream request failed: " + e.getMessage(), Response.Status.BAD_GATEWAY);
        }
    }

    public Future<HttpClientResponse> sendStream(String method, String baseUrl, String path,
                                                  MultiMap headers, Object body, int timeoutMs) {
        URI uri = buildUri(baseUrl, path);
        Log.debugf("Upstream stream request: %s %s", method, uri);

        return httpClient.request(new RequestOptions()
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
