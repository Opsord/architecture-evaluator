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

const DependencyLinesLayer: React.FC<DependencyLinesLayerProps> = ({
    cubes,
    classPosMap,
    selectedCube,
    selectedUnit,
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

    const lines = useMemo(() => {
        if (!selectedCube) {
            return [];
        }
        const selectedCubeObj = cubeById.get(selectedCube);
        if (!selectedCubeObj) {
            return [];
        }

        const result: {
            source: string;
            target: string;
            sourceLabel: string;
            targetLabel: string;
            from: [number, number, number];
            to: [number, number, number];
            direction: "incoming" | "outgoing" | "other";
        }[] = [];

        const seen = new Set<string>();

        // Outgoing: selectedCube depends on depId -> tooltip: Class "${targetLabel}" depends on "${sourceLabel}"
        const outgoingIds = new Set(selectedUnit?.classInstance?.classDependencies ?? []);
        for (const depId of outgoingIds) {
            if (depId === selectedCube) continue;
            const depCube = cubeById.get(depId);
            const from = classPosMap[depId];
            if (!depCube || !from) continue;
            if (!shouldShowDependencyLine(depCube, selectedCubeObj)) continue;
            const key = `${depId}->${selectedCube}`;
            if (seen.has(key)) continue;
            seen.add(key);
            result.push({
                source: depId,
                target: selectedCube,
                sourceLabel: depCube.displayName,
                targetLabel: selectedCubeObj.displayName,
                from,
                to: selectedCubeObj.position,
                direction: "outgoing",
            });
        }

        // Incoming: dependentId depends on selectedCube
        const incomingIds = new Set(selectedUnit?.classInstance?.dependentClasses ?? []);
        for (const dependentId of incomingIds) {
            if (dependentId === selectedCube) continue;
            const dependentCube = cubeById.get(dependentId);
            const to = classPosMap[dependentId];
            if (!dependentCube || !to) continue;
            if (!shouldShowDependencyLine(selectedCubeObj, dependentCube)) continue;
            const key = `${selectedCube}->${dependentId}`;
            if (seen.has(key)) continue;
            seen.add(key);
            result.push({
                source: selectedCube,
                target: dependentId,
                sourceLabel: selectedCubeObj.displayName,
                targetLabel: dependentCube.displayName,
                from: selectedCubeObj.position,
                to,
                direction: "incoming",
            });
        }

        return result;
    }, [classPosMap, cubeById, selectedCube, selectedUnit]);

    if (!selectedCube) {
        return null;
    }

    return (
        <>
            {lines.map((line) => (
                <DependencyLine
                    key={line.source + "->" + line.target}
                    from={line.from}
                    to={line.to}
                    source={line.source}
                    target={line.target}
                    sourceLabel={line.sourceLabel}
                    targetLabel={line.targetLabel}
                    hoveredLine={hoveredLine}
                    isConnected={selectedCube === line.source || selectedCube === line.target}
                    setHoveredLine={setHoveredLine}
                    direction={line.direction}
                />
            ))}
        </>
    );
};

export default React.memo(DependencyLinesLayer);
