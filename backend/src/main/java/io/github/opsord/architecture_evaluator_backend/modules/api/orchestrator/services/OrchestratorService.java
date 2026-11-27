package io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.services;

import io.github.opsord.architecture_evaluator_backend.modules.api.orchestrator.instances.ProjectAnalysisInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.LayerAnnotation;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.file_instance.FileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.ClassVisitor;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.FileInstanceService;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.ProcessedClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.FileAnalysisService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.gradle.GradleFileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.pom.PomFileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.GradleScannerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.PomScannerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.ScannerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.services.SrcScannerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ScannerService scannerService;
    private final PomScannerService pomScannerService;
    private final GradleScannerService gradleScannerService;
    private final SrcScannerService srcScannerService;
    private final FileAnalysisService analysisService;
    private final FileInstanceService fileInstanceService;

    public ProjectAnalysisInstance orchestrateProjectAnalysis(String projectPath) throws IOException {
        logger.info("Starting orchestration for project");

        File projectRoot = scannerService.findProjectRoot(new File(projectPath));
        if (projectRoot == null) {
            logger.warn("Project root not found");
            throw new IOException("Project root not found");
        }

        List<File> srcFiles = srcScannerService.scanSrcFolder(new File(projectRoot, "src"));
        List<FileInstance> fileInstances = srcScannerService.parseJavaFiles(srcFiles, projectRoot);
        if (fileInstances.isEmpty()) {
            logger.warn("No Java files found in the project");
            throw new IOException("No Java files found in the project");
        }

        List<FileInstance> fileInstancesWithoutTests = fileInstances.stream()
                .filter(fi -> fi.getClasses() != null && !fi.getClasses().isEmpty() && fi.getClasses().stream()
                        .noneMatch(cls -> cls.getLayerAnnotation() == LayerAnnotation.TESTING))
                .toList();

        Optional<PomFileInstance> pomFileInstance = pomScannerService.scanPomFile(projectRoot);
        Optional<GradleFileInstance> gradleFileInstance = gradleScannerService.scanGradleFile(projectRoot);

        populateClassDependencies(fileInstancesWithoutTests);

        List<ProcessedClassInstance> processedClasses = analyzeCompilationUnits(
                fileInstances,
                pomFileInstance,
                gradleFileInstance);

        ProjectAnalysisInstance projectAnalysisInstance = organizeProjectAnalysis(processedClasses);
        projectAnalysisInstance.setProjectName(projectRoot.getName());
        pomFileInstance.ifPresent(projectAnalysisInstance::setPomFile);
        gradleFileInstance.ifPresent(projectAnalysisInstance::setGradleFile);

        logger.info("Orchestration completed for project at path: {}", projectRoot.getAbsolutePath());
        return projectAnalysisInstance;
    }

    private String determineInternalBasePackage(
            Optional<PomFileInstance> pomFileInstance,
            Optional<GradleFileInstance> gradleFileInstance,
            List<FileInstance> projectCompUnits) {
        if (pomFileInstance != null && pomFileInstance.isPresent()) {
            String groupId = pomFileInstance.get().getGroupId();
            if (groupId != null && !groupId.isBlank()) {
                return groupId;
            }
        }
        if (gradleFileInstance != null && gradleFileInstance.isPresent()) {
            String group = gradleFileInstance.get().getGroup();
            if (group != null && !group.isBlank()) {
                return group;
            }
        }
        return inferCommonBasePackage(projectCompUnits);
    }

    private String inferCommonBasePackage(List<FileInstance> projectCompUnits) {
        if (projectCompUnits == null || projectCompUnits.isEmpty()) {
            return null;
        }
        List<String[]> validPackages = projectCompUnits.stream()
                .map(FileInstance::getPackageName)
                .filter(pkg -> pkg != null && !pkg.isBlank() && !"default".equals(pkg))
                .distinct()
                .map(pkg -> pkg.split("\\."))
                .toList();
        if (validPackages.isEmpty()) {
            return null;
        }
        String[] first = validPackages.get(0);
        int commonLen = first.length;
        for (int i = 1; i < validPackages.size(); i++) {
            String[] current = validPackages.get(i);
            int max = Math.min(commonLen, current.length);
            int matched = 0;
            while (matched < max && first[matched].equals(current[matched])) {
                matched++;
            }
            commonLen = matched;
        }
        if (commonLen < 2) {
            return null;
        }
        return String.join(".", java.util.Arrays.copyOf(first, commonLen));
    }

    private List<ProcessedClassInstance> analyzeCompilationUnits(
            List<FileInstance> projectCompUnits,
            Optional<PomFileInstance> pomFileInstance,
            Optional<GradleFileInstance> gradleFileInstance) {
        String internalBasePackage = determineInternalBasePackage(pomFileInstance, gradleFileInstance, projectCompUnits);

        return projectCompUnits.stream()
                .flatMap(compilationUnit -> analysisService.analyseFileInstance(
                        compilationUnit,
                        internalBasePackage,
                        pomFileInstance,
                        gradleFileInstance).stream())
                .toList();
    }

    public ProjectAnalysisInstance organizeProjectAnalysis(List<ProcessedClassInstance> processedClasses) {
        ProjectAnalysisInstance projectAnalysisInstance = new ProjectAnalysisInstance();
        projectAnalysisInstance.setEntities(filterByLayerAnnotation(processedClasses, LayerAnnotation.ENTITY));
        projectAnalysisInstance.setDocuments(filterByLayerAnnotation(processedClasses, LayerAnnotation.DOCUMENT));
        projectAnalysisInstance.setRepositories(filterByLayerAnnotation(processedClasses, LayerAnnotation.REPOSITORY));
        projectAnalysisInstance.setServices(filterByLayerAnnotation(processedClasses, LayerAnnotation.SERVICE));
        projectAnalysisInstance.setControllers(filterByLayerAnnotation(processedClasses, LayerAnnotation.CONTROLLER));
        projectAnalysisInstance.setTestClasses(filterByLayerAnnotation(processedClasses, LayerAnnotation.TESTING));
        List<ProcessedClassInstance> otherClasses = processedClasses.stream()
                .filter(pci -> pci.getClassInstance().getLayerAnnotation() == LayerAnnotation.OTHER
                        || pci.getClassInstance().getLayerAnnotation() == LayerAnnotation.UNKNOWN)
                .toList();
        projectAnalysisInstance.setOtherClasses(otherClasses);

        return projectAnalysisInstance;
    }

    private List<ProcessedClassInstance> filterByLayerAnnotation(
            List<ProcessedClassInstance> classes,
            LayerAnnotation layerAnnotation) {
        return classes.stream()
                .filter(pci -> pci.getClassInstance().getLayerAnnotation() == layerAnnotation)
                .toList();
    }

    public void populateClassDependencies(List<FileInstance> allFiles) {
        if (allFiles == null || allFiles.isEmpty()) {
            return;
        }

        Set<String> projectFqns = new LinkedHashSet<>();
        Map<String, String> ownerTopLevelFqnByNestedFqn = new HashMap<>();

        for (FileInstance file : allFiles) {
            if (file.getClasses() == null) {
                continue;
            }
            for (ClassInstance cls : file.getClasses()) {
                String clsFqn = resolveClassFqn(cls, file);
                if (clsFqn != null) {
                    projectFqns.add(clsFqn);
                    registerNestedClasses(cls.getInnerClasses(), clsFqn, ownerTopLevelFqnByNestedFqn);
                }
            }
        }

        Map<String, Set<String>> dependentsByFqn = new HashMap<>();
        for (FileInstance file : allFiles) {
            if (file.getClasses() == null) {
                continue;
            }
            for (ClassInstance cls : file.getClasses()) {
                String clsFqn = resolveClassFqn(cls, file);
                List<String> dependencies = fileInstanceService.getDependentClassFqns(
                        cls, file, projectFqns, ownerTopLevelFqnByNestedFqn);
                cls.setClassDependencies(dependencies);

                if (dependencies != null && clsFqn != null) {
                    for (String dependencyFqn : dependencies) {
                        dependentsByFqn.computeIfAbsent(dependencyFqn, ignored -> new LinkedHashSet<>()).add(clsFqn);
                    }
                }
            }
        }

        for (FileInstance file : allFiles) {
            if (file.getClasses() == null) {
                continue;
            }
            for (ClassInstance cls : file.getClasses()) {
                String clsFqn = resolveClassFqn(cls, file);
                Set<String> dependents = clsFqn != null ? dependentsByFqn.get(clsFqn) : null;
                cls.setDependentClasses(dependents != null ? new ArrayList<>(dependents) : List.of());
            }
        }
    }

    private String resolveClassFqn(ClassInstance cls, FileInstance file) {
        if (cls == null) {
            return null;
        }
        if (cls.getQualifiedName() != null && !cls.getQualifiedName().isBlank()) {
            return cls.getQualifiedName();
        }
        String pkg = file != null ? file.getPackageName() : cls.getPackageName();
        return ClassVisitor.buildQualifiedName(pkg, cls.getName());
    }

    private void registerNestedClasses(
            List<ClassInstance> nestedClasses,
            String ownerTopLevelFqn,
            Map<String, String> ownerTopLevelFqnByNestedFqn) {
        if (nestedClasses == null) {
            return;
        }
        for (ClassInstance nested : nestedClasses) {
            if (nested == null) {
                continue;
            }
            String nestedFqn = nested.getQualifiedName();
            if (nestedFqn != null && !nestedFqn.isBlank()) {
                ownerTopLevelFqnByNestedFqn.put(nestedFqn, ownerTopLevelFqn);
            }
            registerNestedClasses(nested.getInnerClasses(), ownerTopLevelFqn, ownerTopLevelFqnByNestedFqn);
        }
    }
}
