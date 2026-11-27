package io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions;

import java.io.IOException;

public class ArchiveLimitExceededException extends IOException {

    public ArchiveLimitExceededException(String message) {
        super(message);
    }
}
