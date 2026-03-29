package com.agenthub.controller;

import com.agenthub.model.dto.WorkflowRequest;
import com.agenthub.model.dto.WorkflowResponse;
import com.agenthub.orchestrator.dag.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for DAG-based workflow execution.
 */
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    private final DAGExecutorService dagExecutorService;

    public WorkflowController(DAGExecutorService dagExecutorService) {
        this.dagExecutorService = dagExecutorService;
    }

    @PostMapping("/execute")
    public ResponseEntity<WorkflowResponse> executeWorkflow(@Valid @RequestBody WorkflowRequest request) {
        log.info("POST /api/workflow/execute - {} nodes", request.nodes().size());

        // Build DAG from request
        DAG.Builder builder = DAG.builder();
        for (var nodeDef : request.nodes()) {
            builder.addNode(new DAGNode(
                    nodeDef.id(),
                    nodeDef.agentName(),
                    nodeDef.dependencies(),
                    nodeDef.parameters()
            ));
        }
        DAG dag = builder.build();

        // Execute
        DAGExecutionResult result = dagExecutorService.execute(
                dag, request.input(), request.sessionId(), request.context());

        WorkflowResponse response = new WorkflowResponse(
                result.executionId(),
                result.success(),
                result.totalLatencyMs(),
                result.nodeResults(),
                result.errorMessage(),
                result.timestamp()
        );

        return ResponseEntity.ok(response);
    }
}
