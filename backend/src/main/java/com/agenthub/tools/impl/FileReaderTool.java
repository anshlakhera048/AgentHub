package com.agenthub.tools.impl;

import com.agenthub.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class FileReaderTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FileReaderTool.class);
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB

    @Value("${agenthub.tools.file-reader.base-dir:./data}")
    private String baseDir;

    @Override
    public String getName() {
        return "FileReader";
    }

    @Override
    public String getDescription() {
        return "Reads text content from a file within the allowed base directory.";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        String filePath = (String) params.get("path");
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Parameter 'path' is required");
        }

        // Security: prevent path traversal
        Path resolved = Path.of(baseDir).resolve(filePath).normalize();
        Path basePath = Path.of(baseDir).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("Access denied: path traversal detected");
        }

        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        try {
            long size = Files.size(resolved);
            if (size > MAX_FILE_SIZE) {
                throw new IllegalArgumentException(
                        "File too large: " + size + " bytes (max: " + MAX_FILE_SIZE + ")");
            }
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            log.debug("Read file '{}', size={} bytes", filePath, content.length());
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }
}
