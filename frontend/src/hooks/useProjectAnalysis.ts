import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigation } from '../context/NavigationContext'
import { useProjectContext } from '../context/ProjectContext'
import { extractApiErrorMessage, isRequestCanceled } from '../services/api'
import type { ProjectAnalysisDTO } from '../types/ProjectAnalysisInstance'

interface UseProjectAnalysisOptions {
    onLoadingChange?: (isLoading: boolean) => void
}

export function useProjectAnalysis(options?: UseProjectAnalysisOptions) {
    const [isLoading, setIsLoading] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const { setCurrentPage } = useNavigation()
    const { setProjectData } = useProjectContext()
    const abortRef = useRef<AbortController | null>(null)
    const onLoadingChangeRef = useRef(options?.onLoadingChange)
    onLoadingChangeRef.current = options?.onLoadingChange

    useEffect(() => {
        return () => {
            abortRef.current?.abort()
            onLoadingChangeRef.current?.(false)
        }
    }, [])

    const clearError = useCallback(() => {
        setErrorMessage(null)
    }, [])

    const runAnalysis = useCallback(
        async (request: (signal: AbortSignal) => Promise<{ data: ProjectAnalysisDTO }>) => {
            abortRef.current?.abort()
            const controller = new AbortController()
            abortRef.current = controller

            setErrorMessage(null)
            setIsLoading(true)
            onLoadingChangeRef.current?.(true)

            try {
                const response = await request(controller.signal)
                if (controller.signal.aborted) {
                    return
                }
                setProjectData(response.data)
                setCurrentPage('dashboard')
            } catch (error) {
                if (isRequestCanceled(error)) {
                    return
                }
                console.error('Project analysis error:', error)
                setErrorMessage(extractApiErrorMessage(error))
            } finally {
                if (abortRef.current === controller) {
                    setIsLoading(false)
                    onLoadingChangeRef.current?.(false)
                }
            }
        },
        [setCurrentPage, setProjectData],
    )

    return {
        isLoading,
        errorMessage,
        setErrorMessage,
        clearError,
        runAnalysis,
    }
}
