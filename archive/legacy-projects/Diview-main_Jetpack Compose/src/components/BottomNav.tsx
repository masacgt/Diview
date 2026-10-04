import React from 'react';
import { useApp } from '../context/AppContext';

export const BottomNav: React.FC = () => {
  const { currentScreen, navigateTo, openAddHolding } = useApp();

  const isMainTab = ['asset-management', 'annual-dividend', 'roadmap', 'watchlist'].includes(currentScreen);

  return (
    <>
      {/* Floating Action Button (FAB) positioned above the bottom nav */}
      <aside className="fixed right-4 bottom-20 z-30">
        <button
          type="button"
          onClick={openAddHolding}
          aria-label="保有銘柄を追加"
          className="fab-pulse w-13 h-13 rounded-full bg-[#10b981] text-[#003824] flex items-center justify-center shadow-[0_8px_24px_rgba(16,185,129,0.45)] hover:brightness-110 active:scale-95 transition-all cursor-pointer"
        >
          <span className="material-symbols-outlined text-[28px] font-bold">add</span>
        </button>
      </aside>

      {/* Fixed Bottom Navigation Bar */}
      <nav className="fixed bottom-0 inset-x-0 z-40 bg-[#0b1326]/95 backdrop-blur-xl border-t border-slate-800 pb-safe">
        <div className="grid grid-cols-4 items-center h-16 max-w-lg mx-auto px-2">
          {/* 1. 資産管理 */}
          <button
            type="button"
            onClick={() => navigateTo('asset-management')}
            className={`flex flex-col items-center justify-center gap-0.5 h-12 transition-colors active:scale-95 cursor-pointer ${
              currentScreen === 'asset-management'
                ? 'text-[#4edea3] font-bold'
                : 'text-[#bbcabf] hover:text-white'
            }`}
          >
            <span
              className="material-symbols-outlined text-[22px]"
              style={{
                fontVariationSettings: currentScreen === 'asset-management' ? "'FILL' 1" : "'FILL' 0"
              }}
            >
              account_balance_wallet
            </span>
            <span className="text-[10px] tracking-tight">資産管理</span>
          </button>

          {/* 2. 年間配当 / 分析 */}
          <button
            type="button"
            onClick={() => navigateTo('annual-dividend')}
            className={`flex flex-col items-center justify-center gap-0.5 h-12 transition-colors active:scale-95 cursor-pointer ${
              currentScreen === 'annual-dividend'
                ? 'text-[#4edea3] font-bold'
                : 'text-[#bbcabf] hover:text-white'
            }`}
          >
            <span
              className="material-symbols-outlined text-[22px]"
              style={{
                fontVariationSettings: currentScreen === 'annual-dividend' ? "'FILL' 1" : "'FILL' 0"
              }}
            >
              pie_chart
            </span>
            <span className="text-[10px] tracking-tight">年間配当</span>
          </button>

          {/* 3. ロードマップ */}
          <button
            type="button"
            onClick={() => navigateTo('roadmap')}
            className={`flex flex-col items-center justify-center gap-0.5 h-12 transition-colors active:scale-95 cursor-pointer ${
              currentScreen === 'roadmap'
                ? 'text-[#4edea3] font-bold'
                : 'text-[#bbcabf] hover:text-white'
            }`}
          >
            <span
              className="material-symbols-outlined text-[22px]"
              style={{
                fontVariationSettings: currentScreen === 'roadmap' ? "'FILL' 1" : "'FILL' 0"
              }}
            >
              flag
            </span>
            <span className="text-[10px] tracking-tight">ロードマップ</span>
          </button>

          {/* 4. Watchlist */}
          <button
            type="button"
            onClick={() => navigateTo('watchlist')}
            className={`flex flex-col items-center justify-center gap-0.5 h-12 transition-colors active:scale-95 cursor-pointer ${
              currentScreen === 'watchlist'
                ? 'text-[#4edea3] font-bold'
                : 'text-[#bbcabf] hover:text-white'
            }`}
          >
            <span
              className="material-symbols-outlined text-[22px]"
              style={{
                fontVariationSettings: currentScreen === 'watchlist' ? "'FILL' 1" : "'FILL' 0"
              }}
            >
              bookmark
            </span>
            <span className="text-[10px] tracking-tight">Watchlist</span>
          </button>
        </div>
      </nav>
    </>
  );
};
