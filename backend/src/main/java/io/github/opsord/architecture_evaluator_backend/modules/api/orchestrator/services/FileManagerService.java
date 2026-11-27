package io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveExtractionException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveLimitExceededException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.GitHubDownloadException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing file operations such as downloading, saving, extracting, and deleting files/directories.
 */
@Service
public class FileManagerService {

    private static final Logger logger = LoggerFactory.getLogger(FileManagerService.class);

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int BUFFER_SIZE = 8_192;

    static final long MAX_DOWNLOAD_BYTES = 150L * 1024 * 1024;
    static final long MAX_TOTAL_EXTRACTED_BYTES = 300L * 1024 * 1024;
    static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;
    static final int MAX_ARCHIVE_ENTRIES = 10_000;

    private static final class ExtractionBudget {
        private long totalBytes;
        private int entryCount;
    }

    // === Public API ===

    /**
     * Downloads a GitHub repository as a ZIP file and stores it in a secure temporary directory.
     * Tries the {@code main} branch first and falls back to {@code master} on HTTP 404.
     */
    public File downloadGitHubRepository(String repoUrl) throws IOException {
        String trimmedUrl = repoUrl == null ? "" : repoUrl.trim();
        if (!isValidGitHubUrl(trimmedUrl)) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + repoUrl);
        }

        String[] parts = extractGitHubRepoDetails(trimmedUrl);
        String owner = parts[0];
        String repo = parts[1];

        File tempDir = createSecureTempDirectory("githubRepo");
        try {
            String mainUrl = String.format("https://github.com/%s/%s/archive/refs/heads/main.zip", owner, repo);
            try {
                return getZipFile(tempDir, mainUrl);
            } catch (GitHubDownloadException e) {
                if (e.getHttpStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                    logger.info("Branch 'main' not found for {}/{}, falling back to 'master'", owner, repo);
                    String masterUrl = String.format("https://github.com/%s/%s/archive/refs/heads/master.zip", owner, repo);
                    return getZipFile(tempDir, masterUrl);
                }
                throw e;
            }
        } catch (IOException | RuntimeException e) {
            deleteRecursively(tempDir);
            throw e;
        }
    }

    private File getZipFile(File tempDir, String zipUrlString) throws IOException {
        File zipFile = new File(tempDir, "repo.zip");
        HttpURLConnection connection = null;
        try {
            URI uri = new URI(zipUrlString);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new GitHubDownloadException(
                        "Failed to download GitHub repository archive (HTTP " + status + ")",
                        status
                );
            }

            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw new ArchiveLimitExceededException(
                        "GitHub repository archive exceeds maximum allowed size of 150 MB"
                );
            }

            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(zipFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long totalRead = 0;
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    totalRead += bytesRead;
                    if (totalRead > MAX_DOWNLOAD_BYTES) {
                        throw new ArchiveLimitExceededException(
                                "GitHub repository archive exceeds maximum allowed size of 150 MB"
                        );
                    }
                    out.write(buffer, 0, bytesRead);
                }
            }
            return zipFile;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isValidGitHubUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        String pattern = "^(https?://)?(www\\.)?github\\.com/[\\w-]+/[\\w.-]+/?$";
        if (!url.matches(pattern)) {
            return false;
        }

        String[] parts = extractGitHubRepoDetails(url);
        for (String segment : parts) {
            if (segment.equals(".") || segment.equals("..") || segment.contains("..")) {
                return false;
            }
        }
        return true;
    }

    private String[] extractGitHubRepoDetails(String url) {
        String cleaned = url.replaceAll("^(https?://)?(www\\.)?github\\.com/", "");
        cleaned = cleaned.endsWith("/") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;

        String[] parts = cleaned.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid GitHub URL format. Expected format: github.com/owner/repo");
        }

        return parts;
    }

    public File saveUploadedFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Uploaded file must have a valid filename");
        }

        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            throw new IllegalArgumentException("Uploaded file must have a supported extension (.zip or .tar)");
        }

        String extension = originalFilename.substring(lastDotIndex).toLowerCase();
        if (!extension.matches("\\.(zip|tar)$")) {
            throw new IllegalArgumentException("Unsupported archive format. Only .zip and .tar archives are supported");
        }

        File tempDir = createSecureTempDirectory("uploadedProject");
        try {
            String secureFilename = UUID.randomUUID() + extension;
            File uploadedFile = new File(tempDir, secureFilename);
            file.transferTo(uploadedFile);
            return uploadedFile;
        } catch (IOException | RuntimeException e) {
            deleteRecursively(tempDir);
            throw e;
        }
    }

    /**
     * Extracts an archive file (ZIP or TAR) into a directory with Zip Bomb and traversal safeguards.
     */
    public File extractArchive(File archive) throws IOException {
        File extractedDir = new File(archive.getParent(), "extracted");
        if (!extractedDir.mkdir()) {
            throw new IOException("Failed to create extraction directory");
        }

        ExtractionBudget budget = new ExtractionBudget();
        try (InputStream fis = new FileInputStream(archive);
             BufferedInputStream bis = new BufferedInputStream(fis);
             var archiveStream = new ArchiveStreamFactory().createArchiveInputStream(bis)) {

            ArchiveEntry entry;
            while ((entry = archiveStream.getNextEntry()) != null) {
                processArchiveEntry(entry, archiveStream, extractedDir, budget);
            }
        } catch (ArchiveLimitExceededException | ArchiveExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ArchiveExtractionException("Failed to extract archive: " + archive.getName(), e);
        }

        return extractedDir;
    }

    /**
     * Recursively deletes a file or directory and its contents.
     */
    public void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            logger.warn("Failed to delete file or directory: {}", file.getAbsolutePath(), e);
        }
    }

    // === Private Helpers ===

    /**
     * Default-budget overload preserved for direct unit test reflection.
     */
    private void processArchiveEntry(ArchiveEntry entry, InputStream archiveStream, File extractedDir) throws IOException {
        processArchiveEntry(entry, archiveStream, extractedDir, new ExtractionBudget());
    }

    private void processArchiveEntry(
            ArchiveEntry entry,
            InputStream archiveStream,
            File extractedDir,
            ExtractionBudget budget
    ) throws IOException {
        budget.entryCount++;
        if (budget.entryCount > MAX_ARCHIVE_ENTRIES) {
            throw new ArchiveLimitExceededException(
                    "Archive exceeds maximum allowed entry count (" + MAX_ARCHIVE_ENTRIES + ")"
            );
        }

        if (entry instanceof TarArchiveEntry tarEntry
                && (tarEntry.isSymbolicLink() || tarEntry.isLink() || !tarEntry.isFile())) {
            return;
        }

        String entryName = entry.getName().replace("\\", "/");
        entryName = entryName.replaceAll("^([A-Za-z]:)?[/\\\\]+", "");

        File outputFile = new File(extractedDir, entryName);

        String canonicalExtractedDir = extractedDir.getCanonicalPath();
        String canonicalOutputFile = outputFile.getCanonicalPath();

        if (entry.isDirectory() && canonicalOutputFile.equals(canonicalExtractedDir)) {
            return;
        }

        if (!canonicalOutputFile.startsWith(canonicalExtractedDir + File.separator)) {
            throw new ArchiveExtractionException("Entry is outside of the target dir: " + entry.getName());
        }

        if (entry.isDirectory() || !isAnalysisPath(entryName)) {
            return;
        }

        File parent = outputFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
        }

        try (OutputStream os = Files.newOutputStream(outputFile.toPath())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long entryBytes = 0;
            int bytesRead;
            while ((bytesRead = archiveStream.read(buffer)) != -1) {
                entryBytes += bytesRead;
                budget.totalBytes += bytesRead;
                if (entryBytes > MAX_ENTRY_BYTES) {
                    throw new ArchiveLimitExceededException(
                            "Archive entry exceeds maximum allowed size (20 MB): " + entry.getName()
                    );
                }
                if (budget.totalBytes > MAX_TOTAL_EXTRACTED_BYTES) {
                    throw new ArchiveLimitExceededException(
                            "Extracted archive content exceeds maximum allowed size (300 MB)"
                    );
                }
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Keeps source and build descriptors; skips docs, assets, and other archive noise.
     */
    boolean isAnalysisPath(String entryName) {
        String normalized = entryName.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (fileName.equals("pom.xml")
                || fileName.startsWith("build.gradle")
                || fileName.startsWith("settings.gradle")) {
            return true;
        }
        return normalized.startsWith("src/") || normalized.contains("/src/");
    }

    /**
     * Creates a secure temporary directory with permissions restricted to the owner.
     */
    public File createSecureTempDirectory(String prefix) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        File tempDir;
        if (os.contains("win")) {
            File baseTemp = new File(System.getProperty("java.io.tmpdir"));
            File userTempDir;
            do {
                userTempDir = new File(baseTemp, prefix + "-" + UUID.randomUUID());
            } while (userTempDir.exists());
            if (!userTempDir.mkdir()) {
                throw new IOException("Failed to create secure temp directory: " + userTempDir.getAbsolutePath());
            }
            tempDir = userTempDir;
        } else {
            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            tempDir = Files.createTempDirectory(prefix, attr).toFile();
        }
        return tempDir;
    }
}
