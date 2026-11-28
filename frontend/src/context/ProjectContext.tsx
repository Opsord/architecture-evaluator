import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ProjectAnalysisDTO } from "../types/ProjectAnalysisInstance.ts";

interface ProjectContextType {
    projectData: ProjectAnalysisDTO | null;
    setProjectData: (data: ProjectAnalysisDTO) => void;
    isLoadingInitialData: boolean;
    initialDataError: string | null;
    retryLoadInitialData: () => void;
}

const ProjectContext = createContext<ProjectContextType | undefined>(undefined);

export const ProjectProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const shouldLoadSample = import.meta.env.DEV || import.meta.env.VITE_DEMO_MODE === 'true';
    const [projectData, setProjectDataState] = useState<ProjectAnalysisDTO | null>(null);
    const [isLoadingInitialData, setIsLoadingInitialData] = useState<boolean>(shouldLoadSample);
    const [initialDataError, setInitialDataError] = useState<string | null>(null);
    const [loadAttempt, setLoadAttempt] = useState(0);

    useEffect(() => {
        if (!import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'true') {
            return;
        }
        let cancelled = false;
        setIsLoadingInitialData(true);
        setInitialDataError(null);

        void import("../services/MockData/response-zip.json")
            .then((module) => {
                if (cancelled) {
                    return;
                }
                setProjectDataState((current) => current ?? (module.default as unknown as ProjectAnalysisDTO));
            })
            .catch((error: unknown) => {
                if (cancelled) {
                    return;
                }
                console.error("Failed to load sample project data:", error);
                setInitialDataError("Unable to load the sample project data. Please try again.");
            })
            .finally(() => {
                if (!cancelled) {
                    setIsLoadingInitialData(false);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [loadAttempt]);

    const setProjectData = useCallback((data: ProjectAnalysisDTO) => {
        setProjectDataState(data);
        setIsLoadingInitialData(false);
        setInitialDataError(null);
    }, []);

    const retryLoadInitialData = useCallback(() => {
        setLoadAttempt((attempt) => attempt + 1);
    }, []);

    const value = useMemo(
        () => ({
            projectData,
            setProjectData,
            isLoadingInitialData,
            initialDataError,
            retryLoadInitialData,
        }),
        [projectData, setProjectData, isLoadingInitialData, initialDataError, retryLoadInitialData],
    );

    return (
        <ProjectContext.Provider value={value}>
            {children}
        </ProjectContext.Provider>
    );
};

export const useProjectContext = (): ProjectContextType => {
    const context = useContext(ProjectContext);
    if (!context) {
        throw new Error("useProjectContext must be used within a ProjectProvider");
    }
    return context;
};
