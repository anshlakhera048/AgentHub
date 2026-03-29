package com.agenthub.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultMemoryService implements MemoryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryService.class);

    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;

    public DefaultMemoryService(ShortTermMemory shortTermMemory, LongTermMemory longTermMemory) {
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
    }

    @Override
    public void storeShortTermMemory(String sessionId, String content) {
        shortTermMemory.store(sessionId, content);
    }

    @Override
    public String getShortTermMemory(String sessionId) {
        return shortTermMemory.retrieve(sessionId);
    }

    @Override
    public void clearShortTermMemory(String sessionId) {
        shortTermMemory.clear(sessionId);
    }

    @Override
    public void storeDocument(String content, String documentId) {
        try {
            longTermMemory.store(content, documentId);
        } catch (Exception e) {
            log.error("Failed to store document in long-term memory: {}", e.getMessage(), e);
        }
    }

    @Override
    public String retrieveRelevantContext(String query, int topK) {
        try {
            List<String> chunks = longTermMemory.retrieve(query, topK);
            return String.join("\n\n---\n\n", chunks);
        } catch (Exception e) {
            log.warn("Failed to retrieve from long-term memory: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public List<String> retrieveRelevantChunks(String query, int topK) {
        try {
            return longTermMemory.retrieve(query, topK);
        } catch (Exception e) {
            log.warn("Failed to retrieve chunks from long-term memory: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteDocument(String documentId) {
        try {
            longTermMemory.delete(documentId);
        } catch (Exception e) {
            log.warn("Failed to delete document from long-term memory: {}", e.getMessage());
        }
    }
}
