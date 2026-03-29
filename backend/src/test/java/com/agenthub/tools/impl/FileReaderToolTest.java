package com.agenthub.tools.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileReaderToolTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsPathTraversal() {
        FileReaderTool tool = new FileReaderTool();
        // Use reflection to set baseDir
        try {
            var field = FileReaderTool.class.getDeclaredField("baseDir");
            field.setAccessible(true);
            field.set(tool, tempDir.toString());
        } catch (Exception e) {
            fail("Failed to set baseDir: " + e.getMessage());
        }

        assertThrows(SecurityException.class, () ->
                tool.execute(Map.of("path", "../../etc/passwd")));
    }

    @Test
    void readsFileSuccessfully() throws IOException {
        FileReaderTool tool = new FileReaderTool();
        try {
            var field = FileReaderTool.class.getDeclaredField("baseDir");
            field.setAccessible(true);
            field.set(tool, tempDir.toString());
        } catch (Exception e) {
            fail("Failed to set baseDir: " + e.getMessage());
        }

        Files.writeString(tempDir.resolve("test.txt"), "hello world");
        Object result = tool.execute(Map.of("path", "test.txt"));
        assertEquals("hello world", result);
    }
}
