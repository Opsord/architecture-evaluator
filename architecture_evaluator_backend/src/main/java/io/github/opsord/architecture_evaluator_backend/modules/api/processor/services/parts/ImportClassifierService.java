package io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.file_instance.FileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.parts.ImportCategory;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.gradle.GradleDependencyInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.gradle.GradleFileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.pom.ParentSectionDTO;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.pom.PomDependencyInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.project_scanner.instances.pom.PomFileInstance;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ImportClassifierService {

    public Map<ImportCategory, List<String>> classifyDependencies(
            Optional<PomFileInstance> pomFileInstance,
            FileInstance fileInstance,
            String internalBasePackage) {
        return classifyDependencies(pomFileInstance, Optional.empty(), fileInstance, internalBasePackage);
    }

    public Map<ImportCategory, List<String>> classifyDependencies(
            Optional<PomFileInstance> pomFileInstance,
            Optional<GradleFileInstance> gradleFileInstance,
            FileInstance fileInstance,
            String internalBasePackage) {
        Map<ImportCategory, List<String>> classifiedDependencies = new EnumMap<>(ImportCategory.class);
        if (fileInstance == null) {
            return classifiedDependencies;
        }

        ParentSectionDTO parentSection = pomFileInstance != null
                ? pomFileInstance.map(PomFileInstance::getParentSection).orElse(null)
                : null;

        List<String> knownBasePackages = collectKnownBasePackages(pomFileInstance, gradleFileInstance);

        classifyList(fileInstance.getImportedPackages(), knownBasePackages, parentSection, internalBasePackage, classifiedDependencies);
        classifyList(fileInstance.getStaticImports(), knownBasePackages, parentSection, internalBasePackage, classifiedDependencies);

        return classifiedDependencies;
    }

    private void classifyList(
            List<String> imports,
            List<String> knownBasePackages,
            ParentSectionDTO parentSection,
            String internalBasePackage,
            Map<ImportCategory, List<String>> classifiedDependencies) {
        if (imports == null) {
            return;
        }
        for (String importName : imports) {
            if (importName == null || importName.isBlank()) {
                continue;
            }
            ImportCategory category = classifyWithBasePackages(
                    importName, knownBasePackages, parentSection, internalBasePackage);
            classifiedDependencies.computeIfAbsent(category, k -> new ArrayList<>()).add(importName);
        }
    }

    private List<String> collectKnownBasePackages(
            Optional<PomFileInstance> pomFileInstance,
            Optional<GradleFileInstance> gradleFileInstance) {
        List<String> basePackages = new ArrayList<>();
        if (pomFileInstance != null && pomFileInstance.isPresent() && pomFileInstance.get().getDependencies() != null) {
            for (PomDependencyInstance dep : pomFileInstance.get().getDependencies()) {
                if (dep != null && dep.getBasePackage() != null && !dep.getBasePackage().isBlank()) {
                    basePackages.add(dep.getBasePackage());
                }
            }
        }
        if (gradleFileInstance != null && gradleFileInstance.isPresent() && gradleFileInstance.get().getDependencies() != null) {
            for (GradleDependencyInstance dep : gradleFileInstance.get().getDependencies()) {
                if (dep != null && dep.getGroup() != null && !dep.getGroup().isBlank()) {
                    basePackages.add(dep.getGroup());
                }
            }
        }
        return basePackages;
    }

    public ImportCategory classify(
            String importName,
            List<PomDependencyInstance> knownDependencies,
            ParentSectionDTO parentSection,
            String internalBasePackage) {
        List<String> basePackages = knownDependencies == null
                ? Collections.emptyList()
                : knownDependencies.stream()
                        .filter(Objects::nonNull)
                        .map(PomDependencyInstance::getBasePackage)
                        .filter(bp -> bp != null && !bp.isBlank())
                        .toList();
        return classifyWithBasePackages(importName, basePackages, parentSection, internalBasePackage);
    }

    private ImportCategory classifyWithBasePackages(
            String importName,
            List<String> knownBasePackages,
            ParentSectionDTO parentSection,
            String internalBasePackage) {
        if (importName == null || importName.isBlank()) {
            return ImportCategory.EXTERNAL_UNKNOWN;
        }
        if (importName.startsWith("java.") || importName.startsWith("javax.") || importName.startsWith("jakarta.")) {
            return ImportCategory.JAVA_STANDARD;
        } else if (importName.startsWith("spring.") || importName.startsWith("org.springframework.")) {
            return ImportCategory.SPRING;
        } else if (matchesPackagePrefix(importName, internalBasePackage)) {
            return ImportCategory.INTERNAL;
        } else if (matchesParentSection(importName, parentSection)) {
            return ImportCategory.PARENT_DEPENDENCY;
        } else if (matchesAnyBasePackage(importName, knownBasePackages)) {
            return ImportCategory.EXTERNAL_KNOWN;
        } else {
            return ImportCategory.EXTERNAL_UNKNOWN;
        }
    }

    private boolean matchesPackagePrefix(String importName, String base) {
        if (base == null || base.isBlank()) {
            return false;
        }
        return importName.equals(base) || importName.startsWith(base + ".");
    }

    private boolean matchesParentSection(String importName, ParentSectionDTO parentSection) {
        if (parentSection == null) {
            return false;
        }
        return matchesPackagePrefix(importName, parentSection.getGroupId())
                || matchesPackagePrefix(importName, parentSection.getArtifactId());
    }

    private boolean matchesAnyBasePackage(String importName, List<String> basePackages) {
        for (String basePackage : basePackages) {
            if (matchesPackagePrefix(importName, basePackage)) {
                return true;
            }
        }
        return false;
    }
}
