package io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.file_instance.FileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.FileInstanceService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SrcScannerService {

    private static final Logger logger = LoggerFactory.getLogger(SrcScannerService.class);
    private final FileInstanceService fileInstanceService;
    private final ExecutorService parserExecutor;

    public SrcScannerService(FileInstanceService fileInstanceService) {
        this.fileInstanceService = fileInstanceService;
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        AtomicInteger threadCounter = new AtomicInteger(1);
        this.parserExecutor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "java-parser-worker-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdown() {
        parserExecutor.shutdownNow();
    }

    /**
     * Scans the `src` folder for all `.java` files.
     */
    public List<File> scanSrcFolder(File srcFolder) throws IOException {
        if (!srcFolder.exists() || !srcFolder.isDirectory()) {
            logger.warn("Invalid `src` folder: {}", srcFolder.getAbsolutePath());
            return List.of();
        }

        File scanRoot = resolveJavaSourceRoot(srcFolder);
        List<File> javaFiles = new ArrayList<>();
        try (var paths = Files.walk(scanRoot.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> javaFiles.add(path.toFile()));
        }
        logger.info("Found {} Java files under: {}", javaFiles.size(), scanRoot.getAbsolutePath());
        return javaFiles;
    }

    /**
     * Prioritizes {@code src/main/java} when present to analyze production code quickly and avoid
     * inflating analysis times with large test suites. Falls back to {@code src/} for flat layouts
     * without a standard Maven/Gradle {@code src/main/java} directory.
     */
    private File resolveJavaSourceRoot(File srcFolder) {
        File mainJava = new File(srcFolder, "main/java");
        if (mainJava.isDirectory()) {
            return mainJava;
        }
        return srcFolder;
    }

    /**
     * Parses Java files into FileInstance objects using a bounded daemon executor.
     */
    public List<FileInstance> parseJavaFiles(List<File> javaFiles, File projectRoot) {
        if (javaFiles == null || javaFiles.isEmpty()) {
            return List.of();
        }

        List<Future<FileInstance>> futures = new ArrayList<>(javaFiles.size());
        for (File javaFile : javaFiles) {
            futures.add(parserExecutor.submit(() -> {
                try {
                    return fileInstanceService.parseJavaFile(javaFile, projectRoot);
                } catch (Exception e) {
                    logger.error("Failed to parse file: {}", javaFile.getAbsolutePath(), e);
                    return null;
                }
            }));
        }

        List<FileInstance> results = new ArrayList<>(javaFiles.size());
        for (Future<FileInstance> future : futures) {
            try {
                FileInstance instance = future.get();
                if (instance != null) {
                    results.add(instance);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                for (Future<FileInstance> remaining : futures) {
                    remaining.cancel(true);
                }
                logger.warn("Java file parsing interrupted", e);
                break;
            } catch (ExecutionException e) {
                logger.error("Unexpected execution error while parsing Java file", e.getCause());
            }
        }
        return results;
    }
}
