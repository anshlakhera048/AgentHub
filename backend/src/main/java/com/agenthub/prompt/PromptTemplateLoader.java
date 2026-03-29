package com.agenthub.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class PromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateLoader.class);
    private static final String TEMPLATE_DIR = "prompts/";

    public Map<String, String> loadAllTemplates() {
        Map<String, String> templates = new HashMap<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:" + TEMPLATE_DIR + "*.txt");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.endsWith(".txt")) {
                    String agentName = filename.replace(".txt", "");
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    templates.put(agentName, content);
                    log.debug("Loaded prompt template for agent: {}", agentName);
                }
            }
        } catch (IOException e) {
            log.warn("Could not load prompt templates from classpath: {}", e.getMessage());
        }
        return templates;
    }

    public String loadTemplate(String agentName) {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + agentName + ".txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load prompt template for agent '{}': {}", agentName, e.getMessage());
            return null;
        }
    }
}
