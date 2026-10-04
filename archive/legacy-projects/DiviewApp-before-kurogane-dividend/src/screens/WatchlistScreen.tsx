import React, { useState, useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { WatchlistStock } from '../types';
import { ExDividendMatrixModal } from '../components/modals/ExDividendMatrixModal';
import { MonthlyForecastModal } from '../components/modals/MonthlyForecastModal';
import { WatchlistSortModal, WatchlistSortCriteria, WatchlistSortDirection } from '../components/modals/WatchlistSortModal';
import { AnimatedNumber } from '../components/AnimatedNumber';
import { motion, AnimatePresence } from 'motion/react';

export const WatchlistScreen: React.FC = () => {
  const { holdings, watchlist, updateTargetYield, toggleWatchlistStock, navigateTo, openAddHolding, showToast } = useApp();

  const [filter, setFilter] = useState<'all' | 'jp' | 'us' | 'ready'>(() => {
    return (localStorage.getItem('kurogane_watch_filter') as any) || 'all';
  });
  const [isSortOpen, setIsSortOpen] = useState(false);
  const [sortCriteria, setSortCriteria] = useState<WatchlistSortCriteria>(() => {
    return (localStorage.getItem('kurogane_watch_sort') as WatchlistSortCriteria) || 'ach';
  });
  const [sortDirection, setSortDirection] = useState<WatchlistSortDirection>(() => {
    return (localStorage.getItem('kurogane_watch_direction') as WatchlistSortDirection) || 'desc';
  });
  const [globalAlert, setGlobalAlert] = useState(true);

  useEffect(() => {
    localStorage.setItem('kurogane_watch_filter', filter);
  }, [filter]);

  useEffect(() => {
    localStorage.setItem('kurogane_watch_sort', sortCriteria);
  }, [sortCriteria]);

  useEffect(() => {
    localStorage.setItem('kurogane_watch_direction', sortDirection);
  }, [sortDirection]);

  // Dynamic Watchlist Calculations
  const averageYield = watchlist.length > 0
    ? (watchlist.reduce((sum, s) => sum + (s.dps / s.price) * 100, 0) / watchlist.length).toFixed(2)
    : '0.00';

  const potentialDiv = watchlist.reduce((sum, s) => {
    const shares = s.defaultShares || 100;
    const rate = s.currency === '$' ? 154 : 1;
    return sum + (s.dps * shares * rate);
  }, 0);

  const heldCount = watchlist.filter(s => holdings.some(h => h.code === s.code)).length;

  const totalTargetInvestment = watchlist.reduce((sum, s) => {
    const shares = s.defaultShares || 100;
    const rate = s.currency === '$' ? 154 : 1;
    const targetPrice = s.dps / (s.targetYield / 100);
    return sum + (targetPrice * shares * rate);
  }, 0);

  const targetAverageYield = totalTargetInvestment > 0
    ? ((potentialDiv / totalTargetInvestment) * 100).toFixed(2)
    : '0.00';

  // Modals
  const [isMatrixOpen, setIsMatrixOpen] = useState(false);
  const [isForecastOpen, setIsForecastOpen] = useState(false);

  const getSortLabel = () => {
    const dir = sortDirection === 'desc' ? '降順' : '昇順';
    switch (sortCriteria) {
      case 'ach': return `達成率 (${dir})`;
      case 'yield': return `利回り (${dir})`;
      case 'price': return `株価 (${dir})`;
      case 'code': return `コード (${dir})`;
    }
  };

  const filteredList = watchlist.filter(stock => {
    const yieldVal = (stock.dps / stock.price) * 100;
    const isReady = yieldVal >= stock.targetYield;

    if (filter === 'all') return true;
    if (filter === 'jp') return stock.category === 'jp';
    if (filter === 'us') return stock.category === 'us';
    if (filter === 'ready') return isReady;
    return true;
  });

  // Dynamic Sort Processor
  filteredList.sort((a, b) => {
    let valA = 0;
    let valB = 0;

    const yieldA = (a.dps / a.price) * 100;
    const yieldB = (b.dps / b.price) * 100;

    if (sortCriteria === 'ach') {
      valA = yieldA / a.targetYield;
      valB = yieldB / b.targetYield;
    } else if (sortCriteria === 'yield') {
      valA = yieldA;
      valB = yieldB;
    } else if (sortCriteria === 'price') {
      valA = a.price;
      valB = b.price;
    } else if (sortCriteria === 'code') {
      return sortDirection === 'desc' ? b.code.localeCompare(a.code) : a.code.localeCompare(b.code);
    }

    return sortDirection === 'desc' ? valB - valA : valA - valB;
  });

  const readyCount = watchlist.filter(s => (s.dps / s.price) * 100 >= s.targetYield).length;

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-3.5">
      {/* Hero KPI Overview Card */}
      <section className="relative w-full rounded-2xl bg-gradient-to-br from-[#131b2e] via-[#171f33] to-[#0d1629] p-3.5 border border-slate-800/80 shadow-xl overflow-hidden">
        <div className="flex items-center justify-between gap-2 mb-2.5">
          <div className="inline-flex items-center gap-2 px-2.5 py-0.5 rounded-full bg-[#171f33] border border-slate-700/60 shadow-inner">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#4edea3] opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-[#4edea3]" />
            </span>
            <span className="text-[11px] text-[#4edea3] font-bold tracking-wide">
              買場到達 {readyCount}銘柄
            </span>
            <span className="text-[11px] text-[#bbcabf]">/ {watchlist.length}銘柄監視中</span>
          </div>

          <div className="flex items-center gap-2 bg-[#0b1326]/60 px-2 py-0.5 rounded-full border border-slate-800">
            <span className="text-[10px] text-[#bbcabf] font-medium">買場アラート</span>
            <button
              type="button"
              onClick={() => {
                setGlobalAlert(prev => !prev);
                showToast(globalAlert ? '買場アラート通知を一時停止しました' : '買場アラート通知をONにしました', 'notifications');
              }}
              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full p-0.5 transition-colors ${
                globalAlert ? 'bg-[#10b981]' : 'bg-slate-700'
              }`}
            >
              <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                  globalAlert ? 'translate-x-4' : 'translate-x-0'
                }`}
              />
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2.5">
          <div className="flex flex-col p-2.5 rounded-xl bg-[#0b1326]/75 border border-slate-800/80">
            <span className="text-[10px] text-[#bbcabf] font-medium mb-0.5">平均現在利回り</span>
            <div className="flex items-baseline gap-1">
              <span className="font-mono text-2xl text-[#4edea3] font-bold">{averageYield}</span>
              <span className="text-xs text-[#4edea3] font-bold">%</span>
            </div>
            <span className="text-[9px] text-[#4edea3] flex items-center gap-0.5 mt-0.5 font-semibold">
              <span className="material-symbols-outlined text-[12px]">trending_up</span>
              東証平均 +1.8%
            </span>
          </div>

          <div className="flex flex-col p-2.5 rounded-xl bg-[#0b1326]/75 border border-slate-800/80">
            <span className="text-[10px] text-[#bbcabf] font-medium mb-0.5">配当ポテンシャル</span>
            <div className="flex items-baseline gap-0.5">
              <span className="text-xs text-white font-bold">¥</span>
              <span className="font-mono text-2xl text-white font-bold">{Math.round(potentialDiv).toLocaleString()}</span>
              <span className="text-[10px] text-[#bbcabf]">/年</span>
            </div>
            <span className="text-[9px] text-[#bbcabf] mt-0.5">各1単元保有試算 ({heldCount}銘柄保有済)</span>
          </div>
        </div>

        <div className="mt-2.5 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[#bbcabf]">
          <div className="flex items-center gap-1 text-[10px]">
            <span className="material-symbols-outlined text-[#4edea3] text-[14px]">info</span>
            <span>株価下落時に利回り条件を自動再計算</span>
          </div>
          <span className="font-mono text-[10px] text-indigo-300">本日 15:30 更新</span>
        </div>
      </section>

      {/* Filter and Sort Bar */}
      <section className="flex flex-col gap-2">
        <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar pb-0.5">
          <button
            type="button"
            onClick={() => setFilter('all')}
            className={`px-3 py-1 rounded-full text-[11px] shrink-0 active:scale-95 transition-all ${
              filter === 'all'
                ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                : 'bg-[#131b2e] text-white border border-slate-800'
            }`}
          >
            すべて <span className="px-1.5 py-0.2 rounded-full bg-slate-800 text-[9px]">{watchlist.length}</span>
          </button>
          <button
            type="button"
            onClick={() => setFilter('jp')}
            className={`px-3 py-1 rounded-full text-[11px] shrink-0 active:scale-95 transition-all ${
              filter === 'jp'
                ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                : 'bg-[#131b2e] text-white border border-slate-800'
            }`}
          >
            日本株 <span className="px-1.5 py-0.2 rounded-full bg-slate-800 text-[9px]">3</span>
          </button>
          <button
            type="button"
            onClick={() => setFilter('us')}
            className={`px-3 py-1 rounded-full text-[11px] shrink-0 active:scale-95 transition-all ${
              filter === 'us'
                ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                : 'bg-[#131b2e] text-white border border-slate-800'
            }`}
          >
            米国株 <span className="px-1.5 py-0.2 rounded-full bg-slate-800 text-[9px]">1</span>
          </button>
          <button
            type="button"
            onClick={() => setFilter('ready')}
            className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] shrink-0 whitespace-nowrap active:scale-95 transition-all ${
              filter === 'ready'
                ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                : 'bg-[#131b2e] text-white border border-slate-800'
            }`}
          >
            <span className="font-bold inline-flex items-center gap-0.5">
              <span className="material-symbols-outlined text-[13px]">bolt</span>買場到達
            </span>
            <span className="px-1.5 py-0.2 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[9px] font-bold">
              {readyCount}
            </span>
          </button>
        </div>

        <div className="flex items-center justify-between pt-0.5">
          <button
            type="button"
            onClick={() => setIsSortOpen(true)}
            className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#131b2e] border border-slate-800 text-xs text-[#bbcabf] hover:text-white transition-all active:scale-95 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[15px] text-[#4edea3]">swap_vert</span>
            <span>{getSortLabel()}</span>
            <span className="material-symbols-outlined text-[13px]">expand_more</span>
          </button>

          <button
            type="button"
            onClick={openAddHolding}
            className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-[#131b2e] border border-[#4edea3]/30 text-[#4edea3] text-xs font-bold hover:bg-[#10b981]/10 transition-all active:scale-95 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[15px]">add</span>
            <span>銘柄追加</span>
          </button>
        </div>
      </section>

      {/* Watchlist Stock Cards */}
      <section className="flex flex-col gap-2.5">
        {filteredList.map(stock => {
          const currentYield = Number(((stock.dps / stock.price) * 100).toFixed(2));
          const achievementRate = Math.min(100, Math.round((currentYield / stock.targetYield) * 100));
          const isReached = currentYield >= stock.targetYield;
          const isUSD = stock.currency === '$';
          const prefix = isUSD ? '$' : '¥';
          const targetPrice = Math.floor(stock.dps / (stock.targetYield / 100));
          const priceDiff = targetPrice - stock.price;

          return (
            <motion.div
              key={stock.code}
              layout
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.96 }}
              whileHover={{ y: -2 }}
              transition={{ duration: 0.2 }}
              className={`relative flex flex-col rounded-2xl bg-[#131b2e] p-3.5 shadow-md transition-colors ${
                isReached
                  ? 'border border-[#4edea3]/60 glow-celebrate'
                  : 'border border-slate-800 hover:border-slate-700'
              }`}
            >
              {/* Reach Badge */}
              {isReached && (
                <div className="absolute -top-2.5 left-3 px-2 py-0.5 rounded-full bg-[#10b981] text-[#003824] text-[9px] font-bold flex items-center gap-0.5 shadow-md z-10">
                  <span className="material-symbols-outlined text-[11px]">verified</span>
                  <span>絶好の買場到達 (利回り {currentYield}%)</span>
                </div>
              )}

              <div className={`flex items-center justify-between gap-1.5 mb-1.5 ${isReached ? 'pt-1' : ''}`}>
                <div className="flex items-center gap-2 min-w-0">
                  <span className="px-2 py-0.5 rounded font-mono text-[10px] bg-slate-800 text-white font-bold">
                    {stock.code}
                  </span>
                  <h2 className="text-sm font-bold text-white truncate">{stock.name}</h2>
                </div>
                <div className="flex items-baseline gap-1.5 shrink-0">
                  <span className="font-mono text-base text-white font-bold">
                    {prefix}{stock.price.toLocaleString()}
                  </span>
                  <span className={`font-mono text-[10px] font-semibold flex items-center ${stock.isPositive ? 'text-[#4edea3]' : 'text-[#ffb4ab]'}`}>
                    <span className="material-symbols-outlined text-[12px]">
                      {stock.isPositive ? 'arrow_drop_up' : 'arrow_drop_down'}
                    </span>
                    {stock.dailyChange}
                  </span>
                </div>
              </div>

              {/* Yield and Stepper Sub-box */}
              <div className="rounded-xl bg-[#0b1326]/85 border border-slate-800/80 p-2.5 flex flex-col gap-1.5 mb-2.5">
                <div className="flex items-center justify-between gap-1">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className="text-[10px] text-[#bbcabf]">現在利回り</span>
                    <span className={`font-mono text-sm font-bold ${isReached ? 'text-[#4edea3]' : 'text-white'}`}>
                      {currentYield.toFixed(2)}%
                    </span>
                    <span className="material-symbols-outlined text-[14px] text-[#bbcabf]">
                      {isReached ? 'check_circle' : 'arrow_forward'}
                    </span>
                  </div>

                  <div className="flex items-center gap-1 bg-[#171f33] border border-slate-700/80 rounded-full px-1.5 py-0.5">
                    <span className="text-[10px] text-[#bbcabf]">目標:</span>
                    <button
                      type="button"
                      onClick={() => updateTargetYield(stock.code, -0.05)}
                      className="w-5 h-5 flex items-center justify-center rounded-full bg-slate-800 text-white hover:text-[#4edea3] active:scale-90"
                    >
                      <span className="material-symbols-outlined text-[13px]">remove</span>
                    </button>
                    <span className="w-12 text-center font-mono text-xs font-bold text-[#4edea3]">
                      {stock.targetYield.toFixed(2)}%
                    </span>
                    <button
                      type="button"
                      onClick={() => updateTargetYield(stock.code, 0.05)}
                      className="w-5 h-5 flex items-center justify-center rounded-full bg-slate-800 text-white hover:text-[#4edea3] active:scale-90"
                    >
                      <span className="material-symbols-outlined text-[13px]">add</span>
                    </button>
                  </div>
                </div>

                <div className="w-full flex flex-col gap-1 pt-1 border-t border-slate-800/80">
                  <div className="flex items-center justify-between text-[10px]">
                    <span className={`flex items-center gap-1 font-semibold ${isReached ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                      <span className="font-mono font-bold">{achievementRate}%</span>
                      {isReached ? '達成済！購入好機' : '達成'}
                    </span>
                    <span className="font-mono text-[#bbcabf]">
                      {isReached
                        ? `目標超過 +${(currentYield - stock.targetYield).toFixed(2)}%`
                        : `目標まで あと +${(stock.targetYield - currentYield).toFixed(2)}%`}
                    </span>
                  </div>

                  <div className="w-full h-1.5 rounded-full bg-slate-800 overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${
                        isReached
                          ? 'bg-gradient-to-r from-[#10b981] to-[#4edea3]'
                          : 'bg-gradient-to-r from-teal-500 to-[#10b981]'
                      }`}
                      style={{ width: `${achievementRate}%` }}
                    />
                  </div>

                  <div className="flex items-center justify-between text-[10px] text-[#bbcabf] pt-0.5">
                    <span className="flex items-center gap-1">
                      <span className="material-symbols-outlined text-[12px] text-[#4edea3]">monetization_on</span>
                      <span>
                        買場目安: <strong className="text-white font-mono">{prefix}{targetPrice.toLocaleString()}</strong>
                        {priceDiff < 0
                          ? ` (あと ${prefix}${Math.abs(priceDiff).toLocaleString()} 下落)`
                          : ` (達成済)`}
                      </span>
                    </span>
                    <span className="font-mono text-[9px] text-[#4edea3] bg-[#10b981]/15 px-1.5 py-0.2 rounded font-bold">
                      1株配当 {prefix}{stock.dps}
                    </span>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={openAddHolding}
                  className="flex-1 h-8 rounded-lg bg-[#10b981] hover:brightness-110 text-[#003824] text-[11px] font-bold flex items-center justify-center gap-1 shadow active:scale-95 transition-all cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[15px]">add_circle</span>
                  <span>保有株に登録</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    toggleWatchlistStock(stock.code);
                    showToast(
                      stock.alertEnabled
                        ? `${stock.name}の買場通知をOFFにしました`
                        : `${stock.name}の買場到達通知をONにしました`,
                      stock.alertEnabled ? 'notifications_off' : 'notifications_active'
                    );
                  }}
                  className={`w-8 h-8 flex items-center justify-center rounded-lg border active:scale-90 transition-all shrink-0 cursor-pointer ${
                    stock.alertEnabled
                      ? 'bg-[#171f33] border-[#4edea3]/40 text-[#4edea3]'
                      : 'bg-[#171f33] border-slate-700/80 text-[#bbcabf] hover:text-white'
                  }`}
                >
                  <span
                    className="material-symbols-outlined text-[16px]"
                    style={{ fontVariationSettings: stock.alertEnabled ? "'FILL' 1" : "'FILL' 0" }}
                  >
                    {stock.alertEnabled ? 'notifications_active' : 'notifications'}
                  </span>
                </button>

                <button
                  type="button"
                  onClick={() => navigateTo('stock-detail', stock.code)}
                  className="h-8 px-2.5 flex items-center justify-center rounded-lg bg-[#171f33] hover:bg-slate-700 border border-slate-700/80 text-white text-[11px] active:scale-95 transition-all shrink-0 cursor-pointer"
                >
                  <span>詳細</span>
                  <span className="material-symbols-outlined text-[13px]">chevron_right</span>
                </button>
              </div>
            </motion.div>
          );
        })}
      </section>

      {/* Bottom Simulation Panel */}
      <section className="rounded-2xl bg-gradient-to-b from-[#131b2e] to-[#0f172a] border border-slate-800 p-3.5 shadow-lg space-y-2">
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-md bg-[#10b981]/15 border border-[#10b981]/30 flex items-center justify-center">
            <span className="material-symbols-outlined text-[#4edea3] text-[16px]">calculate</span>
          </div>
          <h3 className="text-sm text-white font-bold">監視銘柄の一括シミュレーション</h3>
        </div>
        <p className="text-[11px] text-[#bbcabf] leading-relaxed">
          各1単元を現在の目標買場で揃えた場合、初期投資概算 <strong className="text-white font-mono">¥{Math.round(totalTargetInvestment).toLocaleString()}</strong> で年間 <strong className="text-[#4edea3] font-mono">+¥{Math.round(potentialDiv).toLocaleString()}</strong>（平均利回り {targetAverageYield}%）の不労所得基盤が完成します。
        </p>

        <div className="grid grid-cols-2 gap-2 pt-1">
          <button
            type="button"
            onClick={() => setIsMatrixOpen(true)}
            className="h-8 rounded-lg bg-[#171f33] hover:bg-slate-700 border border-[#4edea3]/40 text-white text-[11px] font-semibold flex items-center justify-center gap-1.5 active:scale-95 transition-all shadow-sm group cursor-pointer"
          >
            <span className="material-symbols-outlined text-[15px] text-[#4edea3] group-hover:scale-110 transition-transform">
              calendar_month
            </span>
            <span className="font-bold">権利落ち月マトリクス</span>
          </button>

          <button
            type="button"
            onClick={() => setIsForecastOpen(true)}
            className="h-8 rounded-lg bg-[#171f33] hover:bg-slate-700 border border-slate-700/80 text-white text-[11px] font-semibold flex items-center justify-center gap-1.5 active:scale-95 transition-all group cursor-pointer"
          >
            <span className="material-symbols-outlined text-[15px] text-indigo-300 group-hover:scale-110 transition-transform">
              bar_chart
            </span>
            <span>受取月別グラフ試算</span>
          </button>
        </div>
      </section>

      {/* Bottom Full Width CTA */}
      <button
        type="button"
        onClick={openAddHolding}
        className="w-full h-11 rounded-xl bg-gradient-to-r from-[#10b981] via-[#4edea3] to-[#10b981] hover:brightness-110 text-[#003824] text-xs font-bold flex items-center justify-center gap-1.5 shadow-lg shadow-emerald-500/20 active:scale-95 transition-all cursor-pointer"
      >
        <span className="material-symbols-outlined text-[18px]">playlist_add</span>
        <span>新しい銘柄をウォッチリストに追加</span>
      </button>

      {/* Simulation Modals */}
      <ExDividendMatrixModal
        isOpen={isMatrixOpen}
        onClose={() => setIsMatrixOpen(false)}
      />
      <MonthlyForecastModal
        isOpen={isForecastOpen}
        onClose={() => setIsForecastOpen(false)}
      />
      <WatchlistSortModal
        isOpen={isSortOpen}
        onClose={() => setIsSortOpen(false)}
        currentCriteria={sortCriteria}
        currentDirection={sortDirection}
        onApply={(criteria, direction) => {
          setSortCriteria(criteria);
          setSortDirection(direction);
          showToast('並び替え条件を適用しました', 'sort');
        }}
      />
    </div>
  );
};
