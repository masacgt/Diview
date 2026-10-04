import React, { useState, useEffect } from 'react';
import { useApp } from '../../context/AppContext';

export const QuickEditModal: React.FC = () => {
  const { quickEditHolding, closeQuickEdit, updateHolding } = useApp();

  const [shares, setShares] = useState(100);
  const [price, setPrice] = useState(2450);

  useEffect(() => {
    if (quickEditHolding) {
      setShares(quickEditHolding.shares);
      setPrice(quickEditHolding.acqPrice);
    }
  }, [quickEditHolding]);

  if (!quickEditHolding) return null;

  const currentPrice = quickEditHolding.price;
  const isUSD = quickEditHolding.currency === '$';
  const prefix = isUSD ? '$' : '¥';

  const costTotal = shares * price;
  const curVal = shares * currentPrice;
  const gain = curVal - costTotal;
  const gainPct = costTotal > 0 ? ((gain / costTotal) * 100).toFixed(1) : '0';
  const annualDiv = Math.round(curVal * (quickEditHolding.yieldRate / 100));

  const handleSave = () => {
    updateHolding(quickEditHolding.code, shares, price);
    closeQuickEdit();
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 backdrop-blur-md flex flex-col justify-end p-0"
      onClick={closeQuickEdit}
    >
      <div
        className="w-full max-w-lg mx-auto bg-[#131b2e] border-t border-slate-700/80 rounded-t-3xl p-4 flex flex-col gap-3.5 shadow-2xl max-h-[85vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto -mt-1 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-slate-800">
          <div className="flex items-center gap-2 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-[#171f33] border border-slate-700/60 flex items-center justify-center text-[#4edea3] shrink-0">
              <span className="material-symbols-outlined text-[18px]">tune</span>
            </div>
            <div className="truncate">
              <h3 className="font-bold text-sm text-white truncate">保有情報のクイック編集</h3>
              <p className="text-[11px] text-[#bbcabf] truncate">
                <span className="font-mono font-bold text-[#4edea3] mr-1">{quickEditHolding.code}</span>
                {quickEditHolding.name}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeQuickEdit}
            className="w-8 h-8 rounded-full bg-[#171f33] text-[#bbcabf] hover:text-white flex items-center justify-center shrink-0"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Market Price Strip */}
        <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-[11px] text-[#bbcabf]">現在値:</span>
            <span className="font-mono font-bold text-base text-white">
              {prefix}{currentPrice.toLocaleString()}
            </span>
          </div>
          <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] font-mono text-[11px] font-bold">
            {quickEditHolding.dailyChange}
          </span>
        </div>

        {/* Section 1: Shares Input with Steppers */}
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center justify-between text-[11px]">
            <label className="font-semibold text-white flex items-center gap-1">
              <span className="material-symbols-outlined text-[#4edea3] text-[15px]">inventory_2</span>
              保有数量 (株)
            </label>
            <span className="font-mono text-[#bbcabf]">単元: 100株</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setShares(Math.max(1, shares - 10))}
              className="w-11 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white font-bold flex items-center justify-center border border-slate-800 active:scale-95"
            >
              <span className="material-symbols-outlined text-[18px]">remove</span>
            </button>
            <div className="flex-1 relative flex items-center">
              <input
                type="number"
                min="1"
                value={shares}
                onChange={e => setShares(Math.max(1, parseInt(e.target.value) || 1))}
                className="w-full h-11 text-center rounded-xl bg-[#0b1326] border border-slate-700/80 text-white font-mono font-bold text-lg focus:outline-none focus:border-[#4edea3]"
              />
              <span className="absolute right-3 text-xs text-[#bbcabf] pointer-events-none">株</span>
            </div>
            <button
              type="button"
              onClick={() => setShares(shares + 10)}
              className="w-11 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white font-bold flex items-center justify-center border border-slate-800 active:scale-95"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
            </button>
          </div>

          {/* Quick Addition Chips */}
          <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
            <button
              type="button"
              onClick={() => setShares(100)}
              className="px-2.5 py-1 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-[11px] font-mono shrink-0"
            >
              1単元 (100株)
            </button>
            <button
              type="button"
              onClick={() => setShares(shares + 10)}
              className="px-2.5 py-1 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-[11px] font-mono shrink-0"
            >
              +10
            </button>
            <button
              type="button"
              onClick={() => setShares(shares + 100)}
              className="px-2.5 py-1 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-[11px] font-mono shrink-0"
            >
              +100
            </button>
            <button
              type="button"
              onClick={() => setShares(shares + 500)}
              className="px-2.5 py-1 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-[11px] font-mono shrink-0"
            >
              +500
            </button>
          </div>
        </div>

        {/* Section 2: Acquisition Price Input */}
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center justify-between text-[11px]">
            <label className="font-semibold text-white flex items-center gap-1">
              <span className="material-symbols-outlined text-[#4edea3] text-[15px]">sell</span>
              取得平均単価 (円)
            </label>
            <button
              type="button"
              onClick={() => setPrice(currentPrice)}
              className="text-[#4edea3] hover:underline flex items-center gap-0.5 font-medium"
            >
              <span className="material-symbols-outlined text-[12px]">sync</span>
              現在値 ({prefix}{currentPrice}) を適用
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPrice(Math.max(1, price - 10))}
              className="w-11 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white font-bold flex items-center justify-center border border-slate-800 active:scale-95"
            >
              <span className="material-symbols-outlined text-[18px]">remove</span>
            </button>
            <div className="flex-1 relative flex items-center">
              <span className="absolute left-3 text-sm font-mono text-[#bbcabf]">{prefix}</span>
              <input
                type="number"
                min="1"
                value={price}
                onChange={e => setPrice(Math.max(1, parseFloat(e.target.value) || 1))}
                className="w-full h-11 pl-7 pr-3 text-center rounded-xl bg-[#0b1326] border border-slate-700/80 text-white font-mono font-bold text-lg focus:outline-none focus:border-[#4edea3]"
              />
            </div>
            <button
              type="button"
              onClick={() => setPrice(price + 10)}
              className="w-11 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white font-bold flex items-center justify-center border border-slate-800 active:scale-95"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
            </button>
          </div>
        </div>

        {/* Section 3: Realtime Calculated Impact Card */}
        <div className="p-3 rounded-xl bg-[#0b1326] border border-[#4edea3]/40 flex flex-col gap-2 shadow-inner">
          <div className="flex items-center justify-between text-[11px]">
            <span className="text-[#4edea3] font-bold flex items-center gap-1">
              <span className="material-symbols-outlined text-[14px]">analytics</span>
              更新後のリアルタイム試算
            </span>
            <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] font-mono text-[10px] font-bold">
              即時再計算中
            </span>
          </div>
          <div className="grid grid-cols-3 gap-2 text-center pt-1 border-t border-slate-800">
            <div className="flex flex-col items-start">
              <span className="text-[10px] text-[#bbcabf]">投資元本</span>
              <span className="font-mono font-bold text-white text-xs mt-0.5">
                {prefix}{Math.round(costTotal).toLocaleString()}
              </span>
            </div>
            <div className="flex flex-col items-center">
              <span className="text-[10px] text-[#bbcabf]">評価損益</span>
              <span className={`font-mono font-bold text-xs mt-0.5 ${gain >= 0 ? 'text-[#4edea3]' : 'text-[#ffb4ab]'}`}>
                {gain >= 0 ? `+${prefix}${Math.round(gain).toLocaleString()} (+${gainPct}%)` : `-${prefix}${Math.round(Math.abs(gain)).toLocaleString()} (${gainPct}%)`}
              </span>
            </div>
            <div className="flex flex-col items-end">
              <span className="text-[10px] text-[#bbcabf]">年間予想配当</span>
              <span className="font-mono font-bold text-[#4edea3] text-xs mt-0.5">
                {prefix}{annualDiv.toLocaleString()}/年
              </span>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex gap-2 pt-1">
          <button
            type="button"
            onClick={closeQuickEdit}
            className="w-1/3 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
          >
            キャンセル
          </button>
          <button
            type="button"
            onClick={handleSave}
            className="flex-1 h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs flex items-center justify-center gap-1.5 shadow-lg shadow-emerald-500/20 active:scale-98"
          >
            <span className="material-symbols-outlined text-[18px]">check</span>
            変更を保存する
          </button>
        </div>
      </div>
    </div>
  );
};
