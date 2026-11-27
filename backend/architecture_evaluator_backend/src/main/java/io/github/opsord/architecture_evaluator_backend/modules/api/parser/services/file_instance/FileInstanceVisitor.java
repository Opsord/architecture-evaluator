package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.file_instance.FileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.ClassService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.annotation.AnnotationService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.package_part.PackageService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class FileInstanceVisitor extends VoidVisitorAdapter<FileInstance> {

    private final PackageService packageService;
    private final AnnotationService annotationService;
    private final ClassService classService;

    @Override
    public void visit(CompilationUnit compilationUnit, FileInstance fileInstance) {
        super.visit(compilationUnit, fileInstance);

        // --- File Information ---
        fileInstance.setPackageName(
                compilationUnit.getPackageDeclaration().map(NodeWithName::getNameAsString)
                        .orElse("default"));

        // --- File-level Annotations & Imports ---
        List<String> importedPackages = packageService.getImportedPackages(compilationUnit);
        List<String> staticImports = packageService.getStaticImports(compilationUnit);
        fileInstance.setImportedPackages(importedPackages);
        fileInstance.setStaticImports(staticImports);
        fileInstance.setFileAnnotations(
                annotationService.getAnnotationsFromFile(compilationUnit));

        // --- Contained Types ---
        fileInstance.setClasses(classService.getClassesFromCompUnit(compilationUnit));

        // --- Metrics ---
        fileInstance.setLinesOfCode(
                compilationUnit.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0));
        int regularCount = importedPackages != null ? importedPackages.size() : 0;
        int staticCount = staticImports != null ? staticImports.size() : 0;
        fileInstance.setImportCount(regularCount + staticCount);
    }
}
