package io.github.opsord.architecture_evaluator_backend.modules.orchestrator;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.controllers.OrchestratorController;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveExtractionException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveLimitExceededException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.GitHubDownloadException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.instances.ProjectAnalysisInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services.OrchestratorService;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services.ProjectIngestionService;
import io.github.opsord.architecture_evaluator_backend.modules.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrchestratorController.class)
@Import(GlobalExceptionHandler.class)
class OrchestratorControllerTest {

    @MockitoBean
    private OrchestratorService orchestratorService;

    @MockitoBean
    private ProjectIngestionService projectIngestionService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyzeProject_shouldReturnForbiddenByDefault() throws Exception {
        mockMvc.perform(post("/api/orchestrator/analyze")
                        .param("projectPath", "some/path"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void analyzeProject_shouldReturnOkWhenLocalAnalysisEnabled() throws Exception {
        ProjectAnalysisInstance mockResult = new ProjectAnalysisInstance();
        when(orchestratorService.orchestrateProjectAnalysis("some/path")).thenReturn(mockResult);

        OrchestratorController enabledController =
                new OrchestratorController(orchestratorService, projectIngestionService, true);
        ResponseEntity<ProjectAnalysisInstance> response = enabledController.analyzeProject("some/path");

        assertEquals(200, response.getStatusCode().value());
        assertSame(mockResult, response.getBody());
    }

    @Test
    void analyzeProjectUpload_shouldReturnOk() throws Exception {
        ProjectAnalysisInstance mockResult = new ProjectAnalysisInstance();
        when(projectIngestionService.analyzeUploadedArchive(any())).thenReturn(mockResult);

        MockMultipartFile multipartFile = new MockMultipartFile(
                "project", "test.zip", "application/zip", "dummy".getBytes());

        mockMvc.perform(multipart("/api/orchestrator/analyze-upload")
                        .file(multipartFile))
                .andExpect(status().isOk());
    }

    @Test
    void analyzeGitHubRepo_shouldReturnOk() throws Exception {
        ProjectAnalysisInstance mockResult = new ProjectAnalysisInstance();
        when(projectIngestionService.analyzeGitHubRepository(anyString())).thenReturn(mockResult);

        mockMvc.perform(post("/api/orchestrator/analyze-github")
                        .param("repoUrl", "https://github.com/user/repo"))
                .andExpect(status().isOk());
    }

    @Test
    void analyzeProjectUpload_shouldReturnBadRequest_onIllegalArgument() throws Exception {
        when(projectIngestionService.analyzeUploadedArchive(any()))
                .thenThrow(new IllegalArgumentException("Unsupported archive format"));

        MockMultipartFile multipartFile = new MockMultipartFile(
                "project", "test.txt", "text/plain", "dummy".getBytes());

        mockMvc.perform(multipart("/api/orchestrator/analyze-upload")
                        .file(multipartFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"))
                .andExpect(jsonPath("$.detail").value("Unsupported archive format"));
    }

    @Test
    void analyzeProjectUpload_shouldReturnPayloadTooLarge_onArchiveLimitExceeded() throws Exception {
        when(projectIngestionService.analyzeUploadedArchive(any()))
                .thenThrow(new ArchiveLimitExceededException("Archive exceeds limit"));

        MockMultipartFile multipartFile = new MockMultipartFile(
                "project", "bomb.zip", "application/zip", "dummy".getBytes());

        mockMvc.perform(multipart("/api/orchestrator/analyze-upload")
                        .file(multipartFile))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("Archive Limit Exceeded"))
                .andExpect(jsonPath("$.detail").value("Archive exceeds limit"));
    }

    @Test
    void analyzeProjectUpload_shouldReturnUnprocessableEntity_onArchiveExtractionError() throws Exception {
        when(projectIngestionService.analyzeUploadedArchive(any()))
                .thenThrow(new ArchiveExtractionException("Corrupted archive"));

        MockMultipartFile multipartFile = new MockMultipartFile(
                "project", "corrupt.zip", "application/zip", "dummy".getBytes());

        mockMvc.perform(multipart("/api/orchestrator/analyze-upload")
                        .file(multipartFile))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Invalid Archive"));
    }

    @Test
    void analyzeProjectUpload_shouldReturnInternalServerError_onGenericIOException() throws Exception {
        when(projectIngestionService.analyzeUploadedArchive(any()))
                .thenThrow(new IOException("/tmp/secret-path failed"));

        MockMultipartFile multipartFile = new MockMultipartFile(
                "project", "test.zip", "application/zip", "dummy".getBytes());

        mockMvc.perform(multipart("/api/orchestrator/analyze-upload")
                        .file(multipartFile))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected I/O error occurred while analyzing the project."));
    }

    @Test
    void analyzeGitHubRepo_shouldReturnNotFound_onGitHub404() throws Exception {
        when(projectIngestionService.analyzeGitHubRepository(anyString()))
                .thenThrow(new GitHubDownloadException("Not found", 404));

        mockMvc.perform(post("/api/orchestrator/analyze-github")
                        .param("repoUrl", "https://github.com/user/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Repository Not Found"));
    }

    @Test
    void analyzeGitHubRepo_shouldReturnBadGateway_onGitHub500() throws Exception {
        when(projectIngestionService.analyzeGitHubRepository(anyString()))
                .thenThrow(new GitHubDownloadException("Upstream error", 503));

        mockMvc.perform(post("/api/orchestrator/analyze-github")
                        .param("repoUrl", "https://github.com/user/repo"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("GitHub Download Failed"));
    }
}
