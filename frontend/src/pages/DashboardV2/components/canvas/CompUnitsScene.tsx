import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Canvas } from "@react-three/fiber";
import LayerBox from "./elements/LayerBox.tsx";
import CompUnitRow from "./agrupators/CompUnitRow.tsx";
import type { FlaggedCube } from "./agrupators/CompUnitRow.tsx";
import DependencyLinesLayer from "./agrupators/DependencyLinesLayer.tsx";
import CameraControls from "./controls/CameraControls.tsx";
import LayerLabelsOverlay from "./LayerLabelsOverlay.tsx";
import type { ProjectAnalysisDTO } from "../../../../types/ProjectAnalysisInstance.ts";
import type { ProcessedClassInstance } from "../../../../types/ProcessedClassInstance.ts";

const GAP = 2;
const MIN_SIZE = 1;
const MAX_SIZE = 3;
const minLOC = 1;
const maxLOC = 500;
const minBoxWidth = 10;
const boxDepth = 2;
const boxMargin = 1;
const boxVerticalMargin = 1;
const verticalGap = 2;

const categories = [
    { key: "controllers", label: "Controllers" },
    { key: "services", label: "Services" },
    { key: "repositories", label: "Repositories" },
    { key: "entities_documents", label: "Entities & Documents" },
    { key: "testClasses", label: "Test Classes" },
    { key: "otherClasses", label: "Other Classes" },
] as const;

type CategoryKey = (typeof categories)[number]["key"];

function getCubeSize(unit: ProcessedClassInstance): [number, number, number] {
    const loc = unit.classInstance?.linesOfCode ?? 1;
    const size = MIN_SIZE + ((Math.min(loc, maxLOC) - minLOC) / (maxLOC - minLOC)) * (MAX_SIZE - MIN_SIZE);
    return [size, size, size];
}

function getRawDisplayName(unit: ProcessedClassInstance, categoryKey: CategoryKey, index: number): string {
    const className = unit.classInstance?.name?.trim() ?? "";
    if (
        !className &&
        categoryKey === "repositories" &&
        (unit.classInstance?.implementedInterfaces?.length ?? 0) > 0
    ) {
        return unit.classInstance.implementedInterfaces[0];
    }
    if (className) {
        return className;
    }
    return `Unnamed-${categoryKey}-${index}`;
}

function getShortPackageSuffix(packageName?: string): string {
    const trimmed = packageName?.trim();
    if (!trimmed || trimmed === "default") {
        return "";
    }
    const lastDot = trimmed.lastIndexOf(".");
    return lastDot >= 0 ? trimmed.substring(lastDot + 1) : trimmed;
}

function unitsForCategory(projectData: ProjectAnalysisDTO, key: CategoryKey): ProcessedClassInstance[] {
    if (key === "entities_documents") {
        return [...(projectData.entities || []), ...(projectData.documents || [])];
    }
    return projectData[key] || [];
}

interface CompUnitsSceneProps {
    projectData: ProjectAnalysisDTO;
    selectedCube: string | null;
    setSelectedCube: (id: string | null, unit: ProcessedClassInstance | null) => void;
    vibrationEnabled: boolean;
}

export interface CompUnitVisual {
    id: string;
    data: ProcessedClassInstance;
    displayName: string;
    position: [number, number, number];
    size: [number, number, number];
    rowIdx: number;
}

const CompUnitsScene: React.FC<CompUnitsSceneProps> = ({
    projectData,
    selectedCube,
    setSelectedCube,
    vibrationEnabled,
}) => {
    const { cubes, classPosMap, boxes, rows } = useMemo(() => {
        const cubes: CompUnitVisual[] = [];
        const classPosMap: Record<string, [number, number, number]> = {};
        const boxes: { rowIdx: number; boxPos: [number, number, number]; boxSize: [number, number, number]; label: string }[] = [];
        const rows: CompUnitVisual[][] = [];
        const rawNameCounts = new Map<string, number>();

        let y = 0;
        let visibleRow = 0;
        categories.forEach((cat) => {
            const units = unitsForCategory(projectData, cat.key);
            if (units.length === 0) return;

            const sortedUnits = [...units].sort(
                (a, b) => (b.classInstance?.linesOfCode ?? 0) - (a.classInstance?.linesOfCode ?? 0),
            );
            const centeredUnits: ProcessedClassInstance[] = new Array(sortedUnits.length);
            const midIdx = Math.floor((sortedUnits.length - 1) / 2);
            let leftIdx = midIdx - 1;
            let rightIdx = midIdx + 1;
            centeredUnits[midIdx] = sortedUnits[0];
            for (let i = 1; i < sortedUnits.length; i++) {
                if (i % 2 === 1) centeredUnits[rightIdx++] = sortedUnits[i];
                else centeredUnits[leftIdx--] = sortedUnits[i];
            }

            const sizes = centeredUnits.map(getCubeSize);
            const totalCubesWidth = sizes.reduce((sum, s) => sum + s[0], 0);
            const totalGapsWidth = (centeredUnits.length - 1) * GAP;
            const boxWidth = Math.max(totalCubesWidth + totalGapsWidth, minBoxWidth);
            const boxHeight = Math.max(...sizes.map((s) => s[1]), MIN_SIZE) + boxVerticalMargin;

            const z = 0;
            const boxPos: [number, number, number] = [0, -y, 0];

            boxes.push({
                rowIdx: visibleRow,
                boxPos,
                boxSize: [boxWidth + boxMargin, boxHeight, boxDepth + boxMargin],
                label: cat.label,
            });

            let x = -((totalCubesWidth + totalGapsWidth) / 2);
            const rowCubes: CompUnitVisual[] = [];
            centeredUnits.forEach((unit, idx) => {
                const size = sizes[idx];
                const rawDisplayName = getRawDisplayName(unit, cat.key, idx);
                const id = unit.classInstance?.qualifiedName?.trim() || rawDisplayName;
                rawNameCounts.set(rawDisplayName, (rawNameCounts.get(rawDisplayName) ?? 0) + 1);
                const cubeInfo: CompUnitVisual = {
                    id,
                    data: unit,
                    displayName: rawDisplayName,
                    position: [x + size[0] / 2, -y, z],
                    size,
                    rowIdx: visibleRow,
                };
                cubes.push(cubeInfo);
                rowCubes.push(cubeInfo);
                classPosMap[id] = cubeInfo.position;
                x += size[0] + GAP;
            });
            rows.push(rowCubes);

            y += boxHeight + verticalGap;
            visibleRow++;
        });

        // Second pass across all rows: disambiguate displayName for homonymous classes
        for (const cube of cubes) {
            if ((rawNameCounts.get(cube.displayName) ?? 0) > 1) {
                const suffix = getShortPackageSuffix(cube.data.classInstance?.packageName);
                if (suffix) {
                    cube.displayName = `${cube.displayName} (${suffix})`;
                }
            }
        }

        return { cubes, classPosMap, boxes, rows };
    }, [projectData]);

    const cubesByRow = useMemo(() => {
        const selected = cubes.find((c) => c.id === selectedCube);
        const dependencySet = new Set(selected?.data.classInstance.classDependencies ?? []);
        const dependentSet = new Set(selected?.data.classInstance.dependentClasses ?? []);
        if (selectedCube && dependentSet.size === 0) {
            for (const cube of cubes) {
                if ((cube.data.classInstance.classDependencies ?? []).includes(selectedCube)) {
                    dependentSet.add(cube.id);
                }
            }
        }

        const flagged: FlaggedCube[] = cubes.map((cube) => {
            const isSelected = selectedCube === cube.id;
            const isDependency = selectedCube ? dependencySet.has(cube.id) : false;
            const isDependent = selectedCube ? dependentSet.has(cube.id) : false;
            return {
                ...cube,
                isSelected,
                isDependency,
                isDependent,
                dimmed: !!selectedCube && !isSelected && !isDependency && !isDependent,
            };
        });

        return rows.map((_, idx) => flagged.filter((cube) => cube.rowIdx === idx));
    }, [cubes, rows, selectedCube]);

    const selectedUnit = selectedCube
        ? cubes.find((c) => c.id === selectedCube)?.data ?? null
        : null;

    const selectedCubeRef = useRef<string | null>(selectedCube);
    selectedCubeRef.current = selectedCube;

    const [canvasEl, setCanvasEl] = useState<HTMLCanvasElement | null>(null);
    const [contextLost, setContextLost] = useState(false);
    const [isPageVisible, setIsPageVisible] = useState(
        typeof document === "undefined" ? true : document.visibilityState === "visible",
    );

    useEffect(() => {
        const handleVisibilityChange = () => {
            setIsPageVisible(document.visibilityState === "visible");
        };
        document.addEventListener("visibilitychange", handleVisibilityChange);
        return () => {
            document.removeEventListener("visibilitychange", handleVisibilityChange);
        };
    }, []);

    useEffect(() => {
        if (!canvasEl) {
            return;
        }
        const handleContextLost = (event: Event) => {
            event.preventDefault();
            setContextLost(true);
        };
        const handleContextRestored = () => {
            setContextLost(false);
        };
        canvasEl.addEventListener("webglcontextlost", handleContextLost);
        canvasEl.addEventListener("webglcontextrestored", handleContextRestored);
        return () => {
            canvasEl.removeEventListener("webglcontextlost", handleContextLost);
            canvasEl.removeEventListener("webglcontextrestored", handleContextRestored);
        };
    }, [canvasEl]);

    const onSelectCube = useCallback(
        (id: string) => {
            if (id === selectedCubeRef.current) {
                setSelectedCube(null, null);
                return;
            }
            const unit = cubes.find((c) => c.id === id)?.data ?? null;
            setSelectedCube(id, unit);
        },
        [cubes, setSelectedCube],
    );

    const layerLabels = useMemo(() => boxes.map((box) => box.label), [boxes]);

    return (
        <div className="relative w-full h-full min-h-0">
            <Canvas
                className="w-full h-full"
                dpr={[1, 1.5]}
                frameloop={vibrationEnabled && isPageVisible && !contextLost ? "always" : "demand"}
                camera={{ position: [0, 0, 20], fov: 60 }}
                onCreated={({ gl }) => setCanvasEl(gl.domElement)}
            >
                <ambientLight intensity={0.5} />
                <directionalLight position={[5, 10, 7]} intensity={1} />

                {boxes.map((box, idx) => {
                    const safeBoxSize = box.boxSize.map((v) => (isNaN(v) ? 1 : v)) as [number, number, number];
                    return (
                        <LayerBox
                            key={`box-${idx}`}
                            position={box.boxPos}
                            size={safeBoxSize}
                        />
                    );
                })}

                {cubesByRow.map((rowCubes, idx) => (
                    <CompUnitRow
                        key={`row-${idx}`}
                        cubes={rowCubes}
                        onSelectCube={onSelectCube}
                        vibrationEnabled={vibrationEnabled}
                    />
                ))}

                <DependencyLinesLayer
                    cubes={cubes}
                    classPosMap={classPosMap}
                    selectedCube={selectedCube}
                    selectedUnit={selectedUnit}
                />

                <CameraControls />
            </Canvas>
            {contextLost && (
                <div
                    role="alert"
                    className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-white/90 p-6 text-center"
                >
                    <p className="text-base font-semibold text-gray-800 mb-2">
                        WebGL rendering context was lost.
                    </p>
                    <p className="text-sm text-gray-600 mb-4">
                        Waiting for the browser to restore the graphics context, or reload the page.
                    </p>
                    <button
                        type="button"
                        onClick={() => window.location.reload()}
                        className="px-4 py-2 rounded-lg bg-swamp-600 text-white text-sm font-medium hover:bg-swamp-700 transition-colors"
                    >
                        Reload page
                    </button>
                </div>
            )}
            <LayerLabelsOverlay labels={layerLabels} />
        </div>
    );
};

export default CompUnitsScene;
