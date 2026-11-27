package io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.controllers;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.instances.ProjectAnalysisInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services.OrchestratorService;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services.ProjectIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorController.class);
    private final OrchestratorService orchestratorService;
    private final ProjectIngestionService projectIngestionService;
    private final boolean localAnalysisEnabled;

    public OrchestratorController(
            OrchestratorService orchestratorService,
            ProjectIngestionService projectIngestionService,
            @Value("${archev.local-analysis.enabled:false}") boolean localAnalysisEnabled
    ) {
        this.orchestratorService = orchestratorService;
        this.projectIngestionService = projectIngestionService;
        this.localAnalysisEnabled = localAnalysisEnabled;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ProjectAnalysisInstance> analyzeProject(@RequestParam String projectPath) throws IOException {
        if (!localAnalysisEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Local filesystem analysis is disabled in this environment."
            );
        }
        String safeProjectPath = projectPath.replaceAll("[\\r\\n]", "_");
        logger.info("Received request to analyze project locally: {}", safeProjectPath);
        ProjectAnalysisInstance result = orchestratorService.orchestrateProjectAnalysis(projectPath);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze-upload")
    public ResponseEntity<ProjectAnalysisInstance> analyzeProject(@RequestParam("project") MultipartFile projectFolder)
            throws IOException {
        logger.info("Received request to analyze project from uploaded archive");
        ProjectAnalysisInstance result = projectIngestionService.analyzeUploadedArchive(projectFolder);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze-github")
    public ResponseEntity<ProjectAnalysisInstance> analyzeGitHubRepo(@RequestParam String repoUrl) throws IOException {
        logger.info("Received request to analyze GitHub repository");
        ProjectAnalysisInstance result = projectIngestionService.analyzeGitHubRepository(repoUrl);
        return ResponseEntity.ok(result);
    }
}
