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
import { JetpackComposeScreen } from './screens/JetpackComposeScreen';
import { AnimatePresence, motion } from 'motion/react';

// Modals
import { SearchModal } from './components/modals/SearchModal';
import { NotificationModal } from './components/modals/NotificationModal';
import { QuickEditModal } from './components/modals/QuickEditModal';
import { AddHoldingModal } from './components/modals/AddHoldingModal';

const AppContent: React.FC = () => {
  const { currentScreen, themeMode, isOledBlack } = useApp();

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
      case 'jetpack-compose':
        return <JetpackComposeScreen />;
      default:
        return <AssetManagementScreen />;
    }
  };

  return (
    <div className={`min-h-screen ${
      themeMode === 'light'
        ? 'bg-[#f1f5f9] text-[#1e293b]'
        : isOledBlack
        ? 'bg-black text-[#dae2fd]'
        : 'bg-[#0b1326] text-[#dae2fd]'
    } font-sans flex flex-col relative antialiased transition-colors duration-200`}>
      <Header />
      <main className="flex-1 w-full overflow-x-hidden">
        <AnimatePresence mode="wait">
          <motion.div
            key={currentScreen}
            initial={{ opacity: 0, y: 10, scale: 0.995 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -10, scale: 0.995 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="w-full"
          >
            {renderScreen()}
          </motion.div>
        </AnimatePresence>
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
