import React from 'react';
import { useApp } from '../context/AppContext';
import { motion } from 'motion/react';

export const BottomNav: React.FC = () => {
  const { currentScreen, navigateTo, openAddHolding } = useApp();

  const tabs = [
    { id: 'asset-management', label: '資産管理', icon: 'account_balance_wallet' },
    { id: 'annual-dividend', label: '年間配当', icon: 'pie_chart' },
    { id: 'roadmap', label: 'ロードマップ', icon: 'flag' },
    { id: 'watchlist', label: 'Watchlist', icon: 'bookmark' },
  ] as const;

  return (
    <>
      {/* Floating Action Button (FAB) positioned above the bottom nav */}
      <aside className="fixed right-4 bottom-20 z-30">
        <motion.button
          type="button"
          onClick={openAddHolding}
          aria-label="保有銘柄を追加"
          whileHover={{ scale: 1.08 }}
          whileTap={{ scale: 0.92 }}
          transition={{ type: 'spring', stiffness: 400, damping: 18 }}
          className="fab-pulse w-13 h-13 rounded-full bg-[#10b981] text-[#003824] flex items-center justify-center shadow-[0_8px_24px_rgba(16,185,129,0.45)] hover:brightness-110 active:scale-95 transition-all cursor-pointer relative"
        >
          <span className="material-symbols-outlined text-[28px] font-bold">add</span>
        </motion.button>
      </aside>

      {/* Fixed Bottom Navigation Bar */}
      <nav className="fixed bottom-0 inset-x-0 z-40 bg-[#0b1326]/95 backdrop-blur-xl border-t border-slate-800 pb-safe">
        <div className="grid grid-cols-4 items-center h-16 max-w-lg mx-auto px-2 relative">
          {tabs.map((tab) => {
            const isActive = currentScreen === tab.id;
            return (
              <motion.button
                key={tab.id}
                type="button"
                onClick={() => navigateTo(tab.id)}
                whileTap={{ scale: 0.93 }}
                className={`relative flex flex-col items-center justify-center gap-0.5 h-12 transition-colors cursor-pointer ${
                  isActive ? 'text-[#4edea3] font-bold' : 'text-[#bbcabf] hover:text-white'
                }`}
              >
                {/* Gliding Active Pill / Indicator */}
                {isActive && (
                  <motion.div
                    layoutId="activeTabIndicator"
                    className="absolute -top-1 w-8 h-1 rounded-full bg-[#10b981] shadow-[0_0_10px_rgba(16,185,129,0.8)]"
                    transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                  />
                )}
                <span
                  className="material-symbols-outlined text-[22px]"
                  style={{
                    fontVariationSettings: isActive ? "'FILL' 1" : "'FILL' 0"
                  }}
                >
                  {tab.icon}
                </span>
                <span className="text-[10px] tracking-tight">{tab.label}</span>
              </motion.button>
            );
          })}
        </div>
      </nav>
    </>
  );
};
