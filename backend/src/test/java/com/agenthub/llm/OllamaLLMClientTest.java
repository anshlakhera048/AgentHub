package com.agenthub.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OllamaLLMClientTest {

    private MockWebServer mockServer;
    private OllamaLLMClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        String baseUrl = mockServer.url("/").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        objectMapper = new ObjectMapper();
        client = new OllamaLLMClient(
                baseUrl, "mistral", objectMapper, new SimpleMeterRegistry()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void generateReturnsResponseText() throws Exception {
        String jsonResponse = """
                {"model":"mistral","response":"Hello, world!","done":true}
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        String result = client.generate("Say hello");

        assertEquals("Hello, world!", result);

        RecordedRequest recorded = mockServer.takeRequest();
        assertEquals("/api/generate", recorded.getPath());
        assertEquals("POST", recorded.getMethod());

        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"mistral\""));
        assertTrue(body.contains("\"prompt\":\"Say hello\""));
        assertTrue(body.contains("\"stream\":false"));
    }

    @Test
    void generateWithOptionsUsesCustomModel() throws Exception {
        String jsonResponse = """
                {"model":"llama3","response":"Custom model response","done":true}
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        LLMOptions options = LLMOptions.builder()
                .model("llama3")
                .temperature(0.5)
                .maxTokens(1024)
                .build();

        String result = client.generate("test prompt", options);

        assertEquals("Custom model response", result);

        String body = mockServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"llama3\""));
    }

    @Test
    void generateThrowsLLMExceptionOnServerError() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error"));

        assertThrows(LLMException.class, () -> client.generate("test"));
    }

    @Test
    void generateAsyncReturnsResult() throws Exception {
        String jsonResponse = """
                {"model":"mistral","response":"Async result","done":true}
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        String result = client.generateAsync("test").get();

        assertEquals("Async result", result);
    }
}
