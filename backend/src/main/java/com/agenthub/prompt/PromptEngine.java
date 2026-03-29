package com.agenthub.prompt;

import java.util.Map;

/**
 * Prompt engine responsible for managing prompt templates,
 * variable injection, and context augmentation.
 */
public interface PromptEngine {

    String getTemplate(String agentName);

    String render(String template, Map<String, String> variables);

    String augmentWithContext(String prompt, String context);

    String augmentWithExamples(String prompt, String examples);
}
