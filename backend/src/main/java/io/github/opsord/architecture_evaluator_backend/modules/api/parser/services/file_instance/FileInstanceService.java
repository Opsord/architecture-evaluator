package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.file_instance.FileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.ClassService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.ClassVisitor;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.annotation.AnnotationService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.package_part.PackageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(FileInstanceService.class);
    private static final ThreadLocal<JavaParser> PARSER = ThreadLocal.withInitial(() ->
            new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)));

    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "Appendable", "AutoCloseable", "Boolean", "Byte", "Character", "CharSequence", "Class",
            "ClassLoader", "Cloneable", "Comparable", "Double", "Enum", "Error", "Exception",
            "Float", "Integer", "Iterable", "Long", "Math", "Number", "Object", "Package",
            "Process", "ProcessBuilder", "Readable", "Record", "Runnable", "Runtime",
            "RuntimeException", "SecurityManager", "Short", "StackTraceElement", "StrictMath",
            "String", "StringBuffer", "StringBuilder", "System", "Thread", "ThreadGroup",
            "ThreadLocal", "Throwable", "Void");

    private final ClassService classService;
    private final PackageService packageService;
    private final AnnotationService annotationService;

    public FileInstance parseJavaFile(File file, File projectRoot) throws FileNotFoundException {
        logger.debug("Starting to parse file: {}", file.getAbsolutePath());
        CompilationUnit compilationUnit = PARSER.get().parse(file).getResult()
                .orElseThrow(() -> new FileNotFoundException("File not found or could not be parsed"));
        logger.debug("Successfully parsed file: {}", file.getAbsolutePath());

        FileInstance fileInstance = new FileInstance();
        fileInstance.setFileName(file.getName());

        String relativePath = projectRoot.toPath().toAbsolutePath()
                .relativize(file.toPath().toAbsolutePath())
                .toString();
        fileInstance.setFilePath(relativePath);

        FileInstanceVisitor visitor = new FileInstanceVisitor(packageService, annotationService, classService);
        compilationUnit.accept(visitor, fileInstance);

        return fileInstance;
    }

    /**
     * Legacy simple-name lookup preserved for backwards compatibility with existing unit tests.
     */
    public List<String> getDependentClassNamesFromClass(ClassInstance classInstance, Set<String> projectClassNames) {
        if (classInstance == null || classInstance.getUsedClasses() == null) {
            return List.of();
        }
        String selfName = classInstance.getName();
        return classInstance.getUsedClasses().stream()
                .distinct()
                .filter(name -> !name.equals(selfName))
                .filter(projectClassNames::contains)
                .toList();
    }

    /**
     * Resolves project dependencies for a class as Fully Qualified Names (FQNs),
     * mapping references to nested types to their owning top-level FQN and excluding self-references.
     */
    public List<String> getDependentClassFqns(
            ClassInstance classInstance,
            FileInstance fileInstance,
            Set<String> projectFqns,
            Map<String, String> ownerTopLevelFqnByNestedFqn) {
        if (classInstance == null || projectFqns == null || projectFqns.isEmpty()) {
            return List.of();
        }

        String packageName = fileInstance != null ? fileInstance.getPackageName() : classInstance.getPackageName();
        String selfFqn = classInstance.getQualifiedName() != null && !classInstance.getQualifiedName().isBlank()
                ? classInstance.getQualifiedName()
                : ClassVisitor.buildQualifiedName(packageName, classInstance.getName());

        List<String> importedPackages = fileInstance != null && fileInstance.getImportedPackages() != null
                ? fileInstance.getImportedPackages()
                : Collections.emptyList();
        List<String> staticImports = fileInstance != null && fileInstance.getStaticImports() != null
                ? fileInstance.getStaticImports()
                : Collections.emptyList();

        List<String> explicitImports = new ArrayList<>();
        List<String> wildcardPrefixes = new ArrayList<>();
        for (String imp : importedPackages) {
            if (imp == null || imp.isBlank()) {
                continue;
            }
            if (imp.endsWith(".*")) {
                wildcardPrefixes.add(imp.substring(0, imp.length() - 2));
            } else {
                explicitImports.add(imp);
            }
        }

        Set<String> resolved = new LinkedHashSet<>();

        // Resolve dependencies introduced via static imports (e.g. import static com.acme.Utils.helper;)
        for (String staticImp : staticImports) {
            if (staticImp == null || staticImp.isBlank()) {
                continue;
            }
            String ownerCandidate = staticImp.endsWith(".*")
                    ? staticImp.substring(0, staticImp.length() - 2)
                    : stripLastSegment(staticImp);
            String ownerFqn = matchProjectFqn(ownerCandidate, projectFqns, ownerTopLevelFqnByNestedFqn);
            if (ownerFqn != null && !ownerFqn.equals(selfFqn)) {
                resolved.add(ownerFqn);
            }
        }

        List<String> usedClasses = classInstance.getUsedClasses();
        if (usedClasses != null) {
            for (String ref : usedClasses) {
                if (ref == null || ref.isBlank()) {
                    continue;
                }
                String targetFqn = resolveReference(
                        ref, packageName, explicitImports, wildcardPrefixes, projectFqns, ownerTopLevelFqnByNestedFqn);
                if (targetFqn != null && !targetFqn.equals(selfFqn)) {
                    resolved.add(targetFqn);
                }
            }
        }

        return new ArrayList<>(resolved);
    }

    private String resolveReference(
            String ref,
            String packageName,
            List<String> explicitImports,
            List<String> wildcardPrefixes,
            Set<String> projectFqns,
            Map<String, String> ownerTopLevelFqnByNestedFqn) {
        // 1. Direct match (already fully qualified or nested FQN)
        String direct = matchProjectFqn(ref, projectFqns, ownerTopLevelFqnByNestedFqn);
        if (direct != null) {
            return direct;
        }

        int firstDot = ref.indexOf('.');
        String firstSegment = firstDot > 0 ? ref.substring(0, firstDot) : ref;
        String suffix = firstDot > 0 ? ref.substring(firstDot) : "";

        // 2. Explicit imports (exact match or first-segment match for "Order.Status")
        for (String imp : explicitImports) {
            if (imp.equals(ref) || imp.endsWith("." + ref)) {
                String match = matchProjectFqn(imp, projectFqns, ownerTopLevelFqnByNestedFqn);
                if (match != null) {
                    return match;
                }
            }
            if (firstDot > 0 && (imp.equals(firstSegment) || imp.endsWith("." + firstSegment))) {
                String nestedCandidate = imp + suffix;
                String match = matchProjectFqn(nestedCandidate, projectFqns, ownerTopLevelFqnByNestedFqn);
                if (match != null) {
                    return match;
                }
            }
        }

        // 3. Same package
        String samePkgCandidate = ClassVisitor.buildQualifiedName(packageName, ref);
        String samePkgMatch = matchProjectFqn(samePkgCandidate, projectFqns, ownerTopLevelFqnByNestedFqn);
        if (samePkgMatch != null) {
            return samePkgMatch;
        }

        // 4. Wildcard imports (guard against java.lang shadowing)
        if (!JAVA_LANG_TYPES.contains(firstSegment)) {
            for (String prefix : wildcardPrefixes) {
                String wildcardCandidate = prefix + "." + ref;
                String wildcardMatch = matchProjectFqn(wildcardCandidate, projectFqns, ownerTopLevelFqnByNestedFqn);
                if (wildcardMatch != null) {
                    return wildcardMatch;
                }
            }
        }

        return null;
    }

    private String matchProjectFqn(
            String candidate,
            Set<String> projectFqns,
            Map<String, String> ownerTopLevelFqnByNestedFqn) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (projectFqns.contains(candidate)) {
            return candidate;
        }
        if (ownerTopLevelFqnByNestedFqn != null) {
            return ownerTopLevelFqnByNestedFqn.get(candidate);
        }
        return null;
    }

    private String stripLastSegment(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot > 0 ? fqn.substring(0, lastDot) : fqn;
    }
}
