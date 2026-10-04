import React, { createContext, useContext, useState, useEffect } from 'react';
import {
  ScreenType,
  Holding,
  WatchlistStock,
  StateSimMode,
  ThemeMode,
  AccentColor,
  ToastMessage
} from '../types';
import { initialHoldings, initialWatchlist } from '../data/mockData';

interface AppContextType {
  currentScreen: ScreenType;
  selectedStockCode: string;
  holdings: Holding[];
  watchlist: WatchlistStock[];
  isPrivacyActive: boolean;
  themeMode: ThemeMode;
  isOledBlack: boolean;
  accentColor: AccentColor;
  stateSim: StateSimMode;
  toast: ToastMessage | null;
  // Modals
  isSearchOpen: boolean;
  isNotificationsOpen: boolean;
  isAddHoldingOpen: boolean;
  quickEditHolding: Holding | null;
  // Actions
  navigateTo: (screen: ScreenType, stockCode?: string) => void;
  goBack: () => void;
  togglePrivacy: () => void;
  setThemeMode: (mode: ThemeMode) => void;
  setIsOledBlack: (oled: boolean) => void;
  setAccentColor: (accent: AccentColor) => void;
  setStateSim: (mode: StateSimMode) => void;
  showToast: (message: string, icon?: string, actionText?: string, onAction?: () => void) => void;
  clearToast: () => void;
  updateHolding: (code: string, shares: number, acqPrice: number) => void;
  addHolding: (holding: Holding) => void;
  removeHolding: (code: string) => void;
  toggleWatchlistStock: (code: string, force?: boolean) => void;
  updateTargetYield: (code: string, delta: number) => void;
  openSearch: () => void;
  closeSearch: () => void;
  openNotifications: () => void;
  closeNotifications: () => void;
  openAddHolding: () => void;
  closeAddHolding: () => void;
  openQuickEdit: (holding: Holding) => void;
  closeQuickEdit: () => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

const accentColorMap: Record<AccentColor, { color: string; light: string; rgb: string; name: string }> = {
  emerald: { color: '#10b981', light: '#4edea3', rgb: '16, 185, 129', name: 'エメラルド' },
  cyan: { color: '#06b6d4', light: '#22d3ee', rgb: '6, 182, 212', name: 'シアン' },
  blue: { color: '#3b82f6', light: '#60a5fa', rgb: '59, 130, 246', name: 'ブルー' },
  amber: { color: '#f59e0b', light: '#fbbf24', rgb: '245, 158, 11', name: 'アンバー' },
  purple: { color: '#8b5cf6', light: '#a78bfa', rgb: '139, 92, 246', name: 'パープル' }
};

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentScreen, setCurrentScreen] = useState<ScreenType>('asset-management');
  const [screenHistory, setScreenHistory] = useState<ScreenType[]>(['asset-management']);
  const [selectedStockCode, setSelectedStockCode] = useState<string>('7203');
  const [holdings, setHoldings] = useState<Holding[]>(initialHoldings);
  const [watchlist, setWatchlist] = useState<WatchlistStock[]>(initialWatchlist);
  const [isPrivacyActive, setIsPrivacyActive] = useState<boolean>(false);
  const [themeMode, setThemeModeState] = useState<ThemeMode>('dark');
  const [isOledBlack, setIsOledBlackState] = useState<boolean>(false);
  const [accentColor, setAccentColorState] = useState<AccentColor>('emerald');
  const [stateSim, setStateSim] = useState<StateSimMode>('normal');
  const [toast, setToast] = useState<ToastMessage | null>(null);

  // Modals state
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [isAddHoldingOpen, setIsAddHoldingOpen] = useState(false);
  const [quickEditHolding, setQuickEditHolding] = useState<Holding | null>(null);

  // Sync css variables when accentColor changes
  useEffect(() => {
    const root = document.documentElement;
    const item = accentColorMap[accentColor];
    if (item) {
      root.style.setProperty('--accent', item.color);
      root.style.setProperty('--accent-light', item.light);
      root.style.setProperty('--accent-glow', `rgba(${item.rgb}, 0.35)`);
      root.style.setProperty('--accent-dim', `rgba(${item.rgb}, 0.15)`);
    }
  }, [accentColor]);

  // Sync theme changes to body
  useEffect(() => {
    const body = document.body;
    body.classList.remove('theme-light', 'theme-oled');
    if (themeMode === 'light') {
      body.classList.add('theme-light');
      document.documentElement.classList.remove('dark');
    } else {
      document.documentElement.classList.add('dark');
      if (isOledBlack) {
        body.classList.add('theme-oled');
      }
    }
  }, [themeMode, isOledBlack]);

  const navigateTo = (screen: ScreenType, stockCode?: string) => {
    if (stockCode) setSelectedStockCode(stockCode);
    setScreenHistory(prev => [...prev, screen]);
    setCurrentScreen(screen);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const goBack = () => {
    if (screenHistory.length > 1) {
      const nextHistory = [...screenHistory];
      nextHistory.pop();
      const prevScreen = nextHistory[nextHistory.length - 1];
      setScreenHistory(nextHistory);
      setCurrentScreen(prevScreen);
    } else {
      setCurrentScreen('asset-management');
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const togglePrivacy = () => {
    setIsPrivacyActive(prev => !prev);
  };

  const showToast = (message: string, icon = 'check_circle', actionText?: string, onAction?: () => void) => {
    const id = Date.now().toString();
    setToast({ id, message, icon, actionText, onAction });
    
    // Trigger real native Android OS notification if running inside WebView APK!
    if (typeof window !== 'undefined' && (window as any).KuroganeAndroid) {
      try {
        (window as any).KuroganeAndroid.showSystemNotification("Kurogane Dividend", message);
      } catch (e) {
        console.error('Android bridge error:', e);
      }
    }

    setTimeout(() => {
      setToast(prev => (prev?.id === id ? null : prev));
    }, 3200);
  };

  const clearToast = () => {
    setToast(null);
  };

  const updateHolding = (code: string, shares: number, acqPrice: number) => {
    setHoldings(prev =>
      prev.map(h => {
        if (h.code === code) {
          const totalValuation = shares * h.price;
          const totalCost = shares * acqPrice;
          const diff = totalValuation - totalCost;
          const diffPct = totalCost > 0 ? ((diff / totalCost) * 100).toFixed(1) : '0';
          const newAnnualDiv = Math.round(shares * h.dps);
          const halfDiv = Math.round(newAnnualDiv / 2);
          return {
            ...h,
            shares,
            acqPrice,
            yoc: acqPrice > 0 ? Number(((h.dps / acqPrice) * 100).toFixed(2)) : h.yieldRate,
            nextPayoutAmount: `¥${halfDiv.toLocaleString()}`,
            dailyChange: diff >= 0 ? `+¥${diff.toLocaleString()} (+${diffPct}%)` : `-¥${Math.abs(diff).toLocaleString()} (${diffPct}%)`,
            isPositive: diff >= 0
          };
        }
        return h;
      })
    );
    showToast('保有情報を正常に更新しました');
  };

  const addHolding = (newHolding: Holding) => {
    setHoldings(prev => [newHolding, ...prev]);
    showToast(`${newHolding.name} (${newHolding.code}) を保有株に追加しました`);
  };

  const removeHolding = (code: string) => {
    setHoldings(prev => prev.filter(h => h.code !== code));
    showToast('銘柄の保有を解除しました', 'delete');
  };

  const toggleWatchlistStock = (code: string, force?: boolean) => {
    setWatchlist(prev =>
      prev.map(item => {
        if (item.code === code) {
          const newState = typeof force === 'boolean' ? force : !item.alertEnabled;
          return { ...item, alertEnabled: newState };
        }
        return item;
      })
    );
  };

  const updateTargetYield = (code: string, delta: number) => {
    setWatchlist(prev =>
      prev.map(item => {
        if (item.code === code) {
          const newTarget = Math.max(0.1, +(item.targetYield + delta).toFixed(2));
          const targetPrice = Math.floor(item.dps / (newTarget / 100));
          return {
            ...item,
            targetYield: newTarget,
            targetPrice
          };
        }
        return item;
      })
    );
  };

  return (
    <AppContext.Provider
      value={{
        currentScreen,
        selectedStockCode,
        holdings,
        watchlist,
        isPrivacyActive,
        themeMode,
        isOledBlack,
        accentColor,
        stateSim,
        toast,
        isSearchOpen,
        isNotificationsOpen,
        isAddHoldingOpen,
        quickEditHolding,
        navigateTo,
        goBack,
        togglePrivacy,
        setThemeMode: setThemeModeState,
        setIsOledBlack: setIsOledBlackState,
        setAccentColor: setAccentColorState,
        setStateSim,
        showToast,
        clearToast,
        updateHolding,
        addHolding,
        removeHolding,
        toggleWatchlistStock,
        updateTargetYield,
        openSearch: () => setIsSearchOpen(true),
        closeSearch: () => setIsSearchOpen(false),
        openNotifications: () => setIsNotificationsOpen(true),
        closeNotifications: () => setIsNotificationsOpen(false),
        openAddHolding: () => setIsAddHoldingOpen(true),
        closeAddHolding: () => setIsAddHoldingOpen(false),
        openQuickEdit: (h: Holding) => setQuickEditHolding(h),
        closeQuickEdit: () => setQuickEditHolding(null)
      }}
    >
      {children}
    </AppContext.Provider>
  );
};

export const useApp = () => {
  const context = useContext(AppContext);
  if (!context) throw new Error('useApp must be used within an AppProvider');
  return context;
};
