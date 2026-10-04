import React, { useState, useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { CategoryType, SortCriteria, SortDirection, StateSimMode } from '../types';
import { monthlyDividends2024, roadmapMasterSteps } from '../data/mockData';
import { SortModal } from '../components/modals/SortModal';
import { AnimatedNumber } from '../components/AnimatedNumber';
import { motion, AnimatePresence } from 'motion/react';

export const AssetManagementScreen: React.FC = () => {
  const {
    holdings,
    isPrivacyActive,
    togglePrivacy,
    navigateTo,
    openQuickEdit,
    openAddHolding,
    stateSim,
    setStateSim,
    showToast
  } = useApp();

  const [selectedMonth, setSelectedMonth] = useState<number | null>(() => {
    const saved = localStorage.getItem('kurogane_asset_month');
    return saved ? parseInt(saved, 10) : null;
  });
  const [categoryFilter, setCategoryFilter] = useState<CategoryType>(() => {
    return (localStorage.getItem('kurogane_asset_category') as CategoryType) || 'all';
  });
  const [isSortOpen, setIsSortOpen] = useState(false);
  const [sortCriteria, setSortCriteria] = useState<SortCriteria>(() => {
    return (localStorage.getItem('kurogane_asset_sort') as SortCriteria) || 'val';
  });
  const [sortDirection, setSortDirection] = useState<SortDirection>(() => {
    return (localStorage.getItem('kurogane_asset_direction') as SortDirection) || 'desc';
  });
  const [isRetrying, setIsRetrying] = useState(false);

  useEffect(() => {
    if (selectedMonth !== null) {
      localStorage.setItem('kurogane_asset_month', selectedMonth.toString());
    } else {
      localStorage.removeItem('kurogane_asset_month');
    }
  }, [selectedMonth]);

  useEffect(() => {
    localStorage.setItem('kurogane_asset_category', categoryFilter);
  }, [categoryFilter]);

  useEffect(() => {
    localStorage.setItem('kurogane_asset_sort', sortCriteria);
  }, [sortCriteria]);

  useEffect(() => {
    localStorage.setItem('kurogane_asset_direction', sortDirection);
  }, [sortDirection]);

  // Calculate totals
  const totalAssets = holdings.reduce((sum, h) => sum + h.shares * h.price * (h.currency === '$' ? 154 : 1), 0);
  const totalCost = holdings.reduce((sum, h) => sum + h.shares * h.acqPrice * (h.currency === '$' ? 154 : 1), 0);
  const totalGain = totalAssets - totalCost;
  const gainPct = totalCost > 0 ? ((totalGain / totalCost) * 100).toFixed(1) : '0';
  const totalAnnualDiv = holdings.reduce((sum, h) => sum + h.shares * h.dps * (h.currency === '$' ? 154 : 1), 0);
  const netAnnualDiv = Math.round(totalAnnualDiv * 0.79685);
  const avgYield = totalAssets > 0 ? ((totalAnnualDiv / totalAssets) * 100).toFixed(2) : '0.00';

  // Calculate dynamic target based on currently active (in_progress) roadmap step
  const totalAnnualExpectedDividendForRoadmap = holdings.reduce((sum, h) => {
    const rate = h.currency === '$' ? 150 : 1;
    return sum + Math.round(h.shares * h.dps * rate);
  }, 0);
  const monthlyIncome = Math.round(totalAnnualExpectedDividendForRoadmap / 12);

  const activeRoadmapStep = roadmapMasterSteps.find((step, idx) => {
    const prevStep = idx > 0 ? roadmapMasterSteps[idx - 1] : null;
    const isCompleted = monthlyIncome >= step.targetMonthly;
    const isPrevCompleted = prevStep ? (monthlyIncome >= prevStep.targetMonthly) : true;
    return !isCompleted && isPrevCompleted;
  }) || roadmapMasterSteps[roadmapMasterSteps.length - 1]; // Fallback to STEP 10 if all completed

  const currentAnnualTarget = activeRoadmapStep ? activeRoadmapStep.targetAnnual : 300000;
  const currentMonthlyTarget = activeRoadmapStep ? activeRoadmapStep.targetMonthly : 25000;

  const parsedYield = parseFloat(avgYield);
  let yieldBadgeText = 'インデックス・極小';
  let yieldBadgeColor = 'text-slate-400';
  let yieldBadgeIcon = 'toll';

  if (parsedYield >= 4.0) {
    yieldBadgeText = '超高配当水準 (4%~)';
    yieldBadgeColor = 'text-[#c084fc]'; // Purple
    yieldBadgeIcon = 'bolt';
  } else if (parsedYield >= 3.0) {
    yieldBadgeText = '高配当水準 (3%~)';
    yieldBadgeColor = 'text-[#4edea3]'; // Emerald
    yieldBadgeIcon = 'verified';
  } else if (parsedYield >= 2.0) {
    yieldBadgeText = '中配当水準 (2%~)';
    yieldBadgeColor = 'text-[#60a5fa]'; // Blue
    yieldBadgeIcon = 'trending_up';
  } else if (parsedYield >= 0.5) {
    yieldBadgeText = '低配当・成長重視';
    yieldBadgeColor = 'text-[#fbbf24]'; // Amber
    yieldBadgeIcon = 'show_chart';
  }

  // Dynamically calculate 12-month expected dividends from holdings
  const dynamicMonthlyDividends = Array.from({ length: 12 }, (_, i) => {
    const month = i + 1;
    const amount = holdings.reduce((sum, h) => {
      if (h.dividendMonths && h.dividendMonths.includes(month)) {
        const rate = h.currency === '$' ? 154 : 1;
        const totalStockAnnualDiv = h.shares * h.dps * rate;
        return sum + Math.round(totalStockAnnualDiv / h.dividendMonths.length);
      }
      return sum;
    }, 0);
    const label = `${(amount / 1000).toFixed(1)}k`;
    return { month, amount, label };
  });

  const maxMonthAmt = Math.max(...dynamicMonthlyDividends.map(m => m.amount), 10000);
  const displayedMonthTotal = selectedMonth !== null
    ? dynamicMonthlyDividends[selectedMonth - 1]?.amount || 0
    : Math.round(totalAnnualDiv);

  // Filter and sort holdings
  let displayHoldings = [...holdings];

  // Month filter
  if (selectedMonth !== null) {
    displayHoldings = displayHoldings.filter(h => h.dividendMonths.includes(selectedMonth));
  }

  // Category filter
  if (categoryFilter !== 'all') {
    displayHoldings = displayHoldings.filter(h => h.category === categoryFilter);
  }

  // Sort
  displayHoldings.sort((a, b) => {
    let valA = 0;
    let valB = 0;
    if (sortCriteria === 'val') {
      valA = a.shares * a.price * (a.currency === '$' ? 154 : 1);
      valB = b.shares * b.price * (b.currency === '$' ? 154 : 1);
    } else if (sortCriteria === 'gain') {
      const costA = a.shares * a.acqPrice;
      const gainA = costA > 0 ? (a.shares * a.price - costA) / costA : 0;
      const costB = b.shares * b.acqPrice;
      const gainB = costB > 0 ? (b.shares * b.price - costB) / costB : 0;
      valA = gainA;
      valB = gainB;
    } else if (sortCriteria === 'div') {
      valA = a.yieldRate;
      valB = b.yieldRate;
    } else if (sortCriteria === 'div_amt') {
      valA = a.shares * a.dps;
      valB = b.shares * b.dps;
    } else if (sortCriteria === 'code') {
      return sortDirection === 'desc' ? b.code.localeCompare(a.code) : a.code.localeCompare(b.code);
    }
    return sortDirection === 'desc' ? valB - valA : valA - valB;
  });

  const handleMonthClick = (m: number) => {
    if (selectedMonth === m) {
      setSelectedMonth(null);
    } else {
      setSelectedMonth(m);
    }
  };

  const handleRetry = () => {
    setIsRetrying(true);
    setTimeout(() => {
      setIsRetrying(false);
      setStateSim('normal');
      showToast('再接続に成功しました！最新株価を反映しました');
    }, 1200);
  };

  const getSortLabel = () => {
    const dir = sortDirection === 'desc' ? '降順' : '昇順';
    switch (sortCriteria) {
      case 'val': return `評価額 (${dir})`;
      case 'gain': return `損益率 (${dir})`;
      case 'div': return `利回り (${dir})`;
      case 'div_amt': return `配当額 (${dir})`;
      case 'code': return `コード (${dir})`;
    }
  };

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-3.5">
      {/* State Simulator Strip */}
      <section className="flex flex-col gap-1 p-2 rounded-xl bg-[#171f33]/90 backdrop-blur-md shadow-md border border-slate-800">
        <div className="flex items-center justify-between text-[11px] px-1">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-[#4edea3] animate-pulse" />
            <span className="text-[#4edea3] uppercase font-bold tracking-wider">State Simulator</span>
          </div>
          <span className="text-[#bbcabf] font-medium">画面状態の動的切替</span>
        </div>
        <div className="flex gap-1.5 overflow-x-auto no-scrollbar py-0.5">
          {[
            { id: 'normal', label: '① 通常・一覧', icon: 'stacked_bar_chart' },
            { id: 'offline', label: '② オフライン', icon: 'wifi_off' },
            { id: 'loading', label: '③ 読込中', icon: 'sync' },
            { id: 'zero', label: '④ 検索ゼロ', icon: 'search_off' },
            { id: 'empty', label: '⑤ 初期未登録', icon: 'add_circle' }
          ].map(tab => (
            <button
              key={tab.id}
              type="button"
              onClick={() => {
                setStateSim(tab.id as StateSimMode);
                if (tab.id === 'loading') {
                  setTimeout(() => setStateSim('normal'), 2000);
                }
              }}
              className={`shrink-0 px-3 py-1.5 rounded-full text-xs font-semibold flex items-center gap-1 transition-all active:scale-95 ${
                stateSim === tab.id
                  ? 'bg-[#10b981] text-[#003824] font-bold shadow-sm'
                  : 'bg-[#0b1326] text-[#bbcabf] hover:text-white'
              }`}
            >
              <span className={`material-symbols-outlined text-[15px] ${stateSim === tab.id && tab.id === 'loading' ? 'animate-spin' : ''}`}>
                {tab.icon}
              </span>
              <span>{tab.label}</span>
            </button>
          ))}
        </div>
      </section>

      {/* ================= STATE: LOADING ================= */}
      {stateSim === 'loading' && (
        <div className="flex flex-col gap-3.5 animate-pulse">
          <div className="p-3 rounded-xl bg-[#171f33] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-[#4edea3] animate-ping" />
              <span className="text-xs text-white font-semibold">最新株価・為替レートを取得中...</span>
            </div>
            <span className="material-symbols-outlined text-[#4edea3] text-[20px] animate-spin">autorenew</span>
          </div>

          <div className="p-4 rounded-2xl bg-[#131b2e] flex flex-col gap-3">
            <div className="flex justify-between">
              <div className="h-4 w-28 rounded bg-slate-800" />
              <div className="h-4 w-16 rounded-full bg-slate-800" />
            </div>
            <div className="h-9 w-52 rounded bg-slate-800 my-1" />
            <div className="h-5 w-36 rounded-full bg-slate-800" />
            <div className="grid grid-cols-2 gap-2 pt-2">
              <div className="p-3 rounded-xl bg-[#171f33] h-16" />
              <div className="p-3 rounded-xl bg-[#171f33] h-16" />
            </div>
          </div>
        </div>
      )}

      {/* ================= STATE: OFFLINE ================= */}
      {stateSim === 'offline' && (
        <div className="flex flex-col gap-3.5">
          <div className="p-4 rounded-xl bg-amber-500/15 border border-amber-500/30 text-white flex items-start gap-3 shadow-md">
            <span className="material-symbols-outlined text-amber-400 text-[24px] shrink-0 mt-0.5">cloud_off</span>
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <span className="text-sm text-amber-400 font-bold">通信エラー / オフライン表示中</span>
                <span className="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 text-[10px] font-bold">OFFLINE</span>
              </div>
              <p className="text-xs text-[#bbcabf] leading-relaxed">
                最新株価の取得に失敗しました。前回キャッシュデータ（本日 15:00時点）を表示しています。
              </p>
            </div>
          </div>

          <section className="p-4 rounded-2xl bg-[#131b2e] border border-slate-800 shadow-xl flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs text-[#bbcabf]">
              <span>総資産評価額 (キャッシュデータ)</span>
              <span className="px-2 py-0.5 rounded-md bg-slate-800 font-mono text-[11px] flex items-center gap-1">
                <span className="material-symbols-outlined text-[13px]">schedule</span>
                最終同期: 15:00
              </span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold text-slate-200">¥{Math.round(totalAssets).toLocaleString()}</span>
              <span className="text-xs text-[#bbcabf]">JPY (キャッシュ)</span>
            </div>
          </section>

          <section className="p-4 rounded-2xl bg-[#171f33] border border-slate-800 flex flex-col gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-red-500/20 text-red-400 flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[22px]">signal_cellular_connected_no_internet_0_bar</span>
              </div>
              <div>
                <h3 className="font-bold text-sm text-white">株価データの更新に失敗しました</h3>
                <span className="text-[11px] text-[#bbcabf]">ネットワーク応答なし (Timeout 15s)</span>
              </div>
            </div>
            <p className="text-xs text-[#bbcabf] leading-relaxed">
              手動登録された保有株数・取得単価は端末内暗号化ストレージに安全に保管されています。電波状況をご確認のうえ再度お試しください。
            </p>
            <button
              type="button"
              disabled={isRetrying}
              onClick={handleRetry}
              className="w-full h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs flex items-center justify-center gap-2 shadow-lg active:scale-98 transition-all"
            >
              <span className={`material-symbols-outlined text-[18px] ${isRetrying ? 'animate-spin' : ''}`}>
                refresh
              </span>
              <span>{isRetrying ? 'サーバーと通信中...' : '再接続して最新株価を取得'}</span>
            </button>
          </section>
        </div>
      )}

      {/* ================= STATE: ZERO SEARCH ================= */}
      {stateSim === 'zero' && (
        <div className="flex flex-col gap-3.5">
          <div className="p-3 rounded-xl bg-[#171f33] border border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2 min-w-0">
              <span className="material-symbols-outlined text-[#4edea3] text-[20px]">search</span>
              <span className="text-xs text-[#bbcabf]">検索条件:</span>
              <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] font-bold text-xs">"NVDA"</span>
            </div>
            <button
              type="button"
              onClick={() => setStateSim('normal')}
              className="flex items-center gap-0.5 text-xs text-[#bbcabf] hover:text-white p-1"
            >
              <span className="material-symbols-outlined text-[15px]">close</span>
              <span>解除</span>
            </button>
          </div>

          <section className="p-6 rounded-2xl bg-[#131b2e] border border-slate-800 flex flex-col items-center text-center gap-3 shadow-md">
            <div className="relative w-16 h-16 rounded-full bg-[#171f33] flex items-center justify-center">
              <span className="material-symbols-outlined text-[32px] text-[#bbcabf]">search_off</span>
              <span className="absolute bottom-0 right-0 w-5 h-5 rounded-full bg-red-900 text-red-200 flex items-center justify-center font-bold text-[11px]">
                0
              </span>
            </div>
            <div className="flex flex-col gap-1 max-w-xs">
              <h3 className="font-bold text-sm text-white">該当する銘柄が見つかりません</h3>
              <p className="text-xs text-[#bbcabf] leading-relaxed">
                検索条件 <span className="text-[#4edea3] font-semibold">"NVDA"</span> に一致する保有銘柄はありません。
              </p>
            </div>
            <div className="flex flex-col w-full gap-2 pt-2">
              <button
                type="button"
                onClick={() => setStateSim('normal')}
                className="w-full h-11 rounded-xl bg-[#10b981] text-[#003824] font-bold text-xs flex items-center justify-center gap-2 shadow active:scale-98"
              >
                <span className="material-symbols-outlined text-[18px]">filter_alt_off</span>
                <span>フィルター・検索をリセット</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setStateSim('normal');
                  openAddHolding();
                }}
                className="w-full h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white font-semibold text-xs flex items-center justify-center gap-1.5 active:scale-98"
              >
                <span className="material-symbols-outlined text-[16px] text-[#4edea3]">add</span>
                <span>「NVDA」を新規保有銘柄に追加</span>
              </button>
            </div>
          </section>
        </div>
      )}

      {/* ================= STATE: EMPTY ================= */}
      {stateSim === 'empty' && (
        <div className="flex flex-col gap-3.5">
          <section className="p-4 rounded-2xl bg-[#131b2e] border border-slate-800 shadow-sm flex flex-col gap-2 opacity-90">
            <div className="flex items-center justify-between text-xs text-[#bbcabf]">
              <span>総資産評価額</span>
              <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#bbcabf] text-[10px]">未登録</span>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-mono text-2xl font-bold text-[#bbcabf]">¥0</span>
              <span className="text-xs text-[#bbcabf]">JPY</span>
            </div>
            <div className="text-[11px] text-[#bbcabf]">
              通算損益: ±¥0 (0.00%) • 年間予想配当金: ¥0/年
            </div>
          </section>

          <section className="p-6 rounded-2xl bg-[#131b2e] border border-slate-800 shadow-xl flex flex-col items-center text-center gap-3">
            <div className="relative w-20 h-20 rounded-full bg-[#171f33] flex items-center justify-center shadow-inner">
              <span className="material-symbols-outlined text-[36px] text-[#4edea3]">account_balance_wallet</span>
              <span className="absolute -top-1 -right-1 px-1.5 py-0.5 rounded-full bg-[#10b981] text-[#003824] text-[9px] font-bold">
                1分で登録
              </span>
            </div>
            <div className="flex flex-col gap-1 max-w-xs">
              <h3 className="font-bold text-base text-white">まだ保有銘柄が登録されていません</h3>
              <p className="text-xs text-[#bbcabf] leading-relaxed">
                証券口座連携は不要です。右下の「＋」ボタンまたはCSVインポートから1分ですぐに保有株式を登録できます。
              </p>
            </div>
            <div className="flex flex-col w-full gap-2 pt-2">
              <button
                type="button"
                onClick={() => {
                  setStateSim('normal');
                  openAddHolding();
                }}
                className="w-full h-11 rounded-xl bg-[#10b981] text-[#003824] font-bold text-xs flex items-center justify-center gap-2 shadow active:scale-98"
              >
                <span className="material-symbols-outlined text-[20px]">add</span>
                <span>銘柄を手動で追加 (デモデータを復元)</span>
              </button>
              <button
                type="button"
                onClick={() => navigateTo('csv-import')}
                className="w-full h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white font-semibold text-xs flex items-center justify-center gap-2 active:scale-98"
              >
                <span className="material-symbols-outlined text-[16px]">upload_file</span>
                <span>CSVファイルから一括インポート</span>
              </button>
            </div>
          </section>
        </div>
      )}

      {/* ================= STATE: NORMAL (FULL DASHBOARD) ================= */}
      {stateSim === 'normal' && (
        <div className="flex flex-col gap-3.5">
          {/* Hero Total Asset Card */}
          <section className="relative overflow-hidden rounded-2xl bg-[#131b2e] p-4 shadow-xl border border-slate-800/80">
            <div className="absolute -top-12 -left-12 w-48 h-48 rounded-full bg-[#10b981]/15 blur-3xl pointer-events-none" />
            <div className="relative z-10 flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  <span className="text-xs font-semibold text-[#bbcabf] uppercase tracking-wider">
                    総資産評価額
                  </span>
                  <button
                    type="button"
                    onClick={togglePrivacy}
                    aria-label="プライバシー表示切替"
                    className="text-[#bbcabf] hover:text-white p-1 transition-colors"
                  >
                    <span className="material-symbols-outlined text-[18px]">
                      {isPrivacyActive ? 'visibility_off' : 'visibility'}
                    </span>
                  </button>
                </div>
                <div className={`flex items-center gap-1 px-2.5 py-1 rounded-full ${
                  totalGain >= 0 ? 'bg-[#10b981]/20 text-[#4edea3]' : 'bg-red-500/20 text-red-400'
                }`}>
                  <span className="material-symbols-outlined text-[16px]">
                    {totalGain >= 0 ? 'trending_up' : 'trending_down'}
                  </span>
                  <span className="text-xs font-bold font-mono">
                    {isPrivacyActive ? (totalGain >= 0 ? '+¥ ••••••' : '-¥ ••••••') : (
                      <>
                        {totalGain >= 0 ? '+' : ''}<AnimatedNumber value={totalGain} prefix="¥" /> ({totalGain >= 0 ? '+' : ''}{gainPct}%)
                      </>
                    )}
                  </span>
                </div>
              </div>

              <div className="flex flex-col gap-0.5 mt-0.5">
                <span className="font-mono text-2xl sm:text-3xl text-white font-bold tracking-tight">
                  <AnimatedNumber
                    value={totalAssets}
                    prefix="¥"
                    isMasked={isPrivacyActive}
                    maskText="¥ ••••••••"
                  />
                </span>
                <div className="flex items-center gap-1.5 text-xs text-[#bbcabf]">
                  <span>投資元本:</span>
                  <span className="font-mono font-medium text-slate-300">
                    <AnimatedNumber
                      value={totalCost}
                      prefix="¥"
                      isMasked={isPrivacyActive}
                      maskText="¥ ••••••••"
                    />
                  </span>
                </div>
              </div>

              {/* Sub-grid: Dividend KPI */}
              <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-800">
                <div className="p-2.5 rounded-xl bg-[#171f33]/80 flex flex-col">
                  <span className="text-[11px] text-[#bbcabf]">年間予想受取配当</span>
                  <div className="flex items-baseline gap-1 mt-0.5">
                    <span className="font-mono text-base font-bold text-[#4edea3]">
                      <AnimatedNumber
                        value={Math.round(totalAnnualDiv)}
                        prefix="¥"
                        isMasked={isPrivacyActive}
                        maskText="¥ ••••••"
                      />
                    </span>
                    <span className="text-[10px] text-[#bbcabf]">/年</span>
                  </div>
                  <span className="text-[10px] text-[#bbcabf] mt-0.5">
                    税引後概算: <AnimatedNumber value={netAnnualDiv} prefix="¥" isMasked={isPrivacyActive} maskText="¥ ••••••" />
                  </span>
                </div>

                <div className="p-2.5 rounded-xl bg-[#171f33]/80 flex flex-col">
                  <span className="text-[11px] text-[#bbcabf]">平均配当利回り</span>
                  <div className="flex items-baseline gap-1 mt-0.5">
                    <span className="font-mono text-base font-bold text-white">
                      {isPrivacyActive ? '•••' : avgYield}
                    </span>
                    <span className="text-[10px] text-[#bbcabf]">%</span>
                  </div>
                  <span className={`text-[10px] ${yieldBadgeColor} font-semibold mt-0.5 flex items-center gap-0.5`}>
                    <span className="material-symbols-outlined text-[13px]">{yieldBadgeIcon}</span>
                    {yieldBadgeText}
                  </span>
                </div>
              </div>
            </div>
          </section>

          {/* Monthly Dividend Chart Card with Interactive Pivot Filtering */}
          <section className="relative overflow-hidden rounded-2xl bg-[#131b2e] p-3.5 shadow-xl border border-slate-800/80 flex flex-col gap-2.5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <div className="w-7 h-7 rounded-lg bg-[#171f33] flex items-center justify-center text-[#4edea3]">
                  <span className="material-symbols-outlined text-[18px]">calendar_month</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <h3 className="text-sm font-bold text-white">月別推移グラフ</h3>
                  <span className="px-1.5 py-0.2 rounded bg-slate-800 text-[10px] text-[#bbcabf] font-mono">
                    2024
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {selectedMonth !== null && (
                  <button
                    type="button"
                    onClick={() => setSelectedMonth(null)}
                    className="text-[10px] text-[#4edea3] hover:underline flex items-center gap-0.5"
                  >
                    <span>年間合計へ戻す</span>
                  </button>
                )}
                <div className="text-right">
                  <span className="text-[10px] text-[#bbcabf] mr-1">
                    {selectedMonth !== null ? `${selectedMonth}月予定:` : '年間合計:'}
                  </span>
                  <span className="font-mono text-sm font-bold text-[#4edea3]">
                    ¥{displayedMonthTotal.toLocaleString()}
                  </span>
                </div>
              </div>
            </div>

            {/* 12 Mini Bars */}
            <div className="relative w-full pt-3 pb-1">
              <div className="absolute inset-x-0 top-6 h-px bg-slate-800 pointer-events-none" />
              <div className="flex items-end justify-between gap-1 h-24 px-0.5">
                {dynamicMonthlyDividends.map(col => {
                  const isSelected = selectedMonth === col.month;
                  const maxHeightPx = 58;
                  const height = Math.max(6, Math.round((col.amount / maxMonthAmt) * maxHeightPx));

                  return (
                    <button
                      key={col.month}
                      type="button"
                      onClick={() => handleMonthClick(col.month)}
                      className={`group relative flex flex-col items-center flex-1 h-full justify-end cursor-pointer transition-all active:scale-95 ${
                        isSelected ? 'scale-105 opacity-100' : selectedMonth !== null ? 'opacity-40' : 'opacity-85 hover:opacity-100'
                      }`}
                    >
                      <div className={`text-[7.5px] font-mono mb-0.5 font-bold ${isSelected ? 'text-[#4edea3]' : 'text-sky-400'}`}>
                        {col.label}
                      </div>
                      <div
                        className={`w-full max-w-[14px] flex flex-col justify-end rounded-t-sm overflow-hidden bg-slate-800 transition-all ${
                          isSelected ? 'ring-2 ring-[#4edea3] ring-offset-1 ring-offset-[#131b2e] shadow-lg' : ''
                        }`}
                      >
                        <div
                          className={`w-full ${
                            col.month === 6 || col.month === 12
                              ? 'bg-gradient-to-t from-[#10b981] to-[#4edea3] shadow-[0_0_8px_rgba(78,222,163,0.6)]'
                              : 'bg-sky-400'
                          }`}
                          style={{ height: `${height}px` }}
                        />
                      </div>
                      <span className={`mt-1 font-mono text-[9px] ${isSelected ? 'text-[#4edea3] font-bold' : 'text-[#bbcabf]'}`}>
                        {col.month}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Legend & Summary */}
            <div className="flex items-center justify-between pt-1 border-t border-slate-800/80 text-[10px]">
              <div className="flex items-center gap-2.5">
                <span className="flex items-center gap-1 text-slate-300">
                  <span className="w-2 h-2 rounded-full bg-[#4edea3] inline-block shadow-[0_0_4px_#4edea3]" />
                  日本株
                </span>
                <span className="flex items-center gap-1 text-slate-300">
                  <span className="w-2 h-2 rounded-full bg-sky-400 inline-block shadow-[0_0_4px_#38bdf8]" />
                  米国株
                </span>
                <span className="flex items-center gap-1 text-slate-300">
                  <span className="w-2 h-2 rounded-full bg-indigo-400 inline-block shadow-[0_0_4px_#818cf8]" />
                  投信
                </span>
              </div>
              <span className="text-[#4edea3] bg-[#10b981]/15 px-2 py-0.5 rounded-full border border-[#4edea3]/30 font-medium">
                {selectedMonth !== null ? `${selectedMonth}月タップ中` : `月平均: 約¥${Math.round(totalAnnualDiv / 12).toLocaleString()}`}
              </span>
            </div>
          </section>

          {/* Goal Progress Card */}
          <section className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 shadow-sm flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[#4edea3] text-[18px]">flag</span>
                <span className="text-xs font-bold text-white">年間配当目標: ¥{currentAnnualTarget.toLocaleString()}</span>
              </div>
              <span className="font-mono text-xs font-bold text-[#4edea3]">
                {((totalAnnualDiv / currentAnnualTarget) * 100).toFixed(1)}%
              </span>
            </div>
            <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
              <div
                className="h-full bg-[#10b981] rounded-full transition-all duration-500"
                style={{ width: `${Math.min(100, (totalAnnualDiv / currentAnnualTarget) * 100)}%` }}
              />
            </div>
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf]">
              <span>現在: {isPrivacyActive ? '¥ ••••••' : `¥${Math.round(totalAnnualDiv).toLocaleString()}`}</span>
              <span>
                {currentAnnualTarget - totalAnnualDiv > 0 ? (
                  isPrivacyActive ? `あと ¥ •••••• で月${(currentMonthlyTarget / 10000).toFixed(1).replace('.0', '')}万円達成` : `あと ¥${Math.round(currentAnnualTarget - totalAnnualDiv).toLocaleString()} で目標クリア`
                ) : (
                  '目標達成済！不労所得生活達成✨'
                )}
              </span>
            </div>
          </section>

          {/* Filter Chips & Sort Button Row */}
          <section className="flex flex-col gap-2">
            <div className="flex items-center justify-between px-1">
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[#4edea3] text-[18px]">inventory_2</span>
                <h3 className="font-bold text-sm text-white">
                  {selectedMonth !== null ? `${selectedMonth}月の配当入金ポートフォリオ` : '保有銘柄一覧'}
                </h3>
                <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#4edea3] font-mono text-[10px] font-bold">
                  {displayHoldings.length}件
                </span>
              </div>

              <div className="flex items-center gap-2">
                {selectedMonth !== null && (
                  <button
                    type="button"
                    onClick={() => setSelectedMonth(null)}
                    className="text-[#4edea3] hover:underline text-[11px] flex items-center"
                  >
                    <span>全件表示</span>
                    <span className="material-symbols-outlined text-[13px]">chevron_right</span>
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => setIsSortOpen(true)}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-xs font-medium border border-slate-700/60 active:scale-95 transition-all cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[15px] text-[#4edea3]">swap_vert</span>
                  <span>{getSortLabel()}</span>
                </button>
              </div>
            </div>

            {/* Horizontal Filter Chips */}
            <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
              {[
                { id: 'all', label: `すべて (${holdings.length})` },
                { id: 'japan', label: `日本株 (${holdings.filter(h => h.category === 'japan').length})` },
                { id: 'us', label: `米国株 (${holdings.filter(h => h.category === 'us').length})` },
                { id: 'fund', label: `投信 (${holdings.filter(h => h.category === 'fund').length})` }
              ].map(chip => (
                <button
                  key={chip.id}
                  type="button"
                  onClick={() => setCategoryFilter(chip.id as CategoryType)}
                  className={`px-3 py-1 rounded-full text-xs font-semibold shrink-0 transition-all cursor-pointer ${
                    categoryFilter === chip.id
                      ? 'bg-[#10b981] text-[#003824] font-bold shadow-sm'
                      : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
                  }`}
                >
                  {chip.label}
                </button>
              ))}
            </div>
          </section>

          {/* Holdings List Cards */}
          <section className="flex flex-col gap-2.5">
            {displayHoldings.length === 0 ? (
              <div className="p-6 rounded-2xl bg-[#131b2e] border border-dashed border-slate-800 text-center flex flex-col items-center gap-2">
                <span className="material-symbols-outlined text-[32px] text-[#bbcabf]">event_busy</span>
                <h4 className="font-bold text-sm text-white">該当する保有銘柄はありません</h4>
                <p className="text-xs text-[#bbcabf] max-w-xs">
                  {selectedMonth !== null
                    ? `${selectedMonth}月に入金が予定されている銘柄はありません。`
                    : '指定の条件に一致する銘柄がありません。'}
                </p>
                {selectedMonth !== null && (
                  <button
                    type="button"
                    onClick={() => setSelectedMonth(null)}
                    className="mt-1 px-3 py-1.5 rounded-lg bg-[#171f33] text-[#4edea3] text-xs font-semibold"
                  >
                    全銘柄一覧を表示
                  </button>
                )}
              </div>
            ) : (
              displayHoldings.map(holding => {
                const isUSD = holding.currency === '$';
                const prefix = isUSD ? '$' : '¥';
                const curVal = holding.shares * holding.price * (isUSD ? 154 : 1);
                const curCost = holding.shares * holding.acqPrice * (isUSD ? 154 : 1);
                const diff = curVal - curCost;
                const diffPct = curCost > 0 ? ((diff / curCost) * 100).toFixed(1) : '0';
                const annualDiv = Math.round(holding.shares * holding.dps * (isUSD ? 154 : 1));

                return (
                  <motion.article
                    key={holding.id}
                    layout
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    whileHover={{ y: -2, scale: 1.006 }}
                    whileTap={{ scale: 0.985 }}
                    transition={{ duration: 0.2 }}
                    onClick={() => navigateTo('stock-detail', holding.code)}
                    className={`p-3.5 rounded-2xl bg-[#131b2e] border hover:bg-[#171f33] transition-colors cursor-pointer relative overflow-hidden ${
                      selectedMonth !== null ? 'border-[#4edea3]/50 shadow-md shadow-[#4edea3]/5' : 'border-slate-800/80 shadow-md'
                    }`}
                  >
                    {/* Month Match Ribbon */}
                    {selectedMonth !== null && (
                      <div className="absolute top-0 right-0 bg-[#10b981]/20 border-b border-l border-[#4edea3]/40 px-2.5 py-0.5 rounded-bl-lg text-[10px] font-bold text-[#4edea3] flex items-center gap-1">
                        <span className="material-symbols-outlined text-[12px]">payments</span>
                        <span>{selectedMonth}月入金 ({holding.dividendType})</span>
                      </div>
                    )}

                    <div className="flex items-start justify-between">
                      <div className="flex items-center gap-2 min-w-0">
                        <span
                          className={`px-2 py-0.5 rounded font-mono text-[11px] font-bold ${
                            holding.category === 'us'
                              ? 'bg-indigo-950 text-indigo-300'
                              : holding.category === 'fund'
                              ? 'bg-rose-950 text-rose-300'
                              : 'bg-slate-800 text-white'
                          }`}
                        >
                          {holding.code}
                        </span>
                        <div className="truncate">
                          <h4 className="font-bold text-sm text-white truncate">{holding.name}</h4>
                          <span className="text-[10px] text-[#bbcabf]">{holding.sector}</span>
                        </div>
                        <button
                          type="button"
                          onClick={e => {
                            e.stopPropagation();
                            openQuickEdit(holding);
                          }}
                          className="w-6 h-6 rounded flex items-center justify-center text-[#bbcabf] hover:text-white hover:bg-slate-700/60 transition-colors"
                          title="保有情報を編集"
                        >
                          <span className="material-symbols-outlined text-[14px]">edit</span>
                        </button>
                      </div>

                      <div className="flex flex-col items-end shrink-0">
                        <span className="font-mono font-bold text-sm text-white">
                          ¥{Math.round(curVal).toLocaleString()}
                        </span>
                        <span
                          className={`text-[10px] font-bold font-mono ${
                            diff >= 0 ? 'text-[#4edea3]' : 'text-[#ffb4ab]'
                          }`}
                        >
                          {diff >= 0 ? `+¥${Math.round(diff).toLocaleString()} (+${diffPct}%)` : `-¥${Math.round(Math.abs(diff)).toLocaleString()} (${diffPct}%)`}
                        </span>
                      </div>
                    </div>

                    <div className="h-px bg-slate-800 my-2" />

                    <div className="grid grid-cols-3 gap-2 text-center text-xs">
                      <div className="flex flex-col items-start">
                        <span className="text-[10px] text-[#bbcabf]">保有数量</span>
                        <span className="font-mono text-white text-[11px] font-semibold mt-0.5">
                          {holding.shares.toLocaleString()}株 ({prefix}{holding.price.toLocaleString()})
                        </span>
                      </div>
                      <div className="flex flex-col items-center">
                        <span className="text-[10px] text-[#bbcabf]">配当利回り</span>
                        <span className="font-mono text-[#4edea3] text-[11px] font-bold mt-0.5">
                          {holding.yieldRate > 0 ? `${holding.yieldRate.toFixed(2)}%` : '-'}
                        </span>
                      </div>
                      <div className="flex flex-col items-end">
                        <span className="text-[10px] text-[#bbcabf]">予想配当金</span>
                        <span className="font-mono text-white text-[11px] font-bold mt-0.5">
                          {annualDiv > 0 ? `¥${annualDiv.toLocaleString()}/年` : '再投資型'}
                        </span>
                      </div>
                    </div>

                    {/* Month Highlight Timing Banner */}
                    {selectedMonth !== null && (
                      <div className="mt-2 p-2 rounded-lg bg-[#171f33] border border-[#4edea3]/30 flex items-center justify-between text-xs">
                        <div className="flex items-center gap-1.5 text-[11px] text-[#bbcabf]">
                          <span className="material-symbols-outlined text-[#4edea3] text-[15px]">event_available</span>
                          <span>入金予定: <strong className="text-white">{selectedMonth}月中旬予定</strong></span>
                        </div>
                        <span className="font-mono font-bold text-[#4edea3] text-xs">
                          ¥{Math.round(annualDiv / (holding.dividendMonths?.length || 2)).toLocaleString()}
                        </span>
                      </div>
                    )}

                    <div className="flex items-center justify-between pt-2 text-[10px] text-[#bbcabf]">
                      <span className="px-2 py-0.5 rounded bg-slate-800 text-indigo-300 font-medium">
                        {holding.accountType}
                      </span>
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[13px] text-[#4edea3]">event</span>
                        次回: {holding.dividendMonths?.[0] || 6}月中旬 (¥{Math.round(annualDiv / (holding.dividendMonths?.length || 2)).toLocaleString()})
                      </span>
                    </div>
                  </motion.article>
                );
              })
            )}
          </section>

          {/* Next Expected Dividend Strip (100% dynamic based on held assets) */}
          {(() => {
            const currentMonthNum = new Date().getMonth() + 1;
            const validHoldings = [...holdings].filter(h => h.dividendMonths && h.dividendMonths.length > 0 && h.shares > 0);
            let nextStock = validHoldings[0];
            let nextMonth = 12;
            let minDiff = 999;
            validHoldings.forEach(h => {
              h.dividendMonths.forEach(m => {
                let diff = m - currentMonthNum;
                if (diff < 0) diff += 12;
                if (diff < minDiff) {
                  minDiff = diff;
                  nextStock = h;
                  nextMonth = m;
                }
              });
            });

            const nextDivAmt = nextStock
              ? Math.round((nextStock.shares * nextStock.dps * (nextStock.currency === '$' ? 154 : 1)) / (nextStock.dividendMonths?.length || 2))
              : 0;

            return (
              <section className="p-3.5 rounded-2xl bg-[#171f33] border border-slate-800 shadow-sm flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-10 h-10 rounded-full bg-[#10b981]/20 flex items-center justify-center text-[#4edea3] shrink-0">
                    <span className="material-symbols-outlined text-[20px]">calendar_today</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-[#bbcabf]">直近の配当受取予定</span>
                    <p className="font-bold text-xs text-white">
                      {nextStock ? `${nextMonth}月中旬 ${nextStock.name}` : '保有銘柄なし'}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <span className="font-mono text-base font-bold text-[#4edea3]">
                    ¥{nextDivAmt.toLocaleString()}
                  </span>
                  <span className="block text-[10px] text-[#bbcabf]">
                    {nextStock?.dividendType || '配当金'} ({nextStock?.shares?.toLocaleString() || 0}株)
                  </span>
                </div>
              </section>
            );
          })()}
        </div>
      )}

      {/* Sort Modal Sheet */}
      <SortModal
        isOpen={isSortOpen}
        onClose={() => setIsSortOpen(false)}
        currentCriteria={sortCriteria}
        currentDirection={sortDirection}
        onApply={(criteria, direction) => {
          setSortCriteria(criteria);
          setSortDirection(direction);
        }}
      />
    </div>
  );
};
