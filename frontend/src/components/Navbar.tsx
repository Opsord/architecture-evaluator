import React from "react";
import { useNavigation } from "../context/NavigationContext";
import { IS_DEMO_MODE } from "../config/demo";

const Navbar: React.FC = () => {
  const { currentPage, setCurrentPage } = useNavigation();

  return (
      <nav className="bg-gradient-to-r from-primary via-bright-turquoise-400 to-bright-turquoise-600 shadow-lg">
        <div className="max-w-screen-xl mx-auto flex flex-wrap items-center justify-between gap-3 p-4">
          {/* Left: Logo and App Name */}
          <button
            type="button"
            onClick={() => setCurrentPage('home')}
            className="flex items-center gap-3 hover:scale-105 transition-transform shrink-0"
          >
            <img
                src={`${import.meta.env.BASE_URL}archev-icon.svg`}
                className="h-8 drop-shadow-lg"
                alt="ArchEv Logo"
            />
            <span className="text-2xl font-bold text-white tracking-wide drop-shadow">
            ArchEv
          </span>
          </button>
          {/* Center: Navigation Links */}
          <ul className="flex gap-4 sm:gap-8 items-center mx-auto">
            <li>
              <button
                type="button"
                aria-current={currentPage === 'home' ? 'page' : undefined}
                onClick={() => setCurrentPage('home')}
                className={`font-medium transition-colors ${
                  currentPage === 'home' 
                    ? 'text-white border-b-2 border-white' 
                    : 'text-white/80 hover:text-white'
                }`}
              >
                Home
              </button>
            </li>
            <li>
              <button
                type="button"
                aria-current={currentPage === 'instructions' ? 'page' : undefined}
                onClick={() => setCurrentPage('instructions')}
                className={`font-medium transition-colors ${
                  currentPage === 'instructions' 
                    ? 'text-white border-b-2 border-white' 
                    : 'text-white/80 hover:text-white'
                }`}
              >
                Instructions
              </button>
            </li>
            <li>
              <button
                type="button"
                aria-current={currentPage === 'load' ? 'page' : undefined}
                onClick={() => setCurrentPage('load')}
                className={`font-medium transition-colors whitespace-nowrap ${
                  currentPage === 'load' 
                    ? 'text-white border-b-2 border-white' 
                    : 'text-white/80 hover:text-white'
                }`}
              >
                {IS_DEMO_MODE ? 'Run Locally' : 'Load'}
              </button>
            </li>
            <li>
              <button
                type="button"
                aria-current={currentPage === 'dashboard' ? 'page' : undefined}
                onClick={() => setCurrentPage('dashboard')}
                className={`font-medium transition-colors ${
                  currentPage === 'dashboard' 
                    ? 'text-white border-b-2 border-white' 
                    : 'text-white/80 hover:text-white'
                }`}
              >
                Dashboard
              </button>
            </li>
          </ul>
          {/* Right: Demo badge or spacer */}
          <div className="flex items-center justify-end shrink-0 min-w-[6rem]">
            {IS_DEMO_MODE && (
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold tracking-wide bg-white/20 text-white border border-white/30 backdrop-blur-sm whitespace-nowrap">
                <span className="w-2 h-2 rounded-full bg-bright-turquoise-100 animate-pulse" />
                Live Demo
              </span>
            )}
          </div>
        </div>
      </nav>
  );
};

export default Navbar;