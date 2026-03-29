package com.agenthub.memory;

import com.agenthub.memory.vector.DocumentIngestionService;
import com.agenthub.memory.vector.SemanticSearchService;
import com.agenthub.memory.vector.VectorSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LongTermMemory implementation backed by the modular vector pipeline
 * (EmbeddingService + VectorStore via DocumentIngestionService and SemanticSearchService).
 */
@Component
@Primary
@ConditionalOnProperty(name = "agenthub.memory.vector.enabled", havingValue = "true")
public class VectorStoreLongTermMemory implements LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreLongTermMemory.class);

    private final DocumentIngestionService ingestionService;
    private final SemanticSearchService searchService;

    public VectorStoreLongTermMemory(DocumentIngestionService ingestionService,
                                      SemanticSearchService searchService) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
        log.info("Using VectorStore-backed long-term memory");
    }

    @Override
    public void store(String content, String documentId) {
        ingestionService.ingest(content, documentId);
    }

    @Override
    public List<String> retrieve(String query, int topK) {
        List<VectorSearchResult> results = searchService.search(query, topK);
        return results.stream()
                .map(VectorSearchResult::content)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String documentId) {
        ingestionService.delete(documentId);
    }
}
