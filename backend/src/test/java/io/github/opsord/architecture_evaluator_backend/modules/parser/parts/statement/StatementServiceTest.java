package io.github.opsord.architecture_evaluator_backend.modules.parser.parts.statement;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.StatementsInfo;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementType;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.services.class_instance.parts.statement.StatementService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementServiceTest {

    private final StatementService statementService = new StatementService();

    @Test
    void countsEachLogicalAndTernaryOperatorOnce() {
        String source = """
            class Demo {
                int foo(boolean a, boolean b) {
                    return a && b || (a ? 1 : 0) > 0 ? 2 : 3;
                }
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        MethodDeclaration method = cu.getClassByName("Demo").orElseThrow()
                .getMethodsByName("foo").get(0);

        StatementsInfo info = statementService.getStatementsInfoFromMethod(method);

        assertEquals(2, info.getNumberOfLogicalOperators());
        assertEquals(2, info.getNumberOfTernaryOperators());
        assertNull(info.getStatements().get(0).getStructure());
    }

    @Test
    void storesMethodCallNameInsteadOfFullSource() {
        String source = """
            class Demo {
                void caller() {
                    callee();
                    int x = 1;
                }
                void callee() {}
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(source);
        MethodDeclaration method = cu.getClassByName("Demo").orElseThrow()
                .getMethodsByName("caller").get(0);

        StatementsInfo info = statementService.getStatementsInfoFromMethod(method);
        List<String> expressionStructures = info.getStatements().stream()
                .filter(s -> s.getType() == StatementType.EXPRESSION)
                .map(StatementInstance::getStructure)
                .toList();
        assertTrue(expressionStructures.contains("callee"));
        assertTrue(expressionStructures.contains(null));
    }

    @Test
    void countsForEachDoWhileSwitchExprWhenGuardAndIgnoresAnonymousClasses() {
        var parser = new com.github.javaparser.JavaParser(
                new com.github.javaparser.ParserConfiguration()
                        .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_21));
        String source = """
            class Demo {
                int process(java.util.List<Object> items) {
                    int count = 0;
                    for (Object item : items) {
                        count += switch (item) {
                            case String s when s.length() > 1 && s.length() < 10 -> 1;
                            case Integer i -> 2;
                            default -> 0;
                        };
                    }
                    do {
                        count--;
                    } while (count > 0);

                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            if (true && false) {
                                int z = true ? 1 : 0;
                            }
                        }
                    };
                    return count;
                }
            }
            """;
        CompilationUnit cu = parser.parse(source).getResult().orElseThrow();
        MethodDeclaration method = cu.getClassByName("Demo").orElseThrow()
                .getMethodsByName("process").get(0);

        StatementsInfo info = statementService.getStatementsInfoFromMethod(method);
        // 1 for-each + 1 do-while = 2 control statements (anonymous class "if" ignored!)
        assertEquals(2, info.getNumberOfControlStatements());
        // 2 case labels + 1 when guard = 3 switch cases (default = 0)
        assertEquals(3, info.getNumberOfSwitchCases());
        // 1 "&&" in when guard (anonymous class "&&" ignored!)
        assertEquals(1, info.getNumberOfLogicalOperators());
        // anonymous class ternary ignored!
        assertEquals(0, info.getNumberOfTernaryOperators());
    }
}
