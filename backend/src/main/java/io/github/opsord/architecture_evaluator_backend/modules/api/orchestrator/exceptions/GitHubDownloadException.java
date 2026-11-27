package io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions;

import java.io.IOException;

public class GitHubDownloadException extends IOException {

    private final int httpStatus;

    public GitHubDownloadException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public GitHubDownloadException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
