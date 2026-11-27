import React, { createContext, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

export type PageType = 'home' | 'instructions' | 'load' | 'dashboard';

interface NavigationContextType {
  currentPage: PageType;
  setCurrentPage: (page: PageType) => void;
}

const NavigationContext = createContext<NavigationContextType | undefined>(undefined);

export const NavigationProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [currentPage, setCurrentPage] = useState<PageType>('home');
  const value = useMemo(() => ({ currentPage, setCurrentPage }), [currentPage]);

  return (
    <NavigationContext.Provider value={value}>
      {children}
    </NavigationContext.Provider>
  );
};

export const useNavigation = () => {
  const context = useContext(NavigationContext);
  if (context === undefined) {
    throw new Error('useNavigation must be used within a NavigationProvider');
  }
  return context;
};
