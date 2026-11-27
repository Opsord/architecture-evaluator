import React from 'react';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  onGoHome: () => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  errorMessage: string | null;
}

class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, errorMessage: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return {
      hasError: true,
      errorMessage: error?.message || 'An unexpected rendering error occurred.',
    };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    console.error('Dashboard ErrorBoundary caught an error:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="flex flex-col items-center justify-center min-h-[320px] p-8 text-center bg-white rounded-xl shadow border border-gray-200 max-w-lg mx-auto my-auto"
        >
          <h2 className="text-xl font-bold text-gray-800 mb-2">
            Unable to display the 3D Dashboard
          </h2>
          <p className="text-sm text-gray-600 mb-6">
            {this.state.errorMessage}
          </p>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="px-4 py-2 rounded-lg bg-swamp-600 text-white text-sm font-medium hover:bg-swamp-700 transition-colors"
            >
              Reload page
            </button>
            <button
              type="button"
              onClick={this.props.onGoHome}
              className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-sm font-medium hover:bg-gray-50 transition-colors"
            >
              Back to Home
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
