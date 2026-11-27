package io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.instances.ProjectAnalysisInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ProjectIngestionService {

    private final FileManagerService fileManagerService;
    private final OrchestratorService orchestratorService;

    public ProjectAnalysisInstance analyzeUploadedArchive(MultipartFile projectArchive) throws IOException {
        File tempDir = null;
        try {
            File uploadedFile = fileManagerService.saveUploadedFile(projectArchive);
            tempDir = uploadedFile.getParentFile();
            File extractedDir = fileManagerService.extractArchive(uploadedFile);
            return orchestratorService.orchestrateProjectAnalysis(extractedDir.getAbsolutePath());
        } finally {
            if (tempDir != null) {
                fileManagerService.deleteRecursively(tempDir);
            }
        }
    }

    public ProjectAnalysisInstance analyzeGitHubRepository(String repoUrl) throws IOException {
        File tempDir = null;
        try {
            File zipFile = fileManagerService.downloadGitHubRepository(repoUrl);
            tempDir = zipFile.getParentFile();
            File extractedDir = fileManagerService.extractArchive(zipFile);
            return orchestratorService.orchestrateProjectAnalysis(extractedDir.getAbsolutePath());
        } finally {
            if (tempDir != null) {
                fileManagerService.deleteRecursively(tempDir);
            }
        }
    }
}
