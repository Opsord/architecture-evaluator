// PackageService.java
package io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.package_part;

import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PackageService {

    public List<String> getImportedPackages(CompilationUnit compilationUnit) {
        List<String> importedPackages = new ArrayList<>();
        PackageVisitor visitor = new PackageVisitor(false);
        compilationUnit.accept(visitor, importedPackages);
        return importedPackages;
    }

    public List<String> getStaticImports(CompilationUnit compilationUnit) {
        List<String> staticImports = new ArrayList<>();
        PackageVisitor visitor = new PackageVisitor(true);
        compilationUnit.accept(visitor, staticImports);
        return staticImports;
    }
}
