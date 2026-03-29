package com.agenthub.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;

class OllamaLLMClientStreamTest {

    private MockWebServer mockServer;
    private OllamaLLMClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        String baseUrl = mockServer.url("/").toString();
        client = new OllamaLLMClient(baseUrl, "test-model", new ObjectMapper(), new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void streamReturnsTokens() {
        // Ollama streaming returns NDJSON — one JSON object per line
        String body = "{\"response\":\"Hello\"}\n" +
                      "{\"response\":\" world\"}\n" +
                      "{\"response\":\"!\",\"done\":true}\n";

        mockServer.enqueue(new MockResponse()
                .setBody(body)
                .setHeader("Content-Type", "application/x-ndjson"));

        Flux<String> result = client.stream("test prompt");

        StepVerifier.create(result)
                .expectNext("Hello")
                .expectNext(" world")
                .verifyComplete();
    }

    @Test
    void streamWithDefaults() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"response\":\"token\"}\n{\"done\":true}\n")
                .setHeader("Content-Type", "application/x-ndjson"));

        Flux<String> result = client.stream("prompt");

        StepVerifier.create(result)
                .expectNext("token")
                .verifyComplete();
    }
}
