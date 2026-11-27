export const StatementType = {
    IF: 'IF',
    FOR: 'FOR',
    FOR_EACH: 'FOR_EACH',
    WHILE: 'WHILE',
    DO_WHILE: 'DO_WHILE',
    SWITCH: 'SWITCH',
    EXPRESSION: 'EXPRESSION',
    RETURN: 'RETURN',
    THROW: 'THROW',
    TRY: 'TRY',
    CATCH: 'CATCH'
} as const;

export type StatementType = (typeof StatementType)[keyof typeof StatementType];

export interface StatementInstance {
    type: StatementType;
    structure?: string;
}
