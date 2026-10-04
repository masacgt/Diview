import React from 'react';
import { useApp } from '../context/AppContext';

export const Header: React.FC = () => {
  const { currentScreen, goBack, openSearch, openNotifications, navigateTo, refreshMarketPrices } = useApp();

  const isMainTab = ['asset-management', 'annual-dividend', 'roadmap', 'watchlist'].includes(currentScreen);

  const getScreenTitle = () => {
    switch (currentScreen) {
      case 'asset-management':
        return { title: '資産管理', sub: 'Asset Management' };
      case 'annual-dividend':
        return { title: '年間配当', sub: 'Annual Dividend' };
      case 'roadmap':
        return { title: 'Roadmap Quest', sub: '不労所得 RPG • ステージ攻略' };
      case 'watchlist':
        return { title: 'Watchlist', sub: '監視銘柄・目標利回り管理' };
      case 'stock-detail':
        return { title: '銘柄情報', sub: 'Stock Information' };
      case 'settings':
        return { title: '設定・データ管理', sub: 'Settings & Data' };
      case 'csv-import':
        return { title: 'CSVインポート', sub: 'Portfolio Import Wizard' };
      case 'contact-feedback':
        return { title: 'お問い合わせ・ご意見', sub: 'Contact & Feedback' };
      case 'terms-privacy':
        return { title: '利用規約・プライバシー', sub: 'Terms & Privacy' };
      case 'jetpack-compose':
        return { title: 'Jetpack Compose', sub: 'Kotlin • Material 3 Code Hub' };
      default:
        return { title: '資産管理', sub: 'Asset Management' };
    }
  };

  const { title, sub } = getScreenTitle();

  return (
    <header className="fixed top-0 inset-x-0 z-40 bg-[#0b1326]/90 backdrop-blur-xl border-b border-slate-800/80 pt-safe">
      <div className="h-16 px-4 flex items-center justify-between max-w-lg mx-auto w-full">
        {isMainTab ? (
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-9 h-9 rounded-xl bg-[#171f33] border border-slate-700/60 flex items-center justify-center text-[#4edea3] shrink-0 shadow-inner">
              {currentScreen === 'asset-management' && (
                <span className="material-symbols-outlined text-[22px]">trending_up</span>
              )}
              {currentScreen === 'annual-dividend' && (
                <span className="material-symbols-outlined text-[22px]">pie_chart</span>
              )}
              {currentScreen === 'roadmap' && (
                <span className="material-symbols-outlined text-[22px]">flag</span>
              )}
              {currentScreen === 'watchlist' && (
                <span className="material-symbols-outlined text-[22px]">bookmark</span>
              )}
            </div>
            <div className="flex flex-col truncate">
              <span className="font-bold text-base text-white tracking-tight leading-tight truncate">
                {title}
              </span>
              <span className="text-[10px] text-[#bbcabf] uppercase tracking-wider font-semibold truncate">
                {sub}
              </span>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-1.5 min-w-0">
            <button
              type="button"
              onClick={goBack}
              aria-label="戻る"
              className="w-10 h-10 -ml-1 rounded-full flex items-center justify-center text-[#dae2fd] hover:text-[#4edea3] hover:bg-[#171f33] active:scale-95 transition-all"
            >
              <span className="material-symbols-outlined text-[22px]">arrow_back</span>
            </button>
            <div className="flex flex-col truncate min-w-0">
              <h1 className="font-bold text-base text-white tracking-tight truncate">{title}</h1>
              <span className="text-[10px] text-[#bbcabf] truncate">{sub}</span>
            </div>
          </div>
        )}

        <div className="flex items-center gap-1 shrink-0">
          <button
            type="button"
            onClick={() => navigateTo(currentScreen === 'jetpack-compose' ? 'asset-management' : 'jetpack-compose')}
            aria-label="Jetpack Compose コード"
            title="Jetpack Compose (Kotlin) コードを表示"
            className={`px-2.5 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
              currentScreen === 'jetpack-compose'
                ? 'bg-[#10b981] text-black shadow-md shadow-[#10b981]/30'
                : 'bg-[#171f33] text-[#4edea3] hover:bg-[#222a3d] border border-[#10b981]/30'
            }`}
          >
            <span className="text-xs">🤖</span>
            <span className="text-[11px] font-mono">Compose</span>
          </button>

          <button
            type="button"
            onClick={() => refreshMarketPrices(true)}
            aria-label="最新相場更新"
            title="最新の株価・相場利回りを更新"
            className="w-9 h-9 flex items-center justify-center rounded-full text-[#bbcabf] hover:text-[#4edea3] hover:bg-[#171f33] active:scale-95 transition-all"
          >
            <span className="material-symbols-outlined text-[19px]">sync</span>
          </button>

          <button
            type="button"
            onClick={openSearch}
            aria-label="銘柄検索"
            className="w-10 h-10 flex items-center justify-center rounded-full text-[#bbcabf] hover:text-white hover:bg-[#171f33] active:scale-95 transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">search</span>
          </button>

          <button
            type="button"
            onClick={openNotifications}
            aria-label="通知"
            className="w-10 h-10 flex items-center justify-center rounded-full text-[#bbcabf] hover:text-white hover:bg-[#171f33] active:scale-95 transition-colors relative"
          >
            <span className="material-symbols-outlined text-[20px]">notifications</span>
            <span className="absolute top-2.5 right-2.5 w-2 h-2 rounded-full bg-[#4edea3] shadow-[0_0_8px_rgba(78,222,163,0.8)] animate-pulse" />
          </button>

          <button
            type="button"
            onClick={() => navigateTo('settings')}
            aria-label="設定"
            className={`w-10 h-10 flex items-center justify-center rounded-full active:scale-95 transition-colors ${
              currentScreen === 'settings'
                ? 'text-[#4edea3] bg-[#171f33]'
                : 'text-[#bbcabf] hover:text-white hover:bg-[#171f33]'
            }`}
          >
            <span className="material-symbols-outlined text-[20px]">settings</span>
          </button>
        </div>
      </div>
    </header>
  );
};
