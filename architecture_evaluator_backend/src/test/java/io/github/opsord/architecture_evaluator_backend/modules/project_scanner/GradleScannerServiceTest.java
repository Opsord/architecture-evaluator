package io.github.opsord.architecture_evaluator_backend.modules.project_scanner;

import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.gradle.GradleFileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.GradleScannerService;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GradleScannerServiceTest {

    private GradleScannerService gradleScannerService;

    @BeforeEach
    void setUp() {
        gradleScannerService = new GradleScannerService();
    }

    @Test
    void testScanGradleFile_validBuildGradle() throws Exception {
        File tempDir = java.nio.file.Files.createTempDirectory("gradleproject").toFile();
        File gradle = new File(tempDir, "build.gradle");
        try (FileWriter fw = new FileWriter(gradle)) {
            fw.write("""
                group = 'com.example'
                version = '1.0.0'
                description = 'Test Gradle Project'
                dependencies {
                  implementation 'org.springframework:spring-core:5.3.0'
                }
                """);
        }
        Optional<GradleFileInstance> result = gradleScannerService.scanGradleFile(tempDir);
        assertTrue(result.isPresent());
        GradleFileInstance gradleFileInstance = result.get();
        assertEquals("com.example", gradleFileInstance.getGroup());
        assertEquals("1.0.0", gradleFileInstance.getVersion());
        assertEquals("Test Gradle Project", gradleFileInstance.getDescription());
        assertNotNull(gradleFileInstance.getDependencies());
        assertFalse(gradleFileInstance.getDependencies().isEmpty());
        assertEquals("org.springframework", gradleFileInstance.getDependencies().get(0).getGroup());
    }

    @Test
    void testScanGradleFile_noGradleFile() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        Optional<GradleFileInstance> result = gradleScannerService.scanGradleFile(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testScanGradleFile_kotlinDslToolchainBomAndSettings() throws Exception {
        File tempDir = java.nio.file.Files.createTempDirectory("gradlekts").toFile();
        try (FileWriter fw = new FileWriter(new File(tempDir, "build.gradle.kts"))) {
            fw.write("""
                plugins {
                    id("org.springframework.boot") version "3.4.5"
                }
                // group = "com.ignored"
                project.group = "com.acme.app"
                version = "2.1.0"
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter-web")
                    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
                }
                """);
        }
        try (FileWriter fw = new FileWriter(new File(tempDir, "settings.gradle.kts"))) {
            fw.write("rootProject.name = \"acme-service\"\n");
        }

        Optional<GradleFileInstance> result = gradleScannerService.scanGradleFile(tempDir);
        assertTrue(result.isPresent());
        GradleFileInstance gradle = result.get();
        assertEquals("com.acme.app", gradle.getGroup());
        assertEquals("acme-service", gradle.getName());
        assertEquals("2.1.0", gradle.getVersion());
        assertEquals("21", gradle.getJavaVersion());
        assertEquals(2, gradle.getDependencies().size());
        assertEquals("org.springframework.boot", gradle.getDependencies().get(0).getGroup());
        assertNull(gradle.getDependencies().get(0).getVersion());
        assertEquals("2.18.0", gradle.getDependencies().get(1).getVersion());
    }

    @Test
    void testScanGradleFile_javaVersionEnumAndLegacyVersion18() throws Exception {
        File dir17 = java.nio.file.Files.createTempDirectory("gradle17").toFile();
        try (FileWriter fw = new FileWriter(new File(dir17, "build.gradle"))) {
            fw.write("sourceCompatibility = JavaVersion.VERSION_17\n");
        }
        assertEquals("17", gradleScannerService.scanGradleFile(dir17).orElseThrow().getJavaVersion());

        File dir8 = java.nio.file.Files.createTempDirectory("gradle8").toFile();
        try (FileWriter fw = new FileWriter(new File(dir8, "build.gradle"))) {
            fw.write("sourceCompatibility = JavaVersion.VERSION_1_8\n");
        }
        assertEquals("8", gradleScannerService.scanGradleFile(dir8).orElseThrow().getJavaVersion());
    }
}