package com.agenthub.llm;

import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Pluggable LLM client abstraction. Implementations can target
 * Ollama, OpenAI, or any other LLM backend.
 */
public interface LLMClient {

    String generate(String prompt);

    String generate(String prompt, LLMOptions options);

    CompletableFuture<String> generateAsync(String prompt);

    CompletableFuture<String> generateAsync(String prompt, LLMOptions options);

    /**
     * Stream tokens from the LLM as they are generated.
     */
    default Flux<String> stream(String prompt) {
        return Flux.just(generate(prompt));
    }

    /**
     * Stream tokens from the LLM with custom options.
     */
    default Flux<String> stream(String prompt, LLMOptions options) {
        return Flux.just(generate(prompt, options));
    }
}
