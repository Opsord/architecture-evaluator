import { useState } from 'react'
import { FaCube, FaGithub, FaTerminal } from 'react-icons/fa'
import { IS_DEMO_MODE, REPO_URL } from '../../config/demo'
import { useNavigation } from '../../context/NavigationContext'
import UploadForm from './components/UploadForm'
import GitHubForm from './components/GitHubForm'
import OptionSelector from './components/OptionSelector'

function DemoProjectLoad() {
    const { setCurrentPage } = useNavigation()

    return (
        <div className="flex flex-col items-center min-h-[80vh] py-12 px-4">
            <div className="w-full max-w-3xl mb-10">
                <h1 className="text-5xl font-extrabold text-center text-primary drop-shadow-lg">
                    Run Locally to Analyze
                </h1>
                <p className="text-lg text-gray-dark text-center mt-4">
                    This GitHub Pages site runs in static demo mode without the Spring Boot analysis backend.
                </p>
            </div>
            <div className="w-full max-w-3xl bg-white/90 backdrop-blur-lg border border-gray-border rounded-2xl shadow-xl p-8 space-y-6">
                <div className="flex items-start gap-4">
                    <div className="p-3 rounded-xl bg-bright-turquoise-100 text-primary shrink-0">
                        <FaTerminal className="text-2xl" />
                    </div>
                    <div className="space-y-2">
                        <h2 className="text-xl font-bold text-gray-dark">
                            Analyze your own Java / Spring Boot projects
                        </h2>
                        <p className="text-gray-600 text-sm leading-relaxed">
                            Clone the repository and start the full stack with Docker Compose to upload ZIP archives or scan GitHub repositories directly.
                        </p>
                    </div>
                </div>

                <pre className="bg-gray-900 text-bright-turquoise-100 rounded-xl p-4 text-sm overflow-x-auto">
                    <code>{`git clone ${REPO_URL}.git\ncd architecture-evaluator\ndocker compose up --build`}</code>
                </pre>

                <div className="flex flex-col sm:flex-row gap-4 pt-2">
                    <button
                        type="button"
                        onClick={() => setCurrentPage('dashboard')}
                        className="flex-1 flex items-center justify-center gap-3 py-3.5 px-6 bg-gradient-to-r from-primary to-bright-turquoise-400 text-white font-bold rounded-xl shadow-lg hover:scale-[1.02] transition-transform"
                    >
                        <FaCube className="text-xl" />
                        Explore Preloaded Dashboard
                    </button>
                    <a
                        href={REPO_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center justify-center gap-2 py-3.5 px-6 bg-white text-primary font-semibold rounded-xl border border-primary/40 hover:bg-bright-turquoise-50 transition-colors"
                    >
                        <FaGithub className="text-xl" />
                        View on GitHub
                    </a>
                </div>
            </div>
        </div>
    )
}

function LiveProjectLoad() {
    const [activeTab, setActiveTab] = useState<'zip' | 'github'>('zip')
    const [isAnalyzing, setIsAnalyzing] = useState(false)

    return (
        <div className="flex flex-col items-center min-h-[80vh] py-12 px-4">
            {/* Title */}
            <div className="w-full max-w-4xl mb-10">
                <h1 className="text-5xl font-extrabold text-center text-primary drop-shadow-lg">
                    Load your project
                </h1>
                <p className="text-lg text-gray-medium text-center mt-4">
                    Choose how you want to load your project for analysis.
                </p>
            </div>
            <div className="w-full max-w-4xl grid grid-cols-1 md:grid-cols-2 gap-10">
                {/* Selector (left section) */}
                <div className="flex items-center justify-center">
                    <div className="w-full h-full max-w-md bg-gradient-to-br from-white/70 to-gray-100/70 backdrop-blur-lg border border-gray-border rounded-2xl shadow-xl p-8 flex flex-col justify-center">
                        <OptionSelector
                            value={activeTab}
                            onChange={setActiveTab}
                            disabled={isAnalyzing}
                        />
                    </div>
                </div>
                {/* Form (right section) */}
                <div className="flex items-center justify-center">
                    <div className="w-full h-full max-w-md bg-gradient-to-br from-white/80 to-bright-turquoise-50/80 backdrop-blur-lg border border-gray-border rounded-2xl shadow-xl p-8 flex flex-col justify-center">
                        {activeTab === 'zip' ? (
                            <UploadForm onLoadingChange={setIsAnalyzing} />
                        ) : (
                            <GitHubForm onLoadingChange={setIsAnalyzing} />
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}

function ProjectLoad() {
    return IS_DEMO_MODE ? <DemoProjectLoad /> : <LiveProjectLoad />
}

export default ProjectLoad
