import React, { useEffect, useMemo, useState } from "react";
import DependencyLine from "../elements/DependencyLine.tsx";
import type { CompUnitVisual } from "../CompUnitsScene.tsx";
import { LayerAnnotation } from "../../../../../types/class/LayerAnnotation.ts";
import type { ProcessedClassInstance } from "../../../../../types/ProcessedClassInstance.ts";

interface DependencyLinesLayerProps {
    cubes: CompUnitVisual[];
    classPosMap: Record<string, [number, number, number]>;
    selectedCube: string | null;
    selectedUnit?: ProcessedClassInstance | null;
}

function shouldShowDependencyLine(
    sourceCube: CompUnitVisual,
    targetCube: CompUnitVisual | undefined,
): boolean {
    if (!targetCube) return false;
    const sourceLayer = sourceCube.data.classInstance.layerAnnotation;
    const targetLayer = targetCube.data.classInstance.layerAnnotation;
    return (
        sourceLayer !== LayerAnnotation.TESTING &&
        targetLayer !== LayerAnnotation.TESTING
    );
}

interface DependencyEdge {
    key: string;
    source: string;
    target: string;
    sourceLabel: string;
    targetLabel: string;
    from: [number, number, number];
    to: [number, number, number];
}

interface RenderDependencyEdge extends DependencyEdge {
    direction: "incoming" | "outgoing" | "other";
}

const DependencyLinesLayer: React.FC<DependencyLinesLayerProps> = ({
    cubes,
    classPosMap,
    selectedCube,
}) => {
    const [hoveredLine, setHoveredLine] = useState<string | null>(null);

    useEffect(() => {
        setHoveredLine(null);
    }, [selectedCube]);

    const cubeById = useMemo(() => {
        const map = new Map<string, CompUnitVisual>();
        for (const cube of cubes) {
            map.set(cube.id, cube);
        }
        return map;
    }, [cubes]);

    const allEdges = useMemo(() => {
        const result: DependencyEdge[] = [];
        const seen = new Set<string>();

        const addEdge = (sourceCube: CompUnitVisual, targetCube: CompUnitVisual) => {
            if (sourceCube.id === targetCube.id) return;
            if (!shouldShowDependencyLine(sourceCube, targetCube)) return;
            const from = classPosMap[sourceCube.id];
            const to = classPosMap[targetCube.id];
            if (!from || !to) return;
            const key = `${sourceCube.id}->${targetCube.id}`;
            if (seen.has(key)) return;
            seen.add(key);
            result.push({
                key,
                source: sourceCube.id,
                target: targetCube.id,
                sourceLabel: sourceCube.displayName,
                targetLabel: targetCube.displayName,
                from,
                to,
            });
        };

        for (const cube of cubes) {
            const deps = cube.data.classInstance.classDependencies ?? [];
            for (const depId of deps) {
                const depCube = cubeById.get(depId);
                if (depCube) {
                    addEdge(depCube, cube);
                }
            }

            const dependents = cube.data.classInstance.dependentClasses ?? [];
            for (const dependentId of dependents) {
                const dependentCube = cubeById.get(dependentId);
                if (dependentCube) {
                    addEdge(cube, dependentCube);
                }
            }
        }

        return result;
    }, [cubes, classPosMap, cubeById]);

    const lines = useMemo<RenderDependencyEdge[]>(() => {
        if (!selectedCube) {
            return allEdges.map((edge) => ({
                ...edge,
                direction: "other",
            }));
        }

        const result: RenderDependencyEdge[] = [];
        for (const edge of allEdges) {
            if (edge.target === selectedCube) {
                result.push({ ...edge, direction: "outgoing" });
            } else if (edge.source === selectedCube) {
                result.push({ ...edge, direction: "incoming" });
            }
        }
        return result;
    }, [allEdges, selectedCube]);

    if (lines.length === 0) {
        return null;
    }

    return (
        <>
            {lines.map((line) => (
                <DependencyLine
                    key={line.key}
                    from={line.from}
                    to={line.to}
                    source={line.source}
                    target={line.target}
                    sourceLabel={line.sourceLabel}
                    targetLabel={line.targetLabel}
                    isHovered={hoveredLine === line.key}
                    isConnected={selectedCube !== null && (selectedCube === line.source || selectedCube === line.target)}
                    setHoveredLine={setHoveredLine}
                    direction={line.direction}
                />
            ))}
        </>
    );
};

export default React.memo(DependencyLinesLayer);
