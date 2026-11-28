package io.github.opsord.architecture_evaluator_backend.modules.parser.class_instance;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.JavaFileType;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.LayerAnnotation;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.ClassService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.annotation.AnnotationService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.constructor.ConstructorService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.exception_handler.ExceptionHandlerService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.interface_instance.InterfaceService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.method.MethodService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable.VariableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassVisitorTest {

    private ClassService classService;

    @BeforeEach
    void setUp() {
        StatementService statementService = new StatementService();
        MethodService methodService = new MethodService(statementService);
        VariableService variableService = new VariableService();
        ConstructorService constructorService = new ConstructorService();
        AnnotationService annotationService = new AnnotationService();
        InterfaceService interfaceService = new InterfaceService();
        ExceptionHandlerService exceptionHandlerService = new ExceptionHandlerService();

        classService = new ClassService(
                methodService,
                statementService,
                variableService,
                constructorService,
                annotationService,
                interfaceService,
                exceptionHandlerService
        );
    }

    @Test
    void testComplexClassParsing() {
        String source = """
        package com.example;

        import org.springframework.stereotype.Service;
        import java.util.List;
        import java.io.IOException;

        @Service
        public class MyService extends BaseClass implements Runnable {
        
            private String name;

            public MyService() {}

            public List<User> getUsers() {
                try {
                    return List.of(new User());
                } catch (Exception e) {
                    e.printStackTrace();
                    return List.of();
                }
            }

            @Override
            public void run() {}

            class InnerHelper {}
        }
        """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        List<ClassInstance> result = classService.getClassesFromCompUnit(cu);

        assertEquals(1, result.size());
        ClassInstance instance = result.get(0);

        // --- Identity ---
        assertEquals("MyService", instance.getName());
        assertEquals("com.example", instance.getPackageName());
        assertEquals("com.example.MyService", instance.getQualifiedName());
        assertEquals(JavaFileType.CLASS, instance.getJavaFileType());
        assertEquals(LayerAnnotation.SERVICE, instance.getLayerAnnotation());

        // --- Inheritance & Interfaces ---
        assertTrue(instance.getSuperClasses().contains("BaseClass"));
        assertTrue(instance.getImplementedInterfaces().contains("Runnable"));

        // --- Annotations ---
        assertTrue(instance.getAnnotations().contains("Service"));
        assertFalse(instance.getAnnotations().contains("Override"));

        // --- Variables, methods, constructor ---
        assertEquals(1, instance.getClassVariables().size());
        assertEquals(1, instance.getConstructors().size());
        assertEquals(2, instance.getMethods().size()); // run() y getUsers()

        // --- Used classes ---
        List<String> used = instance.getUsedClasses();
        assertTrue(used.contains("List"));
        assertTrue(used.contains("User"));
        assertTrue(used.contains("Exception"));

        // --- Inner classes ---
        assertEquals(1, instance.getInnerClasses().size());
        assertEquals("InnerHelper", instance.getInnerClasses().get(0).getName());

        // --- Lines of code ---
        assertTrue(instance.getLinesOfCode() > 0);
    }

    @Test
    void testLayerAnnotationsDetection() {
        String source = """
            @Entity
            class EntityClass {}

            @Repository
            class RepoClass {}

            @Controller
            class ControllerClass {}

            @RestController
            class RestControllerClass {}

            @Document
            class DocumentClass {}

            @Custom
            class CustomClass {}
        """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        List<ClassInstance> result = classService.getClassesFromCompUnit(cu);

        assertEquals(6, result.size());

        assertEquals(LayerAnnotation.ENTITY, result.get(0).getLayerAnnotation());
        assertEquals(LayerAnnotation.REPOSITORY, result.get(1).getLayerAnnotation());
        assertEquals(LayerAnnotation.CONTROLLER, result.get(2).getLayerAnnotation());
        assertEquals(LayerAnnotation.CONTROLLER, result.get(3).getLayerAnnotation());
        assertEquals(LayerAnnotation.DOCUMENT, result.get(4).getLayerAnnotation());
    }

    @Test
    void testJava21TypesRecordEnumAnnotationAndAbstractClass() {
        var parser = new com.github.javaparser.JavaParser(
                new com.github.javaparser.ParserConfiguration()
                        .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_21));
        String source = """
            package com.acme.types;

            public record UserDto(Long id, String name) {
                public UserDto {
                    if (id == null) throw new IllegalArgumentException();
                }
            }
            enum Status { OPEN, CLOSED }
            @interface CustomTag {}
            abstract class BaseHandler {}
        """;

        CompilationUnit cu = parser.parse(source).getResult().orElseThrow();
        List<ClassInstance> result = classService.getClassesFromCompUnit(cu);

        assertEquals(4, result.size());
        assertEquals(JavaFileType.RECORD, result.get(0).getJavaFileType());
        assertEquals("com.acme.types.UserDto", result.get(0).getQualifiedName());
        assertEquals(2, result.get(0).getClassVariables().size());
        assertEquals(1, result.get(0).getConstructors().size());
        assertEquals(2, result.get(0).getConstructors().get(0).getParameters().size());

        assertEquals(JavaFileType.ENUM, result.get(1).getJavaFileType());
        assertEquals(JavaFileType.ANNOTATION, result.get(2).getJavaFileType());
        assertEquals(JavaFileType.ABSTRACT_CLASS, result.get(3).getJavaFileType());
    }

    @Test
    void testExceptionTypeAndSpringDataRepositoryHeuristic() {
        String source = """
            package com.acme.domain;

            public class OrderNotFoundException extends RuntimeException {}
            public abstract class BaseDomainException extends Exception {}
            public class FatalCondition extends Error {}
            public class CustomThrowable extends Throwable {}
            public class ValidationError extends BaseError {}
            public interface ErrorContract extends Error {}
            public interface OrderRepository extends JpaRepository<Order, Long> {}
            public interface AuditRepository extends ListCrudRepository<Audit, Long> {}
            public interface DomainPort extends Repository {}
        """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        List<ClassInstance> result = classService.getClassesFromCompUnit(cu);

        assertEquals(9, result.size());
        assertEquals(JavaFileType.EXCEPTION, result.get(0).getJavaFileType());
        assertEquals(JavaFileType.EXCEPTION, result.get(1).getJavaFileType());
        assertEquals(JavaFileType.EXCEPTION, result.get(2).getJavaFileType());
        assertEquals(JavaFileType.EXCEPTION, result.get(3).getJavaFileType());
        assertEquals(JavaFileType.CLASS, result.get(4).getJavaFileType()); // ValidationError extends BaseError -> not EXCEPTION
        assertEquals(JavaFileType.INTERFACE, result.get(5).getJavaFileType());

        assertEquals(JavaFileType.INTERFACE, result.get(6).getJavaFileType());
        assertEquals(LayerAnnotation.REPOSITORY, result.get(6).getLayerAnnotation());

        assertEquals(JavaFileType.INTERFACE, result.get(7).getJavaFileType());
        assertEquals(LayerAnnotation.REPOSITORY, result.get(7).getLayerAnnotation());

        assertEquals(JavaFileType.INTERFACE, result.get(8).getJavaFileType());
        assertEquals(LayerAnnotation.OTHER, result.get(8).getLayerAnnotation());
    }

}