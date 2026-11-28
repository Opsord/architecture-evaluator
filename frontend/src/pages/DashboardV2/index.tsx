import React, { useCallback, useEffect, useState } from "react";
import { useProjectContext } from "../../context/ProjectContext";
import { useNavigation } from "../../context/NavigationContext";
import { IS_DEMO_MODE } from "../../config/demo";
import CompUnitsScene from "./components/canvas/CompUnitsScene.tsx";
import ProcessedClassInfoCard from "./components/info/ProcessedClassInfoCard.tsx";
import DashboardLegend from "./components/legend/DashboardLegend.tsx";
import CanvasNavigationTips from "./components/navigation/CanvasNavigationTips.tsx";
import type { ProcessedClassInstance } from "../../types/ProcessedClassInstance.ts";

const DashboardV2: React.FC = () => {
    const { projectData, isLoadingInitialData, initialDataError, retryLoadInitialData } = useProjectContext();
    const { setCurrentPage } = useNavigation();
    const [selectedCube, setSelectedCube] = useState<string | null>(null);
    const [selectedUnit, setSelectedUnit] = useState<ProcessedClassInstance | null>(null);
    const [vibrationEnabled, setVibrationEnabled] = useState(true);

    useEffect(() => {
        setSelectedCube(null);
        setSelectedUnit(null);
    }, [projectData]);

    const handleSelectCube = useCallback(
        (name: string | null, unit: ProcessedClassInstance | null) => {
            setSelectedCube(name);
            setSelectedUnit(unit);
        },
        [],
    );

    if (!projectData) {
        if (isLoadingInitialData) {
            return (
                <div className="flex flex-1 items-center justify-center min-h-[60vh] text-gray-500">
                    Loading sample project…
                </div>
            );
        }

        if (initialDataError) {
            return (
                <div className="flex flex-1 flex-col items-center justify-center min-h-[60vh] gap-4 text-center px-4">
                    <p className="text-gray-600">{initialDataError}</p>
                    <button
                        type="button"
                        onClick={retryLoadInitialData}
                        className="px-5 py-2.5 rounded-lg bg-primary text-white font-semibold hover:bg-primary-dark transition-colors"
                    >
                        Retry
                    </button>
                </div>
            );
        }

        return (
            <div className="flex flex-1 items-center justify-center min-h-[60vh] text-gray-500">
                No data available. Please analyze a project first.
            </div>
        );
    }

    return (
        <div className="flex-1 flex flex-col min-h-0 max-h-full h-full gap-3">
            {IS_DEMO_MODE && (
                <div className="shrink-0 flex flex-wrap items-center justify-between gap-2 px-4 py-2 bg-white/90 border border-gray-border rounded-xl shadow-sm text-sm">
                    <span className="text-gray-dark">
                        <span className="font-semibold text-primary-dark">Sample Project:</span>{" "}
                        {projectData.projectName} (Spring Boot demo dataset)
                    </span>
                    <button
                        type="button"
                        onClick={() => setCurrentPage('instructions')}
                        className="text-primary-dark font-semibold hover:underline"
                    >
                        How to read metrics
                    </button>
                </div>
            )}
            <div className="grid grid-cols-5 grid-rows-8 gap-4 flex-1 min-h-0 max-h-full">
                {/* 1. Legend (Left sidebar) */}
                <div className="row-span-8 bg-white rounded-xl shadow p-0 flex overflow-hidden">
                    <DashboardLegend />
                </div>

                {/* 2. Canvas Scene (Main area) */}
                <div className="col-span-3 row-span-7 col-start-2 row-start-2 bg-white rounded-xl shadow overflow-hidden flex">
                    <CompUnitsScene
                        projectData={projectData}
                        selectedCube={selectedCube}
                        setSelectedCube={handleSelectCube}
                        vibrationEnabled={vibrationEnabled}
                    />
                </div>

                {/* 3. Vibration Toggle (Top right) */}
                <div className="col-start-5 row-start-1 bg-white rounded-xl shadow p-3 flex flex-row items-center justify-center gap-2 min-h-[56px]">
                    <label htmlFor="vibration-toggle" className="flex items-center cursor-pointer">
                        <div className="relative">
                            <input
                                type="checkbox"
                                id="vibration-toggle"
                                checked={vibrationEnabled}
                                onChange={e => setVibrationEnabled(e.target.checked)}
                                className="sr-only"
                            />
                            <div className={`block w-10 h-6 rounded-full transition-colors duration-200 ${vibrationEnabled ? "bg-swamp-500" : "bg-gray-300"}`}></div>
                            <div
                                className={`dot absolute left-1 top-1 bg-white w-4 h-4 rounded-full transition transform duration-200 ${vibrationEnabled ? "translate-x-4" : ""}`}
                            ></div>
                        </div>
                        <span className="ml-2 text-sm text-gray-700 select-none">Vibration</span>
                    </label>
                </div>

                {/* 4. Info Card (Right sidebar) */}
                <div className="row-span-7 col-start-5 row-start-2 bg-white rounded-xl shadow p-4 h-full flex flex-col overflow-y-auto">
                    <ProcessedClassInfoCard unit={selectedUnit} />
                </div>

                {/* 6. Canvas Navigation Tips (Top bar) */}
                <div className="col-span-3 col-start-2 row-start-1 bg-gradient-to-r from-background-light to-swamp-100 rounded-xl shadow border border-gray-border">
                    <CanvasNavigationTips />
                </div>
            </div>
        </div>
    );
};

export default DashboardV2;