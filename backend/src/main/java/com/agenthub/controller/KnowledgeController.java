package com.agenthub.controller;

import com.agenthub.memory.vector.DocumentIngestionService;
import com.agenthub.model.dto.KnowledgeIngestRequest;
import com.agenthub.model.dto.KnowledgeIngestResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final DocumentIngestionService ingestionService;

    public KnowledgeController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<KnowledgeIngestResponse> ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        String documentId = request.documentId() != null && !request.documentId().isBlank()
                ? request.documentId()
                : UUID.randomUUID().toString();

        log.info("POST /api/knowledge/ingest - documentId: {}, contentLength: {}",
                documentId, request.content().length());

        try {
            int chunks = ingestionService.ingest(request.content(), documentId);
            return ResponseEntity.ok(KnowledgeIngestResponse.success(documentId, chunks));
        } catch (Exception e) {
            log.error("Knowledge ingestion failed for document '{}': {}", documentId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(KnowledgeIngestResponse.failure(documentId, e.getMessage()));
        }
    }
}
