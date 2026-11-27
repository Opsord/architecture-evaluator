package io.github.opsord.architecture_evaluator_backend.modules.parser.file_instance;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.file_instance.FileInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.ClassService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.annotation.AnnotationService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.constructor.ConstructorService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.exception_handler.ExceptionHandlerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.interface_instance.InterfaceService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.method.MethodService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable.VariableService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.FileInstanceService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.file_instance.package_part.PackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    private FileInstanceService fileInstanceService;

    @BeforeEach
    void setUp() {
        PackageService packageService = new PackageService();
        StatementService statementService = new StatementService();
        MethodService methodService = new MethodService(statementService);
        VariableService variableService = new VariableService();
        ConstructorService constructorService = new ConstructorService();
        AnnotationService annotationService = new AnnotationService();
        InterfaceService interfaceService = new InterfaceService();
        ExceptionHandlerService exceptionHandlerService = new ExceptionHandlerService();

        ClassService classService = new ClassService(
                methodService,
                statementService,
                variableService,
                constructorService,
                annotationService,
                interfaceService,
                exceptionHandlerService
        );
        fileInstanceService = new FileInstanceService(classService, packageService, annotationService);
    }

    @Test
    void testParseJavaFileReturnsFileInstance() throws Exception {
        File tempFile = File.createTempFile("TestClass", ".java");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("package test.pkg;\npublic class TestClass {}");
        }
        File projectRoot = tempFile.getParentFile();

        FileInstance fileInstance = fileInstanceService.parseJavaFile(tempFile, projectRoot);

        assertTrue(fileInstance.getFileName().endsWith(".java"));
        assertTrue(fileInstance.getFileName().startsWith("TestClass"));
        assertEquals("test.pkg", fileInstance.getPackageName());
        assertEquals(1, fileInstance.getClasses().size());
        assertEquals("TestClass", fileInstance.getClasses().get(0).getName());
        assertNotNull(fileInstance.getFilePath());
    }

    @Test
    void testGetDependentClassNamesFromClass() {
        ClassInstance classA = new ClassInstance();
        classA.setName("A");
        classA.setUsedClasses(List.of("B", "C", "D"));

        List<String> deps = fileInstanceService.getDependentClassNamesFromClass(classA, Set.of("B", "C"));

        assertTrue(deps.contains("B"));
        assertTrue(deps.contains("C"));
        assertFalse(deps.contains("D"));
    }

    @Test
    void testGetDependentClassFqnsHandlesWildcardJavaLangGuardNestedTypesAndStaticImports() {
        FileInstance file = new FileInstance();
        file.setPackageName("com.acme.service");
        file.setImportedPackages(List.of("com.acme.model.Order", "com.acme.other.*"));
        file.setStaticImports(List.of("com.acme.util.Helper.run"));

        ClassInstance cls = new ClassInstance();
        cls.setName("OrderService");
        cls.setPackageName("com.acme.service");
        cls.setQualifiedName("com.acme.service.OrderService");
        cls.setUsedClasses(List.of("Order.Status", "Error", "OrderService", "ExtraType"));

        Set<String> projectFqns = Set.of(
                "com.acme.service.OrderService",
                "com.acme.model.Order",
                "com.acme.other.Error",
                "com.acme.other.ExtraType",
                "com.acme.util.Helper");
        var nestedMap = java.util.Map.of("com.acme.model.Order.Status", "com.acme.model.Order");

        List<String> deps = fileInstanceService.getDependentClassFqns(cls, file, projectFqns, nestedMap);

        assertTrue(deps.contains("com.acme.model.Order"));
        assertTrue(deps.contains("com.acme.other.ExtraType"));
        assertTrue(deps.contains("com.acme.util.Helper"));
        assertFalse(deps.contains("com.acme.other.Error"), "java.lang.Error must not be shadowed by wildcard import");
        assertFalse(deps.contains("com.acme.service.OrderService"), "Self-dependency must be excluded");
    }
}