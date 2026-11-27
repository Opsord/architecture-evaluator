package io.github.opsord.architecture_evaluator_backend.modules.parser.parts.method;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.MethodInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.method.MethodVisitor;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MethodVisitorTest {

    @Test
    void testSimpleMethodExtraction() {
        String source = """
            class TestClass {
                public int sum(int a, int b) {
                    return a + b;
                }
            }
        """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("TestClass").orElseThrow();

        List<MethodInstance> methods = new ArrayList<>();
        MethodVisitor visitor = new MethodVisitor(new StatementService());
        clazz.accept(visitor, methods);

        assertEquals(1, methods.size());
        MethodInstance method = methods.get(0);
        assertEquals("sum", method.getName());
        assertEquals("int", method.getBasicInfo().getReturnType());
        assertEquals(2, method.getInputParameters().size());
        assertEquals("a", method.getInputParameters().get(0).getName());
        assertEquals("b", method.getInputParameters().get(1).getName());
        assertEquals("int", method.getInputParameters().get(0).getType());
        assertEquals("int", method.getInputParameters().get(1).getType());
        assertEquals(1, method.getOutputParameters().size());
        assertEquals("int", method.getOutputParameters().get(0).getType());
        assertTrue(method.getMethodMetrics().getLinesOfCode() > 0);
    }

    @Test
    void testVoidMethodExtraction() {
        String source = """
            class TestClass {
                public void doNothing() {}
            }
        """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("TestClass").orElseThrow();

        List<MethodInstance> methods = new ArrayList<>();
        MethodVisitor visitor = new MethodVisitor(new StatementService());
        clazz.accept(visitor, methods);

        assertEquals(1, methods.size());
        MethodInstance method = methods.get(0);
        assertEquals("doNothing", method.getName());
        assertEquals("void", method.getBasicInfo().getReturnType());
        assertEquals(0, method.getInputParameters().size());
        assertEquals(0, method.getOutputParameters().size());
    }

    @Test
    void testMethodWithAnnotations() {
        String source = """
            class TestClass {
                @Deprecated
                public String greet(String name) {
                    return "Hello " + name;
                }
            }
        """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("TestClass").orElseThrow();

        List<MethodInstance> methods = new ArrayList<>();
        MethodVisitor visitor = new MethodVisitor(new StatementService());
        clazz.accept(visitor, methods);

        assertEquals(1, methods.size());
        MethodInstance method = methods.get(0);
        assertEquals("greet", method.getName());
        assertEquals("String", method.getBasicInfo().getReturnType());
        assertEquals(1, method.getInputParameters().size());
        assertEquals("name", method.getInputParameters().get(0).getName());
        assertEquals("String", method.getInputParameters().get(0).getType());
    }

    @Test
    void testLexicalBodyAnalyzerHandlesShadowingSuperSwitchFallThroughAndJacksonIsStatic() throws Exception {
        var parser = new com.github.javaparser.JavaParser(
                new com.github.javaparser.ParserConfiguration()
                        .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_21));
        String source = """
            class Sample {
                private String name;
                private java.util.List<String> items;
                private int counter;

                public void exercise(Object obj, int code) {
                    int x = super.counter;
                    for (String items : items) {
                        System.out.println(items);
                    }
                    if (obj instanceof String name) {
                        System.out.println(name);
                    } else {
                        System.out.println(this.name);
                    }
                    try (AutoCloseable counter = null) {
                        System.out.println(counter);
                    } catch (Exception e) {
                        System.out.println(this.counter);
                    }
                    switch (code) {
                        case 1:
                            int local = 1;
                            break;
                        case 2:
                            local = 2;
                            break;
                    }
                }

                public static void helper() {}
            }
            """;
        CompilationUnit cu = parser.parse(source).getResult().orElseThrow();
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("Sample").orElseThrow();

        List<MethodInstance> methods = new ArrayList<>();
        MethodVisitor visitor = new MethodVisitor(
                new StatementService(),
                java.util.Map.of("name", "String", "items", "List<String>", "counter", "int"),
                "Sample");
        clazz.accept(visitor, methods);

        assertEquals(2, methods.size());
        MethodInstance exercise = methods.get(0);
        List<String> accessedInstanceFields = exercise.getMethodVariables().stream()
                .filter(v -> "instance".equals(v.getScope()))
                .map(io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.VariableInstance::getName)
                .toList();
        assertTrue(accessedInstanceFields.contains("items"));
        assertTrue(accessedInstanceFields.contains("name"));
        assertTrue(accessedInstanceFields.contains("counter"));

        MethodInstance staticHelper = methods.get(1);
        assertTrue(staticHelper.getBasicInfo().isStatic());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(staticHelper.getBasicInfo());
        assertTrue(json.contains("\"isStatic\":true"), "Expected Jackson to serialize isStatic, got: " + json);
    }

    @Test
    void testSuperFieldAccessAloneDoesNotCountAsThisFieldAccess() {
        String source = """
            class Child extends Parent {
                private int count;
                void readSuper() {
                    int y = super.count;
                }
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("Child").orElseThrow();

        List<MethodInstance> methods = new ArrayList<>();
        MethodVisitor visitor = new MethodVisitor(
                new StatementService(),
                java.util.Map.of("count", "int"),
                "Child");
        clazz.accept(visitor, methods);

        long instanceAccesses = methods.get(0).getMethodVariables().stream()
                .filter(v -> "instance".equals(v.getScope()))
                .count();
        assertEquals(0, instanceAccesses);
    }
}