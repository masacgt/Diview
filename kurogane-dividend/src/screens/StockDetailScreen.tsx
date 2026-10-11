import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import { getStockFinancialProfile } from '../data/mockData';

export const StockDetailScreen: React.FC = () => {
  const {
    selectedStockCode,
    holdings,
    openQuickEdit,
    showToast,
    toggleWatchlistStock,
    watchlist
  } = useApp();

  const currentHolding = holdings.find(h => h.code === selectedStockCode) || holdings[0];
  const watchItem = watchlist.find(w => w.code === selectedStockCode);

  // Status Tab selection: default to health as requested
  const [selectedStatusTab, setSelectedStatusTab] = useState<'health' | 'value' | 'growth' | 'risk'>('value');

  // Dynamic stock financial profile based on current holding and live price
  const profile = getStockFinancialProfile(currentHolding.code, currentHolding.price, currentHolding.dps);
  const liveYield = ((currentHolding.dps / currentHolding.price) * 100).toFixed(2);
  const liveYoc = currentHolding.acqPrice > 0 ? ((currentHolding.dps / currentHolding.acqPrice) * 100).toFixed(2) : liveYield;

  // Buy-more simulator state
  const baseShares = currentHolding ? currentHolding.shares : 100;
  const currentPrice = currentHolding ? currentHolding.price : 2600;
  const dps = currentHolding ? currentHolding.dps : 72.8;

  const [simAddedShares, setSimAddedShares] = useState(100);
  const [isAlertOn, setIsAlertOn] = useState(true);

  const isUSD = currentHolding.currency === '$';
  const prefix = isUSD ? '$' : '¥';
  const fxRate = isUSD ? 154 : 1;

  // Dynamic Portfolio Dependency & Concentration Calculations
  const totalPortfolioDiv = holdings.reduce((sum, h) => sum + Math.round(h.shares * h.dps * (h.currency === '$' ? 154 : 1)), 0);
  const currentStockAnnualDiv = Math.round(currentHolding.shares * currentHolding.dps * fxRate);
  const divDependency = totalPortfolioDiv > 0 ? (currentStockAnnualDiv / totalPortfolioDiv) * 100 : 0;
  const divDependencyStr = divDependency.toFixed(1);

  const totalPortfolioVal = holdings.reduce((sum, h) => sum + Math.round(h.shares * h.price * (h.currency === '$' ? 154 : 1)), 0);
  const currentStockVal = Math.round(currentHolding.shares * currentHolding.price * fxRate);
  const sectorHoldings = holdings.filter(h => h.sector === currentHolding.sector);
  const sectorVal = sectorHoldings.reduce((sum, h) => sum + Math.round(h.shares * h.price * (h.currency === '$' ? 154 : 1)), 0);
  const sectorDependency = totalPortfolioVal > 0 ? (sectorVal / totalPortfolioVal) * 100 : 0;
  const sectorDependencyStr = sectorDependency.toFixed(1);
  const cleanSectorName = currentHolding.sector.replace(/^.*•\s*/, '').replace(/^.*·\s*/, '');
  const sectorRiskText = sectorDependency > 30 ? '高集中' : sectorDependency > 20 ? 'やや集中' : '適正分散';

  const cutImpact = Math.round(currentStockAnnualDiv * 0.5);
  const cutImpactLevel = cutImpact >= 10000 ? '大' : cutImpact >= 3000 ? '中' : '小';

  const isHighRisk = divDependency > 10;
  const riskBadgeText = divDependency > 25 ? '● 高リスク（上限目安10%）' : divDependency > 10 ? '● やや高め（上限目安10%）' : '● 適正範囲（10%以下）';

  const totalSimShares = baseShares + simAddedShares;
  const additionalCost = simAddedShares * currentPrice;
  const addedAnnualDiv = Math.round(simAddedShares * dps * fxRate);
  const newTotalAnnualDiv = Math.round(totalSimShares * dps * fxRate);
  const newMonthlyDiv = Math.round(newTotalAnnualDiv / 12);
  const multiplier = baseShares > 0 ? (totalSimShares / baseShares).toFixed(1) : '1.0';

  const totalValuation = currentHolding.shares * currentHolding.price * fxRate;
  const totalCost = currentHolding.shares * currentHolding.acqPrice * fxRate;
  const gainLoss = totalValuation - totalCost;
  const gainLossPct = totalCost > 0 ? ((gainLoss / totalCost) * 100).toFixed(2) : '0.00';

  const isBookmarked = watchItem?.alertEnabled ?? false;

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-3.5">
      {/* 1. Hero Stock Header Card */}
      <section className="relative overflow-hidden rounded-2xl bg-[#131b2e] p-4 shadow-xl border border-slate-800 flex flex-col gap-3">
        <div className="absolute -right-12 -top-12 w-48 h-48 rounded-full bg-[#10b981]/10 blur-3xl pointer-events-none" />

        {/* Top: Name, Code, Badges */}
        <div className="flex items-start justify-between gap-2 relative z-10">
          <div className="flex flex-col gap-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-xl font-bold text-white tracking-tight truncate">{currentHolding.name}</h2>
              <span className="px-2 py-0.5 rounded bg-slate-800 text-[#bbcabf] font-mono text-xs font-bold">
                {currentHolding.code}
              </span>
            </div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="px-2 py-0.5 rounded bg-[#0b1326] border border-slate-800 text-[#bbcabf] text-[10px] font-medium">
                {currentHolding.market}
              </span>
              <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 border border-[#4edea3]/30 text-[#4edea3] text-[10px] font-semibold flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3]" />
                {currentHolding.accountType}
              </span>
            </div>
          </div>

          <button
            type="button"
            onClick={() => {
              toggleWatchlistStock(currentHolding.code);
              showToast(
                isBookmarked ? 'ウォッチリストから解除しました' : 'ウォッチリストに登録しました',
                isBookmarked ? 'bookmark_border' : 'bookmark'
              );
            }}
            className={`w-10 h-10 rounded-xl border flex items-center justify-center transition-all cursor-pointer ${
              isBookmarked
                ? 'bg-[#10b981]/20 border-[#4edea3] text-[#4edea3] shadow-[0_0_12px_rgba(78,222,163,0.35)]'
                : 'bg-[#171f33] border-slate-800 text-[#bbcabf] hover:text-white'
            }`}
          >
            <span
              className="material-symbols-outlined text-[20px]"
              style={{ fontVariationSettings: isBookmarked ? "'FILL' 1" : "'FILL' 0" }}
            >
              {isBookmarked ? 'bookmark' : 'bookmark_border'}
            </span>
          </button>
        </div>

        {/* Price & Daily Change */}
        <div className="flex items-baseline justify-between pt-1 pb-1.5 border-b border-slate-800">
          <div>
            <div className="flex items-center gap-1.5 mb-0.5">
              <span className="text-[10px] text-[#bbcabf]">現在株価</span>
              <span className="px-1.5 py-0.2 rounded bg-slate-800 text-[9px] text-[#4edea3] font-bold">
                リアルタイム
              </span>
            </div>
            <p className="font-mono text-2xl font-bold text-white leading-none">
              {prefix}{currentHolding.price.toLocaleString()}
              <span className="text-xs text-[#bbcabf] ml-1 font-normal">/株</span>
            </p>
          </div>
          <div className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#10b981]/20 border border-[#4edea3]/30 text-[#4edea3] font-bold text-xs">
            <span className="material-symbols-outlined text-[15px]">trending_up</span>
            <span>{currentHolding.dailyChange}</span>
          </div>
        </div>

        {/* 2x2 Metric Tiles */}
        <div className="grid grid-cols-2 gap-2">
          <div
            onClick={() => openQuickEdit(currentHolding)}
            className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 hover:border-[#4edea3]/40 cursor-pointer transition-colors"
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf]">
              <span>保有数量</span>
              <span className="material-symbols-outlined text-[13px] text-[#4edea3]">edit</span>
            </div>
            <div className="flex items-baseline justify-between mt-1">
              <span className="font-mono text-sm font-bold text-white">{currentHolding.shares.toLocaleString()}株</span>
              <span className="text-[10px] text-[#bbcabf]">単元保有</span>
            </div>
          </div>

          <div
            onClick={() => openQuickEdit(currentHolding)}
            className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 hover:border-[#4edea3]/40 cursor-pointer transition-colors"
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf]">
              <span>取得単価</span>
              <span className="material-symbols-outlined text-[13px] text-[#4edea3]">edit</span>
            </div>
            <div className="flex items-baseline justify-between mt-1">
              <span className="font-mono text-sm font-bold text-white">{prefix}{currentHolding.acqPrice.toLocaleString()}</span>
              <span className="text-[10px] text-[#4edea3] font-medium">含み益圏</span>
            </div>
          </div>

          <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800">
            <span className="text-[11px] text-[#bbcabf]">評価額</span>
            <div className="flex items-baseline justify-between mt-1">
              <span className="font-mono text-sm font-bold text-white">¥{Math.round(totalValuation).toLocaleString()}</span>
              <span className="text-[10px] text-[#bbcabf]">時価総額</span>
            </div>
          </div>

          <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 bg-[#10b981]/5">
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf]">
              <span>含み損益</span>
              <span className="material-symbols-outlined text-[13px] text-[#4edea3]">trending_up</span>
            </div>
            <div className="flex items-baseline justify-between mt-1">
              <span className="font-mono text-sm font-bold text-[#4edea3]">
                {gainLoss >= 0 ? `+¥${Math.round(gainLoss).toLocaleString()}` : `-¥${Math.round(Math.abs(gainLoss)).toLocaleString()}`}
              </span>
              <span className="text-[10px] text-[#4edea3] font-bold">
                (+{gainLossPct}%)
              </span>
            </div>
          </div>
        </div>

      </section>

      {/* 2. 年間配当シミュレーション */}
      <section className="rounded-2xl bg-[#131b2e] p-3.5 shadow-md border border-slate-800 flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">payments</span>
            <h3 className="font-bold text-sm text-white">年間配当シミュレーション</h3>
          </div>
          <span className="px-2 py-0.5 rounded-full bg-indigo-950 text-indigo-300 text-[10px] font-bold">
            年2回配当
          </span>
        </div>

        <div className="p-3 rounded-xl bg-[#0b1326] border border-slate-800 flex flex-col gap-0.5">
          <span className="text-[10px] text-[#bbcabf]">
            年間受取予定額 ({currentHolding.shares}株保有)
          </span>
          <div className="flex items-baseline gap-2">
            <span className="font-mono text-2xl font-bold text-[#4edea3]">
              ¥{Math.round(currentHolding.shares * currentHolding.dps).toLocaleString()}
            </span>
            <span className="text-xs text-[#bbcabf]">(1株あたり ¥{currentHolding.dps})</span>
          </div>
          <div className="text-[11px] text-[#4edea3] font-semibold flex items-center gap-1 mt-0.5">
            <span className="material-symbols-outlined text-[14px]">savings</span>
            月換算 約¥{Math.round((currentHolding.shares * currentHolding.dps) / 12).toLocaleString()} / 月
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="p-2.5 rounded-xl bg-[#171f33]">
            <span className="text-[10px] text-[#bbcabf]">配当利回り (現在)</span>
            <span className="font-mono text-sm font-bold text-white mt-0.5 block">{liveYield}%</span>
            <span className="text-[9px] text-[#bbcabf]">現在株価 {prefix}{currentHolding.price.toLocaleString()}換算</span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#171f33]">
            <span className="text-[10px] text-[#bbcabf]">取得ベース利回り (YOC)</span>
            <span className="font-mono text-sm font-bold text-[#4edea3] mt-0.5 block">{liveYoc}% YOC</span>
            <span className="text-[9px] text-[#bbcabf]">取得単価 {prefix}{currentHolding.acqPrice.toLocaleString()}換算</span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#171f33]">
            <span className="text-[10px] text-[#bbcabf]">次回配当予定</span>
            <span className="font-mono text-sm font-bold text-white mt-0.5 block">
              {currentHolding.dividendMonths && currentHolding.dividendMonths.length > 0
                ? `${currentHolding.dividendMonths[0]}月中旬`
                : '6月中旬'}
            </span>
            <span className="text-[9px] text-[#4edea3]">
              {currentHolding.dividendType || '中間配当'} {prefix}{Math.round(currentStockAnnualDiv / (currentHolding.dividendMonths?.length || 2)).toLocaleString()}
            </span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#171f33]">
            <span className="text-[10px] text-[#bbcabf]">権利確定月</span>
            <span className="font-mono text-sm font-bold text-white mt-0.5 block">
              {profile.recordMonths}
            </span>
            <span className="text-[9px] text-[#bbcabf]">{profile.payoutSchedule}</span>
          </div>
        </div>
      </section>

      {/* 3. 買い増しシミュレーター Card */}
      <section className="rounded-2xl bg-[#131b2e] p-3.5 shadow-lg border-2 border-[#4edea3]/30 flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">calculate</span>
            <h3 className="font-bold text-sm text-white">買い増しシミュレーター</h3>
          </div>
          <span className="px-2.5 py-0.5 rounded-full bg-[#10b981]/20 border border-[#4edea3]/30 text-[#4edea3] text-[10px] font-bold">
            +{simAddedShares}株 (合計{totalSimShares}株)
          </span>
        </div>

        {/* Current vs After Comparison */}
        <div className="p-2.5 rounded-xl bg-gradient-to-br from-[#171f33] to-[#0b1326] border border-[#4edea3]/30">
          <div className="flex items-center justify-between pb-1.5 mb-1.5 border-b border-slate-800">
            <span className="text-[11px] font-bold text-white flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-[#4edea3] animate-ping" />
              買い増しインパクト比較
            </span>
            <span className="px-2 py-0.2 rounded-full bg-[#10b981] text-[#003824] text-[10px] font-bold">
              配当 {multiplier}倍 UP
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 text-center">
            <div className="p-2 rounded-lg bg-[#0b1326] border border-slate-800">
              <span className="text-[10px] text-[#bbcabf]">現在保有 ({baseShares}株)</span>
              <div className="font-mono text-base font-bold text-white my-0.5">
                ¥{Math.round(baseShares * dps).toLocaleString()}/年
              </div>
              <span className="text-[10px] text-[#bbcabf]">月 ¥{Math.round((baseShares * dps) / 12).toLocaleString()} / 月</span>
            </div>

            <div className="p-2 rounded-lg bg-[#10b981]/10 border border-[#4edea3]/40">
              <span className="text-[10px] text-[#4edea3] font-bold">シミュレーション ({totalSimShares}株)</span>
              <div className="font-mono text-base font-bold text-[#4edea3] my-0.5">
                ¥{newTotalAnnualDiv.toLocaleString()}/年
              </div>
              <span className="text-[10px] text-[#4edea3] font-bold">月 約¥{newMonthlyDiv.toLocaleString()} / 月</span>
            </div>
          </div>
        </div>

        {/* Stepper Inputs and Presets */}
        <div className="flex flex-col gap-2.5">
          <div className="flex items-center justify-between text-xs">
            <span className="text-[#bbcabf] font-medium flex items-center gap-1">
              <span className="material-symbols-outlined text-[14px] text-accent">keyboard</span>
              追加買増株数を手動指定:
            </span>
            <span className="text-accent font-bold">単元 (100株単位)</span>
          </div>

          {/* Stepper Row matching Image 2 */}
          <div className="flex items-center gap-2">
            {/* Input Box with unit */}
            <div className="flex-1 flex items-center bg-[#0b1326] border border-slate-800 rounded-xl px-3 py-1.5 justify-between">
              <input
                type="number"
                min="0"
                max="5000"
                step="10"
                value={simAddedShares}
                onChange={e => {
                  const val = parseInt(e.target.value);
                  setSimAddedShares(isNaN(val) ? 0 : val);
                }}
                className="w-18 bg-transparent text-white font-mono font-bold text-center text-sm outline-none focus:text-accent"
              />
              <span className="text-xs text-[#bbcabf] font-bold select-none ml-1">株</span>
            </div>

            {/* Step buttons */}
            <button
              type="button"
              onClick={() => setSimAddedShares(prev => Math.max(0, prev - 10))}
              className="px-3 py-2 rounded-xl bg-[#171f33] border border-slate-800 text-white font-mono text-xs font-bold hover:bg-slate-800 cursor-pointer active:scale-95 transition-all"
            >
              -10
            </button>
            <button
              type="button"
              onClick={() => setSimAddedShares(prev => prev + 10)}
              className="px-3 py-2 rounded-xl bg-[#171f33] border border-slate-800 text-white font-mono text-xs font-bold hover:bg-slate-800 cursor-pointer active:scale-95 transition-all"
            >
              +10
            </button>
            <button
              type="button"
              onClick={() => setSimAddedShares(prev => prev + 100)}
              className="px-3 py-2 rounded-xl bg-[#171f33] border border-slate-800 text-white font-mono text-xs font-bold hover:bg-slate-800 cursor-pointer active:scale-95 transition-all"
            >
              +100
            </button>

            {/* Reset Button */}
            <button
              type="button"
              onClick={() => setSimAddedShares(100)}
              title="リセット (100株)"
              className="w-9 h-9 rounded-xl bg-[#171f33] border border-slate-800 text-[#bbcabf] hover:text-white flex items-center justify-center cursor-pointer active:scale-95 transition-all"
            >
              <span className="material-symbols-outlined text-[16px]">autorenew</span>
            </button>
          </div>

          {/* Quick presets row from Image 2 */}
          <div className="grid grid-cols-4 gap-1.5">
            {[
              { val: 0, label: '現状維持' },
              { val: 100, label: '+100株' },
              { val: 250, label: '+250株' },
              { val: 500, label: '+500株' }
            ].map(p => (
              <button
                key={p.val}
                type="button"
                onClick={() => setSimAddedShares(p.val)}
                className={`py-1.5 rounded-xl text-xs font-semibold active:scale-95 transition-all cursor-pointer ${
                  simAddedShares === p.val
                    ? 'bg-accent text-black font-extrabold shadow-accent'
                    : 'bg-[#171f33] text-white hover:bg-slate-800 border border-slate-800/80'
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>

          {/* Original slider underneath */}
          <div className="pt-1">
            <input
              type="range"
              min="0"
              max="600"
              step="10"
              value={simAddedShares}
              onChange={e => setSimAddedShares(parseInt(e.target.value) || 0)}
              className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer"
            />
            <div className="flex justify-between text-[8px] text-[#bbcabf] font-mono mt-1 px-0.5">
              <span>0株</span>
              <span>+250株</span>
              <span>+500株 (計{baseShares + 500}株)</span>
            </div>
          </div>
        </div>

        {/* Calculation Summary */}
        <div className="grid grid-cols-2 gap-2 p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 text-xs">
          <div>
            <span className="text-[10px] text-[#bbcabf]">追加必要資金</span>
            <span className="font-mono text-sm font-bold text-white block mt-0.5">
              ¥{additionalCost.toLocaleString()}
            </span>
          </div>
          <div>
            <span className="text-[10px] text-[#bbcabf]">シミュレーション年間受取</span>
            <span className="font-mono text-sm font-bold text-[#4edea3] block mt-0.5">
              ¥{newTotalAnnualDiv.toLocaleString()}
            </span>
          </div>
          <div className="pt-1">
            <span className="text-[10px] text-[#bbcabf]">年間配当増額分</span>
            <span className="font-mono text-xs font-bold text-[#4edea3] block mt-0.5">
              +{prefix}{addedAnnualDiv.toLocaleString()} / 年
            </span>
          </div>
          <div className="pt-1">
            <span className="text-[10px] text-[#bbcabf]">新月間不労所得</span>
            <span className="font-mono text-xs font-bold text-white block mt-0.5">
              約¥{newMonthlyDiv.toLocaleString()} / 月
            </span>
          </div>
        </div>
      </section>

      {/* 4. 1株配当金の推移 (4期) Bar Chart Card (Dynamically computed per stock) */}
      {(() => {
        const gRate = profile.growthRate > 0 ? profile.growthRate : 5.0;
        const p3 = Math.max(1, Math.round(dps / Math.pow(1 + gRate / 100, 3)));
        const p2 = Math.max(1, Math.round(dps / Math.pow(1 + gRate / 100, 2)));
        const p1 = Math.max(1, Math.round(dps / Math.pow(1 + gRate / 100, 1)));
        const p0 = dps;
        const fourYrGrowth = p3 > 0 ? (((p0 - p3) / p3) * 100).toFixed(1) : '0.0';
        const maxDps = Math.max(p0, 1);

        const histBars = [
          { yr: '22.3期', dpsVal: `${prefix}${p3}`, h: `${Math.round((p3 / maxDps) * 85)}%`, bg: 'bg-indigo-400/60' },
          { yr: '23.3期', dpsVal: `${prefix}${p2}`, h: `${Math.round((p2 / maxDps) * 85)}%`, bg: 'bg-indigo-400/80' },
          { yr: '24.3期', dpsVal: `${prefix}${p1}`, h: `${Math.round((p1 / maxDps) * 85)}%`, bg: 'bg-indigo-400' },
          { yr: '25.3予', dpsVal: `${prefix}${p0}`, h: '95%', bg: 'bg-gradient-to-t from-[#10b981] to-[#4edea3]' }
        ];

        return (
          <section className="rounded-2xl bg-[#131b2e] p-3.5 shadow-md border border-slate-800 flex flex-col gap-2.5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[#4edea3] text-[20px]">bar_chart</span>
                <h3 className="font-bold text-sm text-white">1株配当金の推移 (4期)</h3>
              </div>
              <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold">
                {profile.consecutiveYears > 0 ? `${profile.consecutiveYears}期連続増配` : '安定配当'}
              </span>
            </div>

            <div className="h-32 bg-[#0b1326] rounded-xl p-3 border border-slate-800 flex items-end justify-between gap-3">
              {histBars.map(col => (
                <div key={col.yr} className="flex-1 flex flex-col items-center h-full justify-end">
                  <span className={`text-[9px] font-mono font-bold mb-1 ${col.yr === '25.3予' ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                    {col.dpsVal}
                  </span>
                  <div className={`w-full max-w-[28px] rounded-t-lg ${col.bg}`} style={{ height: col.h }} />
                  <span className={`text-[9px] font-mono mt-1 ${col.yr === '25.3予' ? 'text-[#4edea3] font-bold' : 'text-[#bbcabf]'}`}>
                    {col.yr}
                  </span>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between text-[10px] text-[#bbcabf] px-1">
              <span>4期増配率: <strong className="text-[#4edea3] font-bold">+{fourYrGrowth}%</strong></span>
              <span>配当性向: <strong className="text-white font-bold">約{profile.payoutRatio}%</strong></span>
            </div>
          </section>
        );
      })()}

      {/* 5. 銘柄総合分析ステータス (2x2 Grid + Expandable Detail) */}
      <section className="rounded-2xl bg-[#131b2e] p-3.5 shadow-md border border-slate-800 flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">insights</span>
            <h3 className="font-bold text-sm text-white">銘柄総合分析ステータス</h3>
          </div>
          <span className="text-[10px] text-[#bbcabf]">タップで切替</span>
        </div>

        {/* 2x2 Grid */}
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => setSelectedStatusTab('health')}
            className={`p-3 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'health'
                ? 'bg-[#171f33] border-2 border-[#10b981] shadow-[0_0_12px_rgba(16,185,129,0.25)]'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>① 健全性</span>
              <span className="material-symbols-outlined text-[16px] text-[#4edea3]">verified_user</span>
            </div>
            <div className="font-bold text-sm text-[#4edea3]">
              {profile.healthGrade} <span className="text-xs text-white font-medium">{profile.payoutRatio}%</span>
            </div>
            <div className="flex items-center justify-between mt-1">
              <span className="text-[10px] text-[#4edea3] font-medium">{profile.healthEvaluation}</span>
              <span className="material-symbols-outlined text-[14px] text-[#bbcabf]">expand_more</span>
            </div>
          </button>

          <button
            type="button"
            onClick={() => setSelectedStatusTab('value')}
            className={`p-3 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'value'
                ? 'bg-[#171f33] border-2 border-[#10b981] shadow-[0_0_12px_rgba(16,185,129,0.25)]'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>② 買場シグナル</span>
              <span className="material-symbols-outlined text-[16px] text-[#4edea3]">track_changes</span>
            </div>
            <div className="font-bold text-sm text-[#4edea3]">
              {watchItem && Number(liveYield) >= watchItem.targetYield ? '買場到達' : '検討圏'} <span className="text-xs text-white font-medium">{liveYield}%</span>
            </div>
            <div className="flex items-center justify-between mt-1">
              <span className="text-[10px] text-[#4edea3] font-medium">{profile.buySignalZone?.split('（')[0] || profile.valuationStatus.split('・')[0]}</span>
              <span className="material-symbols-outlined text-[14px] text-[#bbcabf]">expand_more</span>
            </div>
          </button>

          <button
            type="button"
            onClick={() => setSelectedStatusTab('growth')}
            className={`p-3 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'growth'
                ? 'bg-[#171f33] border-2 border-[#10b981] shadow-[0_0_12px_rgba(16,185,129,0.25)]'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>③ 増配耐性</span>
              <span className="material-symbols-outlined text-[16px] text-white">military_tech</span>
            </div>
            <div className="font-bold text-sm text-white">
              {profile.consecutiveYears > 0 ? `${profile.consecutiveYears}期連続` : '安定配当'} <span className="text-xs text-[#4edea3] font-bold">+{profile.growthRate}%</span>
            </div>
            <div className="flex items-center justify-between mt-1">
              <span className="text-[10px] text-[#4edea3] font-medium">非減配{profile.noCutYears}年</span>
              <span className="material-symbols-outlined text-[14px] text-[#bbcabf]">expand_more</span>
            </div>
          </button>

          <button
            type="button"
            onClick={() => setSelectedStatusTab('risk')}
            className={`p-3 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'risk'
                ? 'bg-[#171f33] border-2 border-[#f59e0b] shadow-[0_0_14px_rgba(245,158,11,0.35)]'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>④ 集中リスク</span>
              <span className="material-symbols-outlined text-[16px] text-[#f59e0b]">donut_large</span>
            </div>
            <div className="font-bold text-sm text-[#f59e0b]">
              {isHighRisk ? 'やや高め' : '適正'} <span className="text-xs text-white font-medium">{divDependencyStr}%</span>
            </div>
            <div className="flex items-center justify-between mt-1">
              <span className="text-[10px] text-[#f59e0b] font-medium">上限目安10%</span>
              <span className="material-symbols-outlined text-[14px] text-[#f59e0b]">expand_more</span>
            </div>
          </button>
        </div>

        {/* Dynamic Detail Panel: VALUE TAB (② 買場シグナル詳細分析 - Exact image.png reproduction) */}
        {selectedStatusTab === 'value' && (
          <div className="flex flex-col gap-3 mt-1">
            {/* Header row matching image.png */}
            <div className="flex items-center justify-between mt-1">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#4edea3] text-[22px]">track_changes</span>
                <h4 className="text-base font-bold text-white tracking-tight">
                  買場シグナル詳細分析
                </h4>
              </div>
              <span className="px-3 py-1 rounded-full bg-[#0d2822] border border-[#10b981]/40 text-[#4edea3] text-[11px] font-bold shadow-sm flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3]" />
                <span>{profile.buySignalZone || '超割安ゾーン（検討圏）'}</span>
              </span>
            </div>

            {/* Main Details Container Card */}
            <div className="p-3.5 rounded-2xl bg-[#0b1326] border border-slate-800 flex flex-col gap-3 shadow-md">
              {/* Box 1: 配当利回りレンジ判定 */}
              <div className="p-3.5 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-white">配当利回りレンジ判定</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#0d2822] text-[#4edea3] border border-[#10b981]/30">
                      {profile.yieldRangeBadge || '過去5年平均超え'}
                    </span>
                  </div>
                  <span className="font-mono text-2xl font-extrabold text-[#4edea3]">
                    {liveYield}%
                  </span>
                </div>

                {/* Range progress bar */}
                <div className="w-full h-2.5 bg-slate-800/90 rounded-full overflow-hidden p-[1px] my-1">
                  <div
                    className="h-full bg-gradient-to-r from-[#818cf8] via-[#38bdf8] to-[#4edea3] rounded-full shadow-[0_0_8px_rgba(78,222,163,0.4)]"
                    style={{
                      width: `${Math.min(100, Math.max(8, ((Number(liveYield) - (profile.yieldRangeMin || 2.10)) / ((profile.yieldRangeMax || 3.20) - (profile.yieldRangeMin || 2.10))) * 100))}%`
                    }}
                  />
                </div>

                <div className="flex justify-between text-[10px] font-medium pt-0.5">
                  <span className="text-[#86948a]">安値圏 {(profile.yieldRangeMin || 2.10).toFixed(2)}%</span>
                  <span className="text-[#86948a]">5年平均 {(profile.yieldRangeAvg || 2.45).toFixed(2)}%</span>
                  <span className="text-[#4edea3] font-bold">高値圏 {(profile.yieldRangeMax || 3.20).toFixed(2)}%</span>
                </div>
              </div>

              {/* Box 2 & 3: 2x1 Grid for PER & PBR */}
              <div className="grid grid-cols-2 gap-2.5">
                {/* PER（株価収益率） */}
                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] text-[#86948a]">PER（株価収益率）</span>
                    <span className="material-symbols-outlined text-[16px] text-slate-400">tune</span>
                  </div>
                  <span className="font-mono text-xl font-bold text-white my-1">{profile.per}</span>
                  <span className="text-[11px] text-[#4edea3] font-bold">{profile.perEvaluation || '市場平均比で割安'}</span>
                </div>

                {/* PBR（純資産倍率） */}
                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] text-[#86948a]">PBR（純資産倍率）</span>
                    <span className="material-symbols-outlined text-[16px] text-slate-400">tune</span>
                  </div>
                  <span className="font-mono text-xl font-bold text-white my-1">{profile.pbr}</span>
                  <span className="text-[11px] text-[#86948a] font-medium">{profile.pbrEvaluation || '資本効率適正圏'}</span>
                </div>
              </div>

              {/* Box 4: 買場シグナル総合スコア Row */}
              <div className="flex items-center justify-between p-3 rounded-xl bg-[#131b2e] border border-slate-800">
                <div className="flex items-center gap-2 font-bold text-white text-xs">
                  <span className="material-symbols-outlined text-[#4edea3] text-[20px]">trending_up</span>
                  <span>買場シグナル総合スコア</span>
                </div>
                <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#0d2822] border border-[#10b981]/35 text-[#4edea3] text-[11px] font-bold">
                  <span className="material-symbols-outlined text-[14px]">north_east</span>
                  <span>割安度スコア {profile.valuationScore || 88} / 100（検討圏）</span>
                </div>
              </div>

              {/* Box 5: Bottom AI / Summary Analysis Box */}
              <div className="p-3.5 rounded-xl bg-[#0d2822]/90 border border-[#10b981]/30 flex flex-col gap-2.5 text-xs shadow-sm">
                <div className="flex items-center gap-1.5 font-bold text-[#4edea3]">
                  <span className="material-symbols-outlined text-[18px]">auto_awesome</span>
                  <span>{profile.buySignalTitle || `【王道大型バリュー健全圏】PER ${profile.per} × PBR ${profile.pbr}`}</span>
                </div>
                <div>
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#13382c] text-[#4edea3] border border-[#10b981]/30">
                    {profile.buySignalBadge || '絶好の買増し検討期'}
                  </span>
                </div>
                <p className="text-slate-200 text-xs leading-relaxed">
                  <strong className="text-white font-bold">絶好の押し目買場判定：</strong>{' '}
                  {profile.buySignalComment || `PER ${profile.per}・PBR ${profile.pbr}と、日経平均平均（PER 15倍・PBR 1.4倍）を大幅に下回る優良バリュー水準。高収益性とキャッシュ創出力に対して割安に放置されており、配当再投資や中期的な買い増しに最適な好機シグナルです。`}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* HEALTH TAB (Exact image.png reproduction) */}
        {selectedStatusTab === 'health' && (
          <div className="flex flex-col gap-3 mt-1">
            {/* Header row matching image.png */}
            <div className="flex items-center justify-between mt-1">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#4edea3] text-[22px]">verified_user</span>
                <h4 className="text-base font-bold text-white tracking-tight">
                  健全性詳細分析
                </h4>
              </div>
              <span className="px-3 py-1 rounded-full bg-[#0d2822] border border-[#10b981]/40 text-[#4edea3] text-[11px] font-bold shadow-sm flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3]" />
                <span>{profile.healthGrade}</span>
                <span className="text-slate-300 font-normal">（ {profile.healthEvaluation} ）</span>
              </span>
            </div>

            {/* Main Details Container Card */}
            <div className="p-3.5 rounded-2xl bg-[#0b1326] border border-slate-800 flex flex-col gap-3 shadow-md">
              {/* Box 1: 配当性向 (当期見込) */}
              <div className="p-3.5 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-white">配当性向 (当期見込)</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#0d2822] text-[#4edea3] border border-[#10b981]/30">
                      {profile.payoutStatusBadge}
                    </span>
                  </div>
                  <span className="font-mono text-2xl font-extrabold text-[#4edea3]">
                    {profile.payoutRatio}%
                  </span>
                </div>

                {/* Progress bar */}
                <div className="w-full h-2.5 bg-slate-800/90 rounded-full overflow-hidden p-[1px] my-1">
                  <div
                    className="h-full bg-gradient-to-r from-[#10b981] to-[#4edea3] rounded-full shadow-[0_0_8px_rgba(16,185,129,0.4)]"
                    style={{ width: `${Math.min(100, Math.max(5, profile.payoutRatio))}%` }}
                  />
                </div>

                <div className="flex justify-between text-[10px] font-medium pt-0.5">
                  <span className="text-[#86948a]">0% (低負担)</span>
                  <span className="text-[#4edea3] font-bold">適正目安 30〜50%</span>
                  <span className="text-[#86948a]">100% (高負担)</span>
                </div>
              </div>

              {/* Box 2 & 3: 2x2 Grid of Financial Metrics */}
              <div className="grid grid-cols-2 gap-2.5">
                {/* 自己資本比率 */}
                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <span className="text-[11px] text-[#86948a]">自己資本比率</span>
                  <span className="font-mono text-xl font-bold text-white my-1">{profile.equityRatio}%</span>
                  <span className="text-[11px] text-[#4edea3] font-bold">{profile.equityRatioSub}</span>
                </div>

                {/* 株主還元方針 (DOE/累進) */}
                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <span className="text-[11px] text-[#86948a]">株主還元方針 (DOE/累進)</span>
                  <span className="font-mono text-xl font-bold text-white my-1">{profile.doePolicy}</span>
                  <span className="text-[11px] text-[#4edea3] font-bold">{profile.doePolicySub}</span>
                </div>

                {/* ネットD/Eレシオ */}
                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <span className="text-[11px] text-[#86948a]">ネットD/Eレシオ</span>
                  <span className="font-mono text-xl font-bold text-white my-1">{profile.netDeRatio}</span>
                  <span className="text-[11px] text-[#86948a] font-medium">財務余力・借入健全性</span>
                </div>

                {/* 営業利益率 */}
                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <span className="text-[11px] text-[#86948a]">営業利益率</span>
                  <span className="font-mono text-xl font-bold text-white my-1">{profile.operatingMargin}%</span>
                  <span className="text-[11px] text-[#86948a] font-medium">稼ぐ力・本業収益性</span>
                </div>
              </div>

              {/* Box 4: フリーキャッシュフロー余力 Row */}
              <div className="flex items-center justify-between p-3 rounded-xl bg-[#131b2e] border border-slate-800">
                <div className="flex items-center gap-2 font-bold text-white text-xs">
                  <span className="material-symbols-outlined text-[#4edea3] text-[20px]">water_drop</span>
                  <span>フリーキャッシュフロー余力</span>
                </div>
                <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#0d2822] border border-[#10b981]/35 text-[#4edea3] text-[11px] font-bold">
                  <span className="material-symbols-outlined text-[15px]">check_circle</span>
                  <span>{profile.fcfStatus}</span>
                </div>
              </div>

              {/* Box 5: Bottom AI / 総合自動診断 Box */}
              <div className="p-3.5 rounded-xl bg-[#0d2822]/90 border border-[#10b981]/30 flex flex-col gap-2.5 text-xs shadow-sm">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-[#4edea3]">
                    <span className="material-symbols-outlined text-[18px]">auto_awesome</span>
                    <span>{profile.modelType}</span>
                  </div>
                  <span className="px-2 py-0.5 rounded-md font-mono text-[10px] font-bold bg-[#131b2e] text-[#4edea3] border border-[#10b981]/30">
                    健全性スコア: {profile.healthScore}/100
                  </span>
                </div>
                <p className="text-slate-200 text-xs leading-relaxed">
                  <strong className="text-white font-bold">総合自動診断：</strong>
                  {profile.healthDiagnosisComment}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* GROWTH TAB */}
        {selectedStatusTab === 'growth' && (
          <div className="p-3 rounded-xl bg-[#0b1326] border border-indigo-400/40 flex flex-col gap-2 text-xs">
            <span className="font-bold text-white">
              {profile.consecutiveYears > 0 ? `${profile.consecutiveYears}期連続増配` : '累進・安定配当'}・{profile.noCutYears}年間非減配
            </span>
            <p className="text-[11px] text-[#bbcabf] leading-relaxed">
              {currentHolding.name}は過去の経済危機局面でも減配せず配当を維持・増配（増配率約+{profile.growthRate}%）。安定インカムの基盤となる優良な還元方針を有しています。
            </p>
          </div>
        )}

        {/* 集中リスク TAB / PANEL (Matching Image 6 with full dynamic fidelity) */}
        {selectedStatusTab === 'risk' && (
          <div className="flex flex-col gap-3 mt-1">
            {/* Header row matching image (6).png */}
            <div className="flex items-center justify-between mt-1">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#f59e0b] text-[22px]">warning</span>
                <h4 className="text-base font-bold text-white tracking-tight">集中リスク</h4>
              </div>
              <span className="px-3 py-1 rounded-full bg-[#2d2006] border border-[#f59e0b]/40 text-[#f59e0b] text-[11px] font-bold shadow-sm">
                {riskBadgeText}
              </span>
            </div>

            {/* Main Details Card matching image (6).png */}
            <div className="p-3.5 rounded-2xl bg-[#0b1326] border border-slate-800 flex flex-col gap-3 shadow-md">
              {/* Box 1: Portfolio dependency */}
              <div className="p-3.5 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-white">ポートフォリオ配当依存度</span>
                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                      isHighRisk ? 'bg-[#3b2707] text-[#f59e0b]' : 'bg-[#10b981]/20 text-[#4edea3]'
                    }`}>
                      {isHighRisk ? '上限超過' : '適正水準'}
                    </span>
                  </div>
                  <span className={`font-mono text-2xl font-extrabold ${isHighRisk ? 'text-[#f59e0b]' : 'text-[#4edea3]'}`}>
                    {divDependencyStr}%
                  </span>
                </div>

                {/* Progress bar gradient matching image (6).png */}
                <div className="w-full h-2.5 bg-slate-800/90 rounded-full overflow-hidden p-[1px] my-1">
                  <div
                    className="h-full bg-gradient-to-r from-[#4edea3] via-[#eab308] to-[#f59e0b] rounded-full shadow-[0_0_8px_rgba(245,158,11,0.4)]"
                    style={{ width: `${Math.min(100, Math.max(8, divDependency * 2))}%` }}
                  />
                </div>

                <div className="flex justify-between text-[10px] text-[#86948a] font-medium pt-0.5">
                  <span>0% (低依存)</span>
                  <span>推奨目安 10%未満</span>
                  <span>{currentHolding.name}比率 {divDependencyStr}%</span>
                </div>
              </div>

              {/* Box 2 & 3: Grid of 2 Boxes matching image (6).png */}
              <div className="grid grid-cols-2 gap-2.5">
                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <span className="text-[11px] text-[#86948a]">セクター集中度</span>
                  <span className="font-mono text-xl font-bold text-white my-1">{sectorDependencyStr}%</span>
                  <span className="text-[11px] text-[#f59e0b] font-bold">
                    {cleanSectorName}・{sectorRiskText}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col justify-between">
                  <span className="text-[11px] text-[#86948a]">仮に50%減配の影響</span>
                  <span className="font-mono text-xl font-bold text-[#ff6b6b] my-1">
                    -¥{cutImpact.toLocaleString()}
                  </span>
                  <span className="text-[11px] text-[#86948a] font-medium">
                    年間配当への影響度: {cutImpactLevel}
                  </span>
                </div>
              </div>

              {/* Row 4: Rebalance Recommendation Row matching image (6).png */}
              <div className="flex items-center justify-between p-3 rounded-xl bg-[#131b2e] border border-slate-800">
                <div className="flex items-center gap-2 font-bold text-white text-xs">
                  <span className="material-symbols-outlined text-[#f59e0b] text-[18px]">timelapse</span>
                  <span>リバランス推奨</span>
                </div>
                <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#2d2006] border border-[#f59e0b]/35 text-[#f59e0b] text-[11px] font-bold">
                  <span className="material-symbols-outlined text-[14px]">tune</span>
                  <span>他セクター優先投資を推奨</span>
                </div>
              </div>

              {/* Box 5: Diagnostic Comment Alert Box matching image (6).png */}
              <div className="p-3.5 rounded-xl bg-[#0d2822]/90 border border-[#10b981]/30 flex items-start gap-2.5 text-xs leading-relaxed shadow-sm">
                <span className="material-symbols-outlined text-[#4edea3] text-[20px] shrink-0 mt-0.5">auto_awesome</span>
                <p className="text-slate-200 text-xs leading-relaxed">
                  <strong className="text-white font-bold">診断コメント：</strong>
                  {isHighRisk
                    ? `配当依存度が約${divDependencyStr}%と目安（10%以下）を上回っています。今後の追加投資では他セクターを優先し、依存度を薄めるリバランスが推奨されます。`
                    : `配当依存度は約${divDependencyStr}%と目安（10%以下）の適正健全範囲内に収まっており、バランスの取れたインカム配分が維持されています。`
                  }
                </p>
              </div>
            </div>
          </div>
        )}
      </section>

      {/* 6. 次回権利落ち・直近イベント */}
      <section className="rounded-2xl bg-[#131b2e] p-3.5 shadow-md border border-slate-800 flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">calendar_month</span>
            <h3 className="font-bold text-sm text-white">次回権利落ち・直近イベント</h3>
          </div>
          <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold">
            通知設定中
          </span>
        </div>

        {/* Countdown */}
        <div className="p-3 rounded-xl bg-[#0b1326] border border-slate-800 flex items-center justify-between">
          <div>
            <span className="text-[10px] text-[#bbcabf]">9月末 中間配当</span>
            <p className="text-xs font-bold text-white mt-0.5">権利付き最終日まで</p>
          </div>
          <div className="flex items-baseline gap-1 px-3 py-1 rounded-lg bg-[#10b981]/20 text-[#4edea3] font-bold font-mono">
            <span className="text-[10px]">あと</span>
            <span className="text-xl">18</span>
            <span className="text-[10px]">日</span>
          </div>
        </div>

        <div className="p-2.5 rounded-xl bg-[#171f33] flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[#4edea3] text-[16px]">alarm_on</span>
            <span className="text-white">権利落ち・決算前通知</span>
          </div>
          <button
            type="button"
            onClick={() => {
              setIsAlertOn(!isAlertOn);
              showToast(isAlertOn ? 'イベント通知をオフにしました' : 'イベント通知をオンに設定しました');
            }}
            className={`w-11 h-6 rounded-full p-0.5 transition-colors ${isAlertOn ? 'bg-[#10b981]' : 'bg-slate-700'}`}
          >
            <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${isAlertOn ? 'translate-x-5' : 'translate-x-0'}`} />
          </button>
        </div>
      </section>

      {/* 7. 税務ステータス */}
      <section className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 flex items-start gap-3">
        <div className="w-9 h-9 rounded-xl bg-[#10b981]/20 text-[#4edea3] flex items-center justify-center shrink-0">
          <span className="material-symbols-outlined text-[20px]">redeem</span>
        </div>
        <div className="flex-1 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-bold text-white">NISA 非課税口座 適用中</span>
            <span className="px-2 py-0.2 rounded bg-[#10b981]/20 text-[#4edea3] font-bold text-[10px]">税率 0%</span>
          </div>
          <p className="text-[#bbcabf] text-[11px] mt-1 leading-relaxed">
            課税口座時の税額（20.315% = 約¥1,478/年）が全額非課税。受取額がそのまま手取り（¥7,280 / 100%受取）になります。
          </p>
        </div>
      </section>

      {/* Action Buttons */}
      <div className="grid grid-cols-2 gap-2 pt-1">
        <button
          type="button"
          onClick={() => openQuickEdit(currentHolding)}
          className="h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 border border-slate-700/60"
        >
          <span className="material-symbols-outlined text-[17px]">edit</span>
          保有情報を編集
        </button>
        <button
          type="button"
          onClick={() => showToast('銘柄の保有を解除しました', 'delete')}
          className="h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-[#ffb4ab] text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 border border-slate-700/60"
        >
          <span className="material-symbols-outlined text-[17px]">delete_outline</span>
          銘柄を解除
        </button>
      </div>
    </div>
  );
};
