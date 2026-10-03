import React from 'react';
import { AppProvider, useApp } from './context/AppContext';
import { Header } from './components/Header';
import { BottomNav } from './components/BottomNav';
import { Toast } from './components/Toast';

// Screens
import { AssetManagementScreen } from './screens/AssetManagementScreen';
import { AnnualDividendScreen } from './screens/AnnualDividendScreen';
import { RoadmapScreen } from './screens/RoadmapScreen';
import { WatchlistScreen } from './screens/WatchlistScreen';
import { StockDetailScreen } from './screens/StockDetailScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { CsvImportScreen } from './screens/CsvImportScreen';
import { ContactFeedbackScreen } from './screens/ContactFeedbackScreen';
import { TermsPrivacyScreen } from './screens/TermsPrivacyScreen';

// Modals
import { SearchModal } from './components/modals/SearchModal';
import { NotificationModal } from './components/modals/NotificationModal';
import { QuickEditModal } from './components/modals/QuickEditModal';
import { AddHoldingModal } from './components/modals/AddHoldingModal';

const AppContent: React.FC = () => {
  const { currentScreen } = useApp();

  const renderScreen = () => {
    switch (currentScreen) {
      case 'asset-management':
        return <AssetManagementScreen />;
      case 'annual-dividend':
        return <AnnualDividendScreen />;
      case 'roadmap':
        return <RoadmapScreen />;
      case 'watchlist':
        return <WatchlistScreen />;
      case 'stock-detail':
        return <StockDetailScreen />;
      case 'settings':
        return <SettingsScreen />;
      case 'csv-import':
        return <CsvImportScreen />;
      case 'contact-feedback':
        return <ContactFeedbackScreen />;
      case 'terms-privacy':
        return <TermsPrivacyScreen />;
      default:
        return <AssetManagementScreen />;
    }
  };

  return (
    <div className="min-h-screen bg-[#0b1326] text-[#dae2fd] font-sans flex flex-col relative antialiased">
      <Header />
      <main className="flex-1 w-full">
        {renderScreen()}
      </main>
      <BottomNav />

      {/* Global Modals */}
      <SearchModal />
      <NotificationModal />
      <QuickEditModal />
      <AddHoldingModal />
      <Toast />
    </div>
  );
};

export default function App() {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
}
