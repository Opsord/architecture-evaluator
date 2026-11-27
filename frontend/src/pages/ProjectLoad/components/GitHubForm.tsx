import { useState } from 'react'
import * as React from 'react'
import { analyzeGitHubRepo } from '../../../services/api'
import { useProjectAnalysis } from '../../../hooks/useProjectAnalysis'

const GITHUB_URL_REGEX = /^(https?:\/\/)?(www\.)?github\.com\/[\w-]+\/[\w.-]+\/?$/

function isValidGitHubRepoUrl(url: string): boolean {
    if (!GITHUB_URL_REGEX.test(url)) {
        return false
    }
    const cleaned = url.replace(/^(https?:\/\/)?(www\.)?github\.com\//, '').replace(/\/$/, '')
    const parts = cleaned.split('/')
    return (
        parts.length === 2 &&
        parts.every((segment) => segment !== '.' && segment !== '..' && !segment.includes('..'))
    )
}

interface GitHubFormProps {
    onLoadingChange?: (isLoading: boolean) => void
}

export default function GitHubForm({ onLoadingChange }: GitHubFormProps) {
    const [repoUrl, setRepoUrl] = useState('')
    const { isLoading, errorMessage, setErrorMessage, clearError, runAnalysis } =
        useProjectAnalysis({ onLoadingChange })

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        const trimmed = repoUrl.trim()
        if (!trimmed) return

        if (!isValidGitHubRepoUrl(trimmed)) {
            setErrorMessage('Please enter a valid GitHub repository URL (e.g. https://github.com/owner/repo).')
            return
        }

        await runAnalysis((signal) => analyzeGitHubRepo(trimmed, signal))
    }

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            <div>
                <label
                    htmlFor="github-repo-input"
                    className="block text-lg font-semibold text-primary mb-2"
                >
                    GitHub repository URL
                </label>
                <input
                    id="github-repo-input"
                    type="text"
                    placeholder="https://github.com/owner/repository"
                    value={repoUrl}
                    disabled={isLoading}
                    onChange={(e) => {
                        clearError()
                        setRepoUrl(e.target.value)
                    }}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg text-base disabled:opacity-60"
                />
            </div>
            {errorMessage && (
                <div
                    role="alert"
                    className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
                >
                    {errorMessage}
                </div>
            )}
            <button
                type="submit"
                disabled={!repoUrl.trim() || isLoading}
                className={`w-full py-4 text-xl rounded-lg font-bold transition text-white ${
                    !repoUrl.trim() || isLoading
                        ? 'bg-gray-400 cursor-not-allowed'
                        : 'bg-gradient-to-r from-primary to-bright-turquoise-400 hover:scale-105 hover:shadow-xl'
                }`}
            >
                {isLoading ? 'Processing...' : 'Clone and Analyze'}
            </button>
        </form>
    )
}
