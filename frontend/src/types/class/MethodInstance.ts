import type { ParameterInstance } from "./ParameterInstance";
import type { VariableInstance } from "./VariableInstance";
import type { StatementInstance } from "./StatementInstance.ts";

export interface BasicInfo {
    accessModifier: string;
    returnType: string;
    isStatic?: boolean;
    hasBody?: boolean;
}

export interface MethodMetrics {
    linesOfCode: number;
    mcCabeComplexity: number;
}

export interface StatementsInfo {
    numberOfStatements: number;
    numberOfExecutableStatements: number;
    numberOfControlStatements: number;
    numberOfSwitchCases?: number;
    statements: StatementInstance[];
}

export interface MethodInstance {
    name: string;
    basicInfo: BasicInfo;
    statementsInfo: StatementsInfo;
    inputParameters: ParameterInstance[];
    outputParameters: ParameterInstance[];
    methodMetrics: MethodMetrics;
    methodVariables: VariableInstance[];
    invokedMethods?: string[];
}
