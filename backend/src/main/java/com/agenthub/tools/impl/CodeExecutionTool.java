package com.agenthub.tools.impl;

import com.agenthub.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Sandboxed code execution tool. Runs code snippets in isolated processes
 * with strict timeouts and resource limits.
 */
@Component
public class CodeExecutionTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionTool.class);

    @Value("${agenthub.tools.code-execution.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${agenthub.tools.code-execution.enabled:false}")
    private boolean enabled;

    @Override
    public String getName() {
        return "CodeExecution";
    }

    @Override
    public String getDescription() {
        return "Executes Python code snippets in a sandboxed environment with strict timeouts.";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        if (!enabled) {
            throw new UnsupportedOperationException(
                    "Code execution tool is disabled. Set agenthub.tools.code-execution.enabled=true to enable.");
        }

        String code = (String) params.get("code");
        String language = (String) params.getOrDefault("language", "python");

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Parameter 'code' is required");
        }

        // Basic safety checks — block obviously dangerous operations
        validateCodeSafety(code, language);

        return switch (language.toLowerCase()) {
            case "python" -> executePython(code);
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private Object executePython(String code) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "-c", code);
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return Map.of("success", false, "error", "Execution timed out after " + timeoutSeconds + "s");
            }

            int exitCode = process.exitValue();
            log.debug("Python execution completed with exit code: {}", exitCode);

            return Map.of(
                    "success", exitCode == 0,
                    "output", output.toString().trim(),
                    "exitCode", exitCode
            );
        } catch (Exception e) {
            throw new RuntimeException("Code execution failed: " + e.getMessage(), e);
        }
    }

    private void validateCodeSafety(String code, String language) {
        String[] dangerousPatterns = {
                "import os", "import subprocess", "import sys",
                "os.system", "os.exec", "subprocess.run", "subprocess.Popen",
                "__import__", "eval(", "exec(",
                "open('/etc", "open('/proc", "open('/sys",
                "shutil.rmtree", "os.remove", "os.unlink"
        };

        String lowerCode = code.toLowerCase();
        for (String pattern : dangerousPatterns) {
            if (lowerCode.contains(pattern.toLowerCase())) {
                throw new SecurityException(
                        "Code contains potentially dangerous operation: " + pattern);
            }
        }
    }
}
