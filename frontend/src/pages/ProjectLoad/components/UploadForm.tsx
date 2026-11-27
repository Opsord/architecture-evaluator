import { useState } from 'react'
import * as React from 'react'
import { analyzeProjectUpload } from '../../../services/api'
import { useProjectAnalysis } from '../../../hooks/useProjectAnalysis'

const MAX_ARCHIVE_BYTES = 150 * 1024 * 1024
const SUPPORTED_ARCHIVE_REGEX = /\.(zip|tar)$/i

interface UploadFormProps {
    onLoadingChange?: (isLoading: boolean) => void
}

export default function UploadForm({ onLoadingChange }: UploadFormProps) {
    const [file, setFile] = useState<File | null>(null)
    const { isLoading, errorMessage, setErrorMessage, clearError, runAnalysis } =
        useProjectAnalysis({ onLoadingChange })

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        clearError()
        const selected = e.target.files?.[0] || null
        if (!selected) {
            setFile(null)
            return
        }
        if (!SUPPORTED_ARCHIVE_REGEX.test(selected.name)) {
            setFile(null)
            e.target.value = ''
            setErrorMessage('Unsupported archive format. Please select a .zip or .tar file.')
            return
        }
        if (selected.size > MAX_ARCHIVE_BYTES) {
            setFile(null)
            e.target.value = ''
            setErrorMessage('The selected archive exceeds the 150 MB limit.')
            return
        }
        setFile(selected)
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!file) return
        await runAnalysis((signal) => analyzeProjectUpload(file, signal))
    }

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            <div>
                <label
                    htmlFor="project-archive-input"
                    className="block text-lg font-semibold text-primary mb-2"
                >
                    Project archive (.zip or .tar)
                </label>
                <input
                    id="project-archive-input"
                    type="file"
                    accept=".zip,.tar"
                    disabled={isLoading}
                    onChange={handleFileChange}
                    className="block w-full text-base text-gray-700
                        file:mr-4 file:py-3 file:px-6
                        file:rounded-lg file:border-0
                        file:text-base file:font-semibold
                        file:bg-bright-turquoise-100 file:text-primary
                        hover:file:bg-bright-turquoise-200
                        disabled:opacity-60"
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
                disabled={!file || isLoading}
                className={`w-full py-4 text-xl rounded-lg font-bold transition text-white ${
                    !file || isLoading
                        ? 'bg-gray-400 cursor-not-allowed'
                        : 'bg-gradient-to-r from-primary to-bright-turquoise-400 hover:scale-105 hover:shadow-xl'
                }`}
            >
                {isLoading ? 'Analyzing...' : 'Analyze project'}
            </button>
        </form>
    )
}
