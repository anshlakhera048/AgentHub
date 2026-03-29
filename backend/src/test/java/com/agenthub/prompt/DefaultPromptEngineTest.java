package com.agenthub.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPromptEngineTest {

    @Test
    void renderReplacesPlaceholders() {
        PromptTemplateLoader loader = new PromptTemplateLoader();
        DefaultPromptEngine engine = new DefaultPromptEngine(loader);

        String template = "Hello {{name}}, you said: {{input}}";
        String result = engine.render(template, Map.of("name", "Alice", "input", "hi"));

        assertEquals("Hello Alice, you said: hi", result);
    }

    @Test
    void renderRemovesUnresolvedPlaceholders() {
        PromptTemplateLoader loader = new PromptTemplateLoader();
        DefaultPromptEngine engine = new DefaultPromptEngine(loader);

        String template = "Hello {{name}}. Context: {{missing}}";
        String result = engine.render(template, Map.of("name", "Bob"));

        assertEquals("Hello Bob. Context:", result);
    }

    @Test
    void augmentWithContextAppendsSection() {
        PromptTemplateLoader loader = new PromptTemplateLoader();
        DefaultPromptEngine engine = new DefaultPromptEngine(loader);

        String result = engine.augmentWithContext("Base prompt", "Some context");
        assertTrue(result.contains("Base prompt"));
        assertTrue(result.contains("Some context"));
    }
}
