package io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions;

import java.io.IOException;

public class ArchiveExtractionException extends IOException {

    public ArchiveExtractionException(String message) {
        super(message);
    }

    public ArchiveExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
