import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ProjectAnalysisDTO } from "../types/ProjectAnalysisInstance.ts";

interface ProjectContextType {
    projectData: ProjectAnalysisDTO | null;
    setProjectData: (data: ProjectAnalysisDTO) => void;
}

const ProjectContext = createContext<ProjectContextType | undefined>(undefined);

export const ProjectProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [projectData, setProjectDataState] = useState<ProjectAnalysisDTO | null>(null);

    useEffect(() => {
        if (!import.meta.env.DEV) {
            return;
        }
        let cancelled = false;
        void import("../services/MockData/response-zip.json").then((module) => {
            if (cancelled) {
                return;
            }
            setProjectDataState((current) => current ?? (module.default as unknown as ProjectAnalysisDTO));
        });
        return () => {
            cancelled = true;
        };
    }, []);

    const setProjectData = useCallback((data: ProjectAnalysisDTO) => {
        setProjectDataState(data);
    }, []);

    const value = useMemo(() => ({ projectData, setProjectData }), [projectData, setProjectData]);

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
