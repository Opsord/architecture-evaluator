import React, { Suspense, lazy } from 'react';
import { useNavigation } from '../context/NavigationContext';
import ErrorBoundary from './ErrorBoundary';
import Home from '../pages/Home';
import Instructions from '../pages/Instructions';
import ProjectLoad from '../pages/ProjectLoad';

const DashboardV2 = lazy(() => import('../pages/DashboardV2'));

const MainContent: React.FC = () => {
  const { currentPage, setCurrentPage } = useNavigation();

  const renderPage = () => {
    switch (currentPage) {
      case 'home':
        return <Home />;
      case 'instructions':
        return <Instructions />;
      case 'load':
        return <ProjectLoad />;
      case 'dashboard':
        return (
          <ErrorBoundary onGoHome={() => setCurrentPage('home')}>
            <Suspense fallback={<div className="p-8 text-center text-gray-500">Loading dashboard…</div>}>
              <DashboardV2 />
            </Suspense>
          </ErrorBoundary>
        );
      default:
        return <Home />;
    }
  };

  const containerClasses = currentPage === 'dashboard'
    ? "w-full h-full flex flex-col min-h-0 animate-fadeIn"
    : "w-full flex flex-col animate-fadeIn";

  return (
    <div className={containerClasses}>
      {renderPage()}
    </div>
  );
};

export default MainContent;
