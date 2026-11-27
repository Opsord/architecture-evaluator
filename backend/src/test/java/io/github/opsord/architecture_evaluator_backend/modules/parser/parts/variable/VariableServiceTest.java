package io.github.opsord.architecture_evaluator_backend.modules.parser.parts.variable;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.VariableInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.variable.VariableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VariableServiceTest {

    private VariableService variableService;

    @BeforeEach
    void setUp() {
        variableService = new VariableService();
    }

    @Test
    void testGetVariablesReturnsInstanceVariables() {
        String source = """
            class Demo {
                private int number;
                String name;
            }
        """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("Demo").orElseThrow();

        List<VariableInstance> variables = variableService.getVariables(clazz);

        assertEquals(2, variables.size());
        assertTrue(variables.stream().allMatch(v -> v.getScope().equals("instance")));
        assertTrue(variables.stream().anyMatch(v -> v.getName().equals("number") && v.getType().equals("int")));
        assertTrue(variables.stream().anyMatch(v -> v.getName().equals("name") && v.getType().equals("String")));
    }

    @Test
    void testGetVariablesIgnoresLocalVariablesInsideMethods() {
        String source = """
            class Demo {
                void foo() {
                    int x = 1;
                    String y = "test";
                }
            }
        """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("Demo").orElseThrow();

        List<VariableInstance> variables = variableService.getVariables(clazz);

        assertTrue(variables.isEmpty());
    }

    @Test
    void testGetVariablesDistinguishesStaticAndInterfaceFields() {
        String source = """
            class Demo {
                private static final String CONST = "A";
                private int count;
            }
            interface Constants {
                String KEY = "k";
            }
        """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration demo = cu.getClassByName("Demo").orElseThrow();
        ClassOrInterfaceDeclaration iface = cu.getInterfaceByName("Constants").orElseThrow();

        List<VariableInstance> demoVars = variableService.getVariables(demo);
        assertEquals(2, demoVars.size());
        assertTrue(demoVars.stream().anyMatch(v -> v.getName().equals("CONST") && "static".equals(v.getScope())));
        assertTrue(demoVars.stream().anyMatch(v -> v.getName().equals("count") && "instance".equals(v.getScope())));

        List<VariableInstance> ifaceVars = variableService.getVariables(iface);
        assertEquals(1, ifaceVars.size());
        assertEquals("static", ifaceVars.get(0).getScope());
    }

    @Test
    void testGetVariablesExtractsRecordComponentsAsInstanceFields() {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        CompilationUnit cu = parser.parse("record UserDto(Long id, String email) {}").getResult().orElseThrow();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).orElseThrow();

        List<VariableInstance> variables = variableService.getVariables(record);

        assertEquals(2, variables.size());
        assertTrue(variables.stream().allMatch(v -> "instance".equals(v.getScope())));
        assertTrue(variables.stream().anyMatch(v -> v.getName().equals("id") && v.getType().equals("Long")));
        assertTrue(variables.stream().anyMatch(v -> v.getName().equals("email") && v.getType().equals("String")));
    }

    @Test
    void testGetVariablesReturnsEmptyListForNoVariables() {
        String source = """
            class Demo {
                void foo() {}
            }
        """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("Demo").orElseThrow();

        List<VariableInstance> variables = variableService.getVariables(clazz);

        assertTrue(variables.isEmpty());
    }
}
