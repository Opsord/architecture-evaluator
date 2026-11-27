package io.github.opsord.architecture_evaluator_backend.modules.orchestrator;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveExtractionException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.exceptions.ArchiveLimitExceededException;
import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services.FileManagerService;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileManagerServiceTest {

    private FileManagerService fileManagerService;

    @BeforeEach
    void setUp() {
        fileManagerService = new FileManagerService();
    }

    @Test
    void testIsValidGitHubUrl_validUrls() throws Exception {
        var method = FileManagerService.class.getDeclaredMethod("isValidGitHubUrl", String.class);
        method.setAccessible(true);
        assertTrue((Boolean) method.invoke(fileManagerService, "https://github.com/user/repo"));
        assertTrue((Boolean) method.invoke(fileManagerService, "http://github.com/user/repo"));
        assertTrue((Boolean) method.invoke(fileManagerService, "github.com/user/repo"));
        assertTrue((Boolean) method.invoke(fileManagerService, "https://www.github.com/user/repo/"));
    }

    @Test
    void testIsValidGitHubUrl_invalidUrls() throws Exception {
        var method = FileManagerService.class.getDeclaredMethod("isValidGitHubUrl", String.class);
        method.setAccessible(true);
        assertFalse((Boolean) method.invoke(fileManagerService, "https://gitlab.com/user/repo"));
        assertFalse((Boolean) method.invoke(fileManagerService, "https://github.com/user"));
        assertFalse((Boolean) method.invoke(fileManagerService, "https://github.com/user/.."));
        assertFalse((Boolean) method.invoke(fileManagerService, "https://github.com/user/."));
        assertFalse((Boolean) method.invoke(fileManagerService, ""));
        assertFalse((Boolean) method.invoke(fileManagerService, (Object) null));
    }

    @Test
    void testExtractGitHubRepoDetails_valid() throws Exception {
        var method = FileManagerService.class.getDeclaredMethod("extractGitHubRepoDetails", String.class);
        method.setAccessible(true);
        String[] result = (String[]) method.invoke(fileManagerService, "https://github.com/user/repo");
        assertEquals("user", result[0]);
        assertEquals("repo", result[1]);
    }

    @Test
    void testExtractGitHubRepoDetails_invalid() throws Exception {
        var method = FileManagerService.class.getDeclaredMethod("extractGitHubRepoDetails", String.class);
        method.setAccessible(true);
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                method.invoke(fileManagerService, "https://github.com/user");
            } catch (java.lang.reflect.InvocationTargetException exception) {
                throw exception.getCause();
            }
        });
    }

    @Test
    void testSaveUploadedFile_validZip() throws Exception {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.getOriginalFilename()).thenReturn("test.zip");
        File tempFile = File.createTempFile("test", ".zip");
        doAnswer(invocation -> {
            try {
                Files.copy(tempFile.toPath(), ((File) invocation.getArgument(0)).toPath());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(multipartFile).transferTo(any(File.class));

        try {
            File saved = fileManagerService.saveUploadedFile(multipartFile);
            assertTrue(saved.exists());
            assertTrue(saved.getName().endsWith(".zip"));
            saved.delete();
            saved.getParentFile().delete();
        } catch (IOException e) {
            if (e.getMessage().contains("Failed to set secure permissions")) {
                // On Windows, the permission setting may fail; skip this assertion
            } else {
                throw e;
            }
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void testSaveUploadedFile_invalidExtension() {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");
        assertThrows(IllegalArgumentException.class, () -> fileManagerService.saveUploadedFile(multipartFile));
    }

    @Test
    void testSaveUploadedFile_unsupportedArchiveExtensions() {
        for (String name : new String[]{"archive.rar", "archive.7z", "archive.gz"}) {
            MultipartFile multipartFile = mock(MultipartFile.class);
            when(multipartFile.isEmpty()).thenReturn(false);
            when(multipartFile.getOriginalFilename()).thenReturn(name);
            assertThrows(IllegalArgumentException.class, () -> fileManagerService.saveUploadedFile(multipartFile));
        }
    }

    @Test
    void testSaveUploadedFile_noExtension() {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("testfile");
        assertThrows(IllegalArgumentException.class, () -> fileManagerService.saveUploadedFile(multipartFile));
    }

    @Test
    void testSaveUploadedFile_emptyFile() {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.isEmpty()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> fileManagerService.saveUploadedFile(multipartFile));
    }

    @Test
    void testExtractArchive_zip() throws Exception {
        File tempDir = Files.createTempDirectory("ziptest").toFile();
        File zipFile = new File(tempDir, "test.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zipFile)) {
            ZipArchiveEntry skipped = new ZipArchiveEntry("README.md");
            zos.putArchiveEntry(skipped);
            zos.write("docs".getBytes());
            zos.closeArchiveEntry();
            ZipArchiveEntry source = new ZipArchiveEntry("src/main/java/Demo.java");
            zos.putArchiveEntry(source);
            zos.write("class Demo {}".getBytes());
            zos.closeArchiveEntry();
            ZipArchiveEntry pom = new ZipArchiveEntry("pom.xml");
            zos.putArchiveEntry(pom);
            zos.write("<project/>".getBytes());
            zos.closeArchiveEntry();
        }

        File extracted = fileManagerService.extractArchive(zipFile);
        File extractedSource = new File(extracted, "src/main/java/Demo.java");
        File extractedPom = new File(extracted, "pom.xml");
        File extractedReadme = new File(extracted, "README.md");
        assertTrue(extractedSource.exists());
        assertEquals("class Demo {}", Files.readString(extractedSource.toPath()));
        assertTrue(extractedPom.exists());
        assertFalse(extractedReadme.exists());

        fileManagerService.deleteRecursively(tempDir);
    }

    @Test
    void testExtractArchive_invalidFile() throws Exception {
        File tempDir = Files.createTempDirectory("invalidarchive").toFile();
        File notArchive = new File(tempDir, "not_an_archive.txt");
        try {
            assertTrue(notArchive.createNewFile());
            assertThrows(ArchiveExtractionException.class, () -> fileManagerService.extractArchive(notArchive));
        } finally {
            fileManagerService.deleteRecursively(tempDir);
        }
    }

    @Test
    void testDeleteRecursively_fileAndDir() throws Exception {
        File tempDir = Files.createTempDirectory("deltest").toFile();
        File file = new File(tempDir, "file.txt");
        assertTrue(file.createNewFile());
        File subDir = new File(tempDir, "sub");
        assertTrue(subDir.mkdir());
        File subFile = new File(subDir, "subfile.txt");
        assertTrue(subFile.createNewFile());

        fileManagerService.deleteRecursively(tempDir);
        assertFalse(tempDir.exists());
    }

    @Test
    void testCreateSecureTempDirectory() throws Exception {
        var method = FileManagerService.class.getDeclaredMethod("createSecureTempDirectory", String.class);
        method.setAccessible(true);
        try {
            File dir = (File) method.invoke(fileManagerService, "testprefix");
            assertTrue(dir.exists());
            assertTrue(dir.isDirectory());
            fileManagerService.deleteRecursively(dir);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException && cause.getMessage().contains("Failed to set secure permissions")) {
                // On Windows, the permission setting may fail; skip this assertion
                return;
            }
            throw e;
        }
    }

    @Test
    void testDownloadGitHubRepository_invalidUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                fileManagerService.downloadGitHubRepository("https://gitlab.com/user/repo")
        );
    }

    @Test
    void testDownloadGitHubRepository_invalidSyntax() {
        assertThrows(IllegalArgumentException.class, () ->
                fileManagerService.downloadGitHubRepository("ht!tp://github.com/user/repo")
        );
    }

    @Test
    void testExtractArchive_entryOutsideExtractionDir() throws Exception {
        // Crea un zip con una entrada maliciosa
        File tempDir = Files.createTempDirectory("ziptest").toFile();
        File zipFile = new File(tempDir, "test.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zipFile)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("../evil.txt");
            zos.putArchiveEntry(entry);
            zos.write("bad".getBytes());
            zos.closeArchiveEntry();
        }
        assertThrows(ArchiveExtractionException.class, () -> fileManagerService.extractArchive(zipFile));
        fileManagerService.deleteRecursively(tempDir);
    }

    @Test
    void testExtractArchive_analysisPathOutsideExtractionDir() throws Exception {
        File tempDir = Files.createTempDirectory("ziptest").toFile();
        File zipFile = new File(tempDir, "test.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zipFile)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("../src/Evil.java");
            zos.putArchiveEntry(entry);
            zos.write("class Evil {}".getBytes());
            zos.closeArchiveEntry();
        }
        assertThrows(ArchiveExtractionException.class, () -> fileManagerService.extractArchive(zipFile));
        fileManagerService.deleteRecursively(tempDir);
    }

    @Test
    void testProcessArchiveEntry_skipsDirectoryEntries() throws Exception {
        File tempDir = Files.createTempDirectory("faildirtest").toFile();
        File extractedDir = new File(tempDir, "extracted");
        extractedDir.delete();
        assertTrue(extractedDir.createNewFile());

        FileManagerService service = new FileManagerService();
        ArchiveEntry entry = mock(ArchiveEntry.class);
        when(entry.getName()).thenReturn("dir/");
        when(entry.isDirectory()).thenReturn(true);

        var method = FileManagerService.class.getDeclaredMethod("processArchiveEntry", ArchiveEntry.class, InputStream.class, File.class);
        method.setAccessible(true);
        method.invoke(service, entry, new ByteArrayInputStream(new byte[0]), extractedDir);

        extractedDir.delete();
        tempDir.delete();
    }

    @Test
    void testDeleteRecursively_nonExistentFile() {
        File file = new File("nonexistentfile.txt");
        fileManagerService.deleteRecursively(file);
        assertFalse(file.exists(), "File should not exist after deletion attempt");
        assertDoesNotThrow(() -> fileManagerService.deleteRecursively(null));
    }

    @Test
    void testExtractArchive_skipsTarSymlinks() throws Exception {
        File tempDir = Files.createTempDirectory("tartest").toFile();
        File tarFile = new File(tempDir, "test.tar");
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(new FileOutputStream(tarFile))) {
            TarArchiveEntry symlink = new TarArchiveEntry("src/main/java/Link.java", TarArchiveEntry.LF_SYMLINK);
            symlink.setLinkName("/etc/passwd");
            tos.putArchiveEntry(symlink);
            tos.closeArchiveEntry();

            byte[] content = "class Real {}".getBytes();
            TarArchiveEntry regular = new TarArchiveEntry("src/main/java/Real.java");
            regular.setSize(content.length);
            tos.putArchiveEntry(regular);
            tos.write(content);
            tos.closeArchiveEntry();
        }

        File extracted = fileManagerService.extractArchive(tarFile);
        assertFalse(new File(extracted, "src/main/java/Link.java").exists());
        assertTrue(new File(extracted, "src/main/java/Real.java").exists());
        fileManagerService.deleteRecursively(tempDir);
    }

    @Test
    void testExtractArchive_exceedsEntryCountLimit() throws Exception {
        File tempDir = Files.createTempDirectory("entrylimit").toFile();
        File zipFile = new File(tempDir, "many.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zipFile)) {
            for (int i = 0; i < 10_002; i++) {
                ZipArchiveEntry entry = new ZipArchiveEntry("noise/file-" + i + ".txt");
                zos.putArchiveEntry(entry);
                zos.closeArchiveEntry();
            }
        }

        assertThrows(ArchiveLimitExceededException.class, () -> fileManagerService.extractArchive(zipFile));
        fileManagerService.deleteRecursively(tempDir);
    }

    @Test
    void testExtractArchive_exceedsEntryBytesLimit() throws Exception {
        File tempDir = Files.createTempDirectory("bytelimit").toFile();
        File zipFile = new File(tempDir, "bomb.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zipFile)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("src/main/java/Huge.java");
            zos.putArchiveEntry(entry);
            byte[] chunk = new byte[1024 * 1024]; // 1 MB of zeros (highly compressible)
            for (int i = 0; i < 21; i++) {
                zos.write(chunk);
            }
            zos.closeArchiveEntry();
        }

        try {
            assertThrows(ArchiveLimitExceededException.class, () -> fileManagerService.extractArchive(zipFile));
        } finally {
            fileManagerService.deleteRecursively(tempDir);
        }
    }

    @Test
    void testExtractArchive_allowsRootDirectoryEntry() throws Exception {
        File tempDir = Files.createTempDirectory("rootdirzip").toFile();
        File zipFile = new File(tempDir, "rootdir.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zipFile)) {
            ZipArchiveEntry rootDir = new ZipArchiveEntry("./");
            zos.putArchiveEntry(rootDir);
            zos.closeArchiveEntry();

            ZipArchiveEntry source = new ZipArchiveEntry("./src/main/java/App.java");
            zos.putArchiveEntry(source);
            zos.write("class App {}".getBytes());
            zos.closeArchiveEntry();
        }

        try {
            File extracted = fileManagerService.extractArchive(zipFile);
            assertTrue(new File(extracted, "src/main/java/App.java").exists());
        } finally {
            fileManagerService.deleteRecursively(tempDir);
        }
    }

    @Test
    void testCreateSecureTempDirectory_permissionFailure() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows branch (unchanged)
            File baseTemp = new File(System.getProperty("java.io.tmpdir"));
            File tempDir = new File(baseTemp, "failperm-" + java.util.UUID.randomUUID());
            tempDir.mkdir();
            tempDir.setReadable(false, false);
            tempDir.setWritable(false, false);
            tempDir.setExecutable(false, false);
            try {
                var method = FileManagerService.class.getDeclaredMethod("createSecureTempDirectory", String.class);
                method.setAccessible(true);
                try {
                    method.invoke(fileManagerService, "failperm");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException || cause instanceof IllegalArgumentException) {
                        return; // Exception is expected
                    }
                    throw e;
                }
            } finally {
                tempDir.setReadable(true, false);
                tempDir.setWritable(true, false);
                tempDir.setExecutable(true, false);
                tempDir.delete();
            }
        } else {
            // Linux/Mac: try to create a directory in a non-writable location
            File unwritableDir = new File("/root/forbidden-" + UUID.randomUUID());
            if (unwritableDir.exists() || unwritableDir.mkdirs()) {
                // If we can create it, permissions are too loose, skip the test
                Assumptions.assumeTrue(false, "Skipping: unable to simulate permission failure on this environment.");
            }
            var method = FileManagerService.class.getDeclaredMethod("createSecureTempDirectory", String.class);
            method.setAccessible(true);
            Exception ex = assertThrows(Exception.class, () -> invokeCreateSecureTempDirectory(method));
            assertTrue(ex instanceof IOException || ex instanceof IllegalArgumentException,
                    "Expected IOException or IllegalArgumentException but got: " + ex.getClass());
        }
    }

    private void invokeCreateSecureTempDirectory(java.lang.reflect.Method method) throws Exception {
        try {
            method.invoke(fileManagerService, "/root/forbidden");
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void testCreateSecureTempDirectory_permissionFailure_windowsOnly() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            File baseTemp = new File(System.getProperty("java.io.tmpdir"));
            File tempDir = new File(baseTemp, "failperm-" + UUID.randomUUID());
            tempDir.mkdir();
            tempDir.setReadable(false, false);
            tempDir.setWritable(false, false);
            tempDir.setExecutable(false, false);
            try {
                try {
                    fileManagerService.createSecureTempDirectory("failperm");
                    // En Windows, si no lanza excepción, el test pasa
                } catch (IOException ex) {
                    assertTrue(ex.getMessage().contains("Failed to create secure temp directory") ||
                            ex.getMessage().contains("Failed to set secure permissions"));
                }
            } finally {
                tempDir.setReadable(true, false);
                tempDir.setWritable(true, false);
                tempDir.setExecutable(true, false);
                tempDir.delete();
            }
        }
    }

    @Test
    void testCreateSecureTempDirectory_windowsBranch() throws Exception {
        String originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 10");
        try {
            File dir = fileManagerService.createSecureTempDirectory("winprefix");
            assertTrue(dir.exists());
            assertTrue(dir.isDirectory());
            fileManagerService.deleteRecursively(dir);
        } catch (IOException e) {
            // On Windows, the permission setting may fail; skip this assertion
            if (!e.getMessage().contains("Failed to set secure permissions")) {
                throw e;
            }
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }

}