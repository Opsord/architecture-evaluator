package io.github.opsord.architecture_evaluator_backend.modules.config;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveExtractionException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveLimitExceededException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.GitHubDownloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Request");
        return problem;
    }

    @ExceptionHandler(ArchiveLimitExceededException.class)
    public ProblemDetail handleArchiveLimitExceeded(ArchiveLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
        problem.setTitle("Archive Limit Exceeded");
        return problem;
    }

    @ExceptionHandler(ArchiveExtractionException.class)
    public ProblemDetail handleArchiveExtraction(ArchiveExtractionException ex) {
        log.warn("Archive extraction failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "The uploaded archive could not be extracted. Please provide a valid .zip or .tar archive."
        );
        problem.setTitle("Invalid Archive");
        return problem;
    }

    @ExceptionHandler(GitHubDownloadException.class)
    public ProblemDetail handleGitHubDownload(GitHubDownloadException ex) {
        log.warn("GitHub download failed (HTTP {}): {}", ex.getHttpStatus(), ex.getMessage());
        if (ex.getHttpStatus() == HttpStatus.NOT_FOUND.value()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND,
                    "GitHub repository or default branch (main/master) was not found."
            );
            problem.setTitle("Repository Not Found");
            return problem;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Failed to download repository archive from GitHub."
        );
        problem.setTitle("GitHub Download Failed");
        return problem;
    }

    @ExceptionHandler(IOException.class)
    public ProblemDetail handleIOException(IOException ex) {
        log.error("Unexpected I/O error during project analysis", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected I/O error occurred while analyzing the project."
        );
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
