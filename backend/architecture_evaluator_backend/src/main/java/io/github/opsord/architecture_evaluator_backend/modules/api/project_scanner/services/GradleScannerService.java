package io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services;

import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.gradle.GradleDependencyInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.gradle.GradleFileInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GradleScannerService {

    private static final Logger logger = LoggerFactory.getLogger(GradleScannerService.class);

    private static final List<String> GRADLE_FILES = List.of("build.gradle", "build.gradle.kts");
    private static final List<String> SETTINGS_FILES = List.of("settings.gradle", "settings.gradle.kts");

    private static final Pattern GROUP_PATTERN = Pattern.compile(
            "^(?:project\\.)?group(?:\\s*=\\s*|\\s+)['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^(?:project\\.)?version(?:\\s*=\\s*|\\s+)['\"]([^'\"]+)['\"]");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
            "^(?:project\\.)?description(?:\\s*=\\s*|\\s+)['\"]([^'\"]+)['\"]");
    private static final Pattern ROOT_PROJECT_NAME_PATTERN = Pattern.compile(
            "^rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");

    private static final Pattern TOOLCHAIN_JAVA_PATTERN = Pattern.compile(
            "JavaLanguageVersion\\.of\\(\\s*(\\d+)\\s*\\)");
    private static final Pattern JAVA_VERSION_ENUM_PATTERN = Pattern.compile(
            "JavaVersion\\.VERSION_(?:1_)?(\\d+)");
    private static final Pattern SOURCE_COMPATIBILITY_PATTERN = Pattern.compile(
            "sourceCompatibility\\s*=?\\s*['\"]?(\\d+(?:\\.\\d+)?)['\"]?");

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "^(?:implementation|api|compile|testImplementation|runtimeOnly|compileOnly|annotationProcessor|testRuntimeOnly)"
                    + "(?:\\s*\\(\\s*|\\s+)['\"]([^:'\"]+):([^:'\"]+)(?::([^'\"]+))?['\"]\\s*\\)?");

    public Optional<GradleFileInstance> scanGradleFile(File projectDirectory) {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            logger.warn("Invalid project directory: {}", projectDirectory != null ? projectDirectory.getAbsolutePath() : "null");
            return Optional.empty();
        }

        File gradleFile = findFile(projectDirectory, GRADLE_FILES);
        if (gradleFile == null) {
            logger.warn("No build.gradle or build.gradle.kts found in directory: {}", projectDirectory.getAbsolutePath());
            return Optional.empty();
        }

        try {
            List<String> lines = Files.readAllLines(gradleFile.toPath());
            GradleFileInstance gradleFileInstance = new GradleFileInstance();
            gradleFileInstance.setGroup(extractFirstMatch(lines, GROUP_PATTERN));
            String projectName = extractFirstMatch(lines, ROOT_PROJECT_NAME_PATTERN);
            if (projectName == null) {
                projectName = extractRootProjectNameFromSettings(projectDirectory);
            }
            gradleFileInstance.setName(projectName);
            gradleFileInstance.setVersion(extractFirstMatch(lines, VERSION_PATTERN));
            gradleFileInstance.setDescription(extractFirstMatch(lines, DESCRIPTION_PATTERN));
            gradleFileInstance.setJavaVersion(extractJavaVersion(lines));
            gradleFileInstance.setDependencies(extractDependencies(lines));
            return Optional.of(gradleFileInstance);
        } catch (IOException e) {
            logger.error("Error reading gradle file: {}", gradleFile.getAbsolutePath(), e);
            return Optional.empty();
        }
    }

    private File findFile(File dir, List<String> candidates) {
        for (String fileName : candidates) {
            File file = new File(dir, fileName);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private String extractRootProjectNameFromSettings(File projectDirectory) {
        File settingsFile = findFile(projectDirectory, SETTINGS_FILES);
        if (settingsFile == null) {
            return null;
        }
        try {
            return extractFirstMatch(Files.readAllLines(settingsFile.toPath()), ROOT_PROJECT_NAME_PATTERN);
        } catch (IOException e) {
            logger.warn("Error reading settings gradle file: {}", settingsFile.getAbsolutePath(), e);
            return null;
        }
    }

    private String extractFirstMatch(List<String> lines, Pattern pattern) {
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String extractJavaVersion(List<String> lines) {
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            Matcher toolchainMatcher = TOOLCHAIN_JAVA_PATTERN.matcher(line);
            if (toolchainMatcher.find()) {
                return toolchainMatcher.group(1);
            }
            Matcher enumMatcher = JAVA_VERSION_ENUM_PATTERN.matcher(line);
            if (enumMatcher.find()) {
                return enumMatcher.group(1);
            }
            Matcher compatMatcher = SOURCE_COMPATIBILITY_PATTERN.matcher(line);
            if (compatMatcher.find()) {
                return compatMatcher.group(1);
            }
        }
        return null;
    }

    private List<GradleDependencyInstance> extractDependencies(List<String> lines) {
        List<GradleDependencyInstance> dependencies = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            Matcher matcher = DEPENDENCY_PATTERN.matcher(line);
            if (matcher.find()) {
                dependencies.add(new GradleDependencyInstance(
                        matcher.group(1), matcher.group(2), matcher.group(3)));
            }
        }
        return dependencies;
    }
}
