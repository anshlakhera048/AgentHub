package com.agenthub.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultPromptEngine implements PromptEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultPromptEngine.class);
    private final Map<String, String> templates = new ConcurrentHashMap<>();
    private final PromptTemplateLoader templateLoader;

    public DefaultPromptEngine(PromptTemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
    }

    @PostConstruct
    public void init() {
        templates.putAll(templateLoader.loadAllTemplates());
        log.info("Loaded {} prompt templates: {}", templates.size(), templates.keySet());
    }

    @Override
    public String getTemplate(String agentName) {
        String template = templates.get(agentName);
        if (template == null) {
            log.warn("No prompt template found for agent '{}', using fallback", agentName);
            return getDefaultTemplate();
        }
        return template;
    }

    @Override
    public String render(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        // Remove any remaining unresolved placeholders
        result = result.replaceAll("\\{\\{[^}]+\\}\\}", "");
        return result.trim();
    }

    @Override
    public String augmentWithContext(String prompt, String context) {
        if (context == null || context.isBlank()) return prompt;
        return prompt + "\n\n--- Context ---\n" + context;
    }

    @Override
    public String augmentWithExamples(String prompt, String examples) {
        if (examples == null || examples.isBlank()) return prompt;
        return prompt + "\n\n--- Examples ---\n" + examples;
    }

    private String getDefaultTemplate() {
        return """
                You are a helpful AI assistant.
                
                {{memory_context}}
                
                User request: {{user_input}}
                
                Please provide a helpful and accurate response.
                """;
    }

    public void registerTemplate(String agentName, String template) {
        templates.put(agentName, template);
        log.info("Registered prompt template for agent '{}'", agentName);
    }
}
