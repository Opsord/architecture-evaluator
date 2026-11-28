package io.github.opsord.architecture_evaluator_backend.modules.processor.parts;

import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.ClassInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.JavaFileType;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.ParameterInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.MethodInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.BasicInfo;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.MethodMetrics;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementInstance;
import io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.StatementsInfo;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.parts.ComplexityMetricsDTO;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.dto.parts.ProgramMetricsDTO;
import io.github.opsord.architecture_evaluator_backend.modules.api.processor.services.parts.ComplexityMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComplexityMetricsServiceTest {

    private ComplexityMetricsService service;

    @BeforeEach
    void setUp() {
        service = new ComplexityMetricsService();
    }

    private List<StatementInstance> executableStatements(int count) {
        StatementInstance expr = new StatementInstance();
        expr.setType(io.github.opsord.architecture_evaluator_backend.modules.api.parser.instances.class_instance.parts.method.parts.statement.StatementType.EXPRESSION);
        return Collections.nCopies(count, expr);
    }

    // Helper to create a MethodInstance with the given properties
    private MethodInstance method(int numStatements, int numControlStatements, int numInputs, int numOutputs) {
        MethodInstance methodInstance = new MethodInstance();
        StatementsInfo statementsInfo = new StatementsInfo();
        statementsInfo.setStatements(executableStatements(numStatements));
        statementsInfo.setNumberOfControlStatements(numControlStatements);
        methodInstance.setStatementsInfo(statementsInfo);
        methodInstance.setInputParameters(numInputs > 0 ? Collections.nCopies(numInputs, new ParameterInstance()) : null);
        methodInstance.setOutputParameters(numOutputs > 0 ? Collections.nCopies(numOutputs, new ParameterInstance()) : null);
        return methodInstance;
    }

    @Test
    void testEmptyClass() {
        ClassInstance classInstance = new ClassInstance();
        classInstance.setMethods(Collections.emptyList());
        classInstance.setStatements(Collections.emptyList());
        classInstance.setLinesOfCode(0);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(classInstance);
        assertEquals(0, dto.getApproxMcCabeCC());
        assertEquals(0.0, dto.getImprovedCC());
    }

    @Test
    void testSingleMethodNoStatements() {
        MethodInstance m = method(0, 0, 0, 0);
        ClassInstance classInstance = new ClassInstance();
        classInstance.setMethods(List.of(m));
        classInstance.setStatements(Collections.emptyList());
        classInstance.setLinesOfCode(10);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(classInstance);
        assertEquals(1, dto.getApproxMcCabeCC());
        assertEquals(0.1, dto.getImprovedCC());
    }

    @Test
    void testMultipleMethodsWithStatements() {
        MethodInstance m1 = method(3, 1, 2, 1); // 3 stmts, 1 control, 2 in, 1 out
        MethodInstance m2 = method(2, 0, 1, 0); // 2 stmts, 0 control, 1 in, 0 out
        ClassInstance classInstance = new ClassInstance();
        classInstance.setMethods(List.of(m1, m2));
        classInstance.setStatements(executableStatements(5));
        classInstance.setLinesOfCode(20);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(classInstance);
        assertEquals(3, dto.getApproxMcCabeCC());
        assertEquals(0.5, dto.getImprovedCC());
    }

    @Test
    void testMethodWithOnlyControlStatements() {
        MethodInstance m = method(0, 3, 0, 0); // 0 stmts, 3 control stmts
        ClassInstance classInstance = new ClassInstance();
        classInstance.setMethods(List.of(m));
        classInstance.setStatements(executableStatements(3));
        classInstance.setLinesOfCode(15);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(classInstance);
        assertEquals(4, dto.getApproxMcCabeCC());
        // improvedCC = (1+3+0+0)/15 = 0.266...
        assertEquals((1+3+0+0)/15.0, dto.getImprovedCC());
    }

    @Test
    void testMethodWithInputsAndOutputs() {
        MethodInstance m = method(1, 0, 4, 2); // 1 stmt, 0 control, 4 in, 2 out
        ClassInstance classInstance = new ClassInstance();
        classInstance.setMethods(List.of(m));
        classInstance.setStatements(executableStatements(1));
        classInstance.setLinesOfCode(7);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(classInstance);
        assertEquals(1, dto.getApproxMcCabeCC());
        // improvedCC = (1+1+4+2)/7 = 8/7
        assertEquals(8.0/7.0, dto.getImprovedCC());
    }

    @Test
    void testThrowDoesNotSubtractAndCatchSwitchLogicalTernaryAddComplexity() {
        MethodInstance m = new MethodInstance();
        StatementsInfo info = new StatementsInfo();
        info.setNumberOfControlStatements(2);  // if + for-each
        info.setNumberOfSwitchCases(3);        // 3 switch cases
        info.setNumberOfCatchClauses(2);       // 2 catch blocks
        info.setNumberOfThrowStatements(4);    // throws must NOT subtract
        info.setNumberOfLogicalOperators(2);   // && and ||
        info.setNumberOfTernaryOperators(1);   // ?:
        m.setStatementsInfo(info);

        ClassInstance classInstance = new ClassInstance();
        classInstance.setMethods(List.of(m));
        classInstance.setStatements(executableStatements(5));
        classInstance.setLinesOfCode(25);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(classInstance);
        // CC = 1 + 2 + 3 + 2 + 2 + 1 = 11
        assertEquals(11, dto.getApproxMcCabeCC());
        assertEquals(11, dto.getMaxMethodMcCabeCC());
    }

    @Test
    void testMethodWithOnlyThrowNeverProducesZeroOrNegativeCC() {
        MethodInstance m = new MethodInstance();
        StatementsInfo info = new StatementsInfo();
        info.setNumberOfThrowStatements(2);
        m.setStatementsInfo(info);

        ClassInstance classInstance = new ClassInstance();
        classInstance.setMethods(List.of(m));
        classInstance.setStatements(executableStatements(2));
        classInstance.setLinesOfCode(5);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(classInstance);
        assertEquals(1, dto.getApproxMcCabeCC());
    }

    @Test
    void testPureInterfaceHasZeroComplexityAndZeroImprovedCC() {
        MethodInstance sig1 = method(0, 0, 2, 1);
        BasicInfo bi1 = new BasicInfo();
        bi1.setHasBody(false);
        sig1.setBasicInfo(bi1);
        sig1.setMethodMetrics(new MethodMetrics());

        MethodInstance sig2 = method(0, 0, 1, 1);
        BasicInfo bi2 = new BasicInfo();
        bi2.setHasBody(false);
        sig2.setBasicInfo(bi2);
        sig2.setMethodMetrics(new MethodMetrics());

        ClassInstance iface = new ClassInstance();
        iface.setJavaFileType(JavaFileType.INTERFACE);
        iface.setMethods(List.of(sig1, sig2));
        iface.setStatements(Collections.emptyList());
        iface.setLinesOfCode(10);

        ProgramMetricsDTO pm = new ProgramMetricsDTO();
        pm.setNumberOfMethods(2);
        pm.setSumOfExecutableStatements(0);
        pm.setMaxInputParameters(2);
        pm.setMaxOutputParameters(1);
        pm.setLinesOfCode(10);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(iface, pm);
        assertEquals(0, dto.getApproxMcCabeCC());
        assertEquals(0, dto.getMaxMethodMcCabeCC());
        assertEquals(0.0, dto.getImprovedCC());
        assertEquals(0, sig1.getMethodMetrics().getMcCabeComplexity());
        assertEquals(0, sig2.getMethodMetrics().getMcCabeComplexity());
    }

    @Test
    void testMixedInterfaceCountsOnlyDefaultMethodWithBody() {
        MethodInstance sig = method(0, 0, 5, 1);
        BasicInfo sigInfo = new BasicInfo();
        sigInfo.setHasBody(false);
        sig.setBasicInfo(sigInfo);
        sig.setMethodMetrics(new MethodMetrics());

        MethodInstance defaultMethod = method(2, 1, 1, 1);
        BasicInfo defaultInfo = new BasicInfo();
        defaultInfo.setHasBody(true);
        defaultMethod.setBasicInfo(defaultInfo);
        defaultMethod.setMethodMetrics(new MethodMetrics());

        ClassInstance iface = new ClassInstance();
        iface.setJavaFileType(JavaFileType.INTERFACE);
        iface.setMethods(List.of(sig, defaultMethod));
        iface.setStatements(executableStatements(2));
        iface.setLinesOfCode(10);

        ProgramMetricsDTO pm = new ProgramMetricsDTO();
        pm.setNumberOfMethods(2);
        pm.setSumOfExecutableStatements(2);
        pm.setMaxInputParameters(5);
        pm.setMaxOutputParameters(1);
        pm.setLinesOfCode(10);

        ComplexityMetricsDTO dto = service.calculateComplexityMetrics(iface, pm);
        assertEquals(2, dto.getApproxMcCabeCC());
        assertEquals(2, dto.getMaxMethodMcCabeCC());
        // improvedCC uses only defaultMethod: (1 method + 2 stmts + 1 in + 1 out) / 10 = 0.5
        assertEquals(0.5, dto.getImprovedCC());
        assertEquals(0, sig.getMethodMetrics().getMcCabeComplexity());
        assertEquals(2, defaultMethod.getMethodMetrics().getMcCabeComplexity());
    }
}