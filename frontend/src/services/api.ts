import axios from 'axios'
import type { ProjectAnalysisDTO } from '../types/ProjectAnalysisInstance.ts'

const api = axios.create({
    baseURL: '/api/orchestrator',
    timeout: 180_000,
    headers: {
        'Accept': 'application/json',
    },
})

export const analyzeProjectUpload = (
    file: File,
    signal?: AbortSignal,
): Promise<{ data: ProjectAnalysisDTO }> => {
    const formData = new FormData()
    formData.append('project', file)

    return api.post('/analyze-upload', formData, {
        signal,
    })
}

export const analyzeGitHubRepo = (
    repoUrl: string,
    signal?: AbortSignal,
): Promise<{ data: ProjectAnalysisDTO }> =>
    api.post('/analyze-github', null, {
        params: { repoUrl },
        signal,
    })

export function isRequestCanceled(error: unknown): boolean {
    return (
        axios.isCancel(error) ||
        (typeof error === 'object' &&
            error !== null &&
            'code' in error &&
            (error as { code?: string }).code === 'ERR_CANCELED')
    )
}

export function extractApiErrorMessage(error: unknown): string {
    if (axios.isAxiosError(error)) {
        if (error.code === 'ECONNABORTED') {
            return 'The analysis request timed out. Please try a smaller project or retry later.'
        }

        const data = error.response?.data
        if (typeof data === 'object' && data !== null) {
            const problem = data as { detail?: unknown; title?: unknown; message?: unknown }
            if (typeof problem.detail === 'string' && problem.detail.trim().length > 0) {
                return problem.detail
            }
            if (typeof problem.title === 'string' && problem.title.trim().length > 0) {
                return problem.title
            }
            if (typeof problem.message === 'string' && problem.message.trim().length > 0) {
                return problem.message
            }
        }

        const status = error.response?.status
        if (status === 413) {
            return 'The uploaded archive exceeds the maximum allowed size of 150 MB.'
        }
        if (status === 404) {
            return 'The requested repository or resource could not be found.'
        }
        if (status === 502 || status === 503 || status === 504) {
            return 'The analysis service is temporarily unavailable or timed out. Please try again.'
        }
        if (!error.response) {
            return 'Unable to reach the analysis server. Please check your connection and try again.'
        }
    }

    return 'An unexpected error occurred while analyzing the project.'
}
