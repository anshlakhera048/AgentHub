package com.agenthub.tools.impl;

import com.agenthub.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class HttpClientTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(HttpClientTool.class);

    private final HttpClient httpClient;

    @Value("${agenthub.tools.http-client.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("#{'${agenthub.tools.http-client.allowed-domains:}'.split(',')}")
    private List<String> allowedDomains;

    public HttpClientTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String getName() {
        return "HttpClient";
    }

    @Override
    public String getDescription() {
        return "Makes HTTP GET requests to allowed external URLs and returns the response body.";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        String url = (String) params.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Parameter 'url' is required");
        }

        // Validate URL against allowed domains
        URI uri = URI.create(url);
        if (!isAllowedDomain(uri)) {
            throw new SecurityException(
                    "Domain not allowed: " + uri.getHost() +
                    ". Allowed domains: " + allowedDomains);
        }

        String method = (String) params.getOrDefault("method", "GET");

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            if ("GET".equalsIgnoreCase(method)) {
                requestBuilder.GET();
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = (String) params.getOrDefault("body", "");
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
                requestBuilder.header("Content-Type", "application/json");
            } else {
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            log.debug("HTTP {} {} -> status {}", method, url, response.statusCode());

            return Map.of(
                    "statusCode", response.statusCode(),
                    "body", response.body(),
                    "headers", response.headers().map()
            );
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    private boolean isAllowedDomain(URI uri) {
        if (allowedDomains == null || allowedDomains.isEmpty() ||
            (allowedDomains.size() == 1 && allowedDomains.get(0).isBlank())) {
            return true; // No restriction if no domains configured
        }
        String host = uri.getHost();
        return allowedDomains.stream().anyMatch(domain ->
                host != null && host.endsWith(domain.trim()));
    }
}
