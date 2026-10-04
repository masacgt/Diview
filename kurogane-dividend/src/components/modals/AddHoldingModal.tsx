import React, { useState } from 'react';
import { useApp } from '../../context/AppContext';
import { Holding } from '../../types';

interface StockCandidate {
  code: string;
  name: string;
  sector: string;
  price: number;
  yieldRate: number;
  dps: number;
  currency: '¥' | '$';
  category: 'japan' | 'us' | 'fund';
  dividendMonths: number[];
  dividendType: string;
}

const STOCK_PRESETS: Record<string, StockCandidate> = {
  '8316': { code: '8316', name: '三井住友FG', sector: '東証P • 銀行業', price: 3150, yieldRate: 4.13, dps: 130, currency: '¥', category: 'japan', dividendMonths: [6, 12], dividendType: '中間配当' },
  '7203': { code: '7203', name: 'トヨタ自動車', sector: '東証P • 輸送用機器', price: 2600, yieldRate: 2.80, dps: 72.8, currency: '¥', category: 'japan', dividendMonths: [6, 12], dividendType: '中間配当' },
  '9432': { code: '9432', name: '日本電信電話 (NTT)', sector: '東証P • 情報通信', price: 154.2, yieldRate: 3.37, dps: 5.2, currency: '¥', category: 'japan', dividendMonths: [6, 12], dividendType: '期末配当' },
  '8058': { code: '8058', name: '三菱商事', sector: '東証P • 卸売業', price: 3240, yieldRate: 3.52, dps: 100, currency: '¥', category: 'japan', dividendMonths: [6, 12], dividendType: '中間配当' },
  'AAPL': { code: 'AAPL', name: 'Apple Inc.', sector: 'NASDAQ • テクノロジー', price: 220, yieldRate: 0.52, dps: 1.0, currency: '$', category: 'us', dividendMonths: [2, 5, 8, 11], dividendType: '四半期配当' },
  'MSFT': { code: 'MSFT', name: 'Microsoft Corp.', sector: 'NASDAQ • ソフトウェア', price: 440, yieldRate: 0.76, dps: 3.0, currency: '$', category: 'us', dividendMonths: [3, 6, 9, 12], dividendType: '四半期配当' }
};

export const AddHoldingModal: React.FC = () => {
  const { isAddHoldingOpen, closeAddHolding, addHolding } = useApp();

  const [tickerInput, setTickerInput] = useState('8316');
  const [selectedStock, setSelectedStock] = useState<StockCandidate>(STOCK_PRESETS['8316']);

  // Multi-account matrix input
  const [nisaShares, setNisaShares] = useState(100);
  const [nisaPrice, setNisaPrice] = useState(3150);

  const [tsumitateShares, setTsumitateShares] = useState(0);
  const [tsumitatePrice, setTsumitatePrice] = useState(3150);

  const [tokuteiShares, setTokuteiShares] = useState(0);
  const [tokuteiPrice, setTokuteiPrice] = useState(3150);

  const [generalShares, setGeneralShares] = useState(0);
  const [generalPrice, setGeneralPrice] = useState(3150);

  if (!isAddHoldingOpen) return null;

  const handleStockSelect = (stock: StockCandidate) => {
    setSelectedStock(stock);
    setTickerInput(stock.code);
    setNisaPrice(stock.price);
    setTsumitatePrice(stock.price);
    setTokuteiPrice(stock.price);
    setGeneralPrice(stock.price);
  };

  const onTickerInputChange = (val: string) => {
    setTickerInput(val);
    const clean = val.trim().toUpperCase();
    if (STOCK_PRESETS[clean]) {
      handleStockSelect(STOCK_PRESETS[clean]);
    }
  };

  const isUSD = selectedStock.currency === '$';
  const prefix = isUSD ? '$' : '¥';
  const fxRate = isUSD ? 154 : 1;

  // Real-time matrix calculation
  const nisaGross = nisaShares * selectedStock.dps;
  const nisaNet = nisaGross;
  const nisaPrincipal = nisaShares * nisaPrice;
  const nisaTaxSavings = nisaGross * 0.20315 * fxRate;

  const tsumitateGross = tsumitateShares * selectedStock.dps;
  const tsumitateNet = tsumitateGross;
  const tsumitatePrincipal = tsumitateShares * tsumitatePrice;

  const tokuteiGross = tokuteiShares * selectedStock.dps;
  const tokuteiTax = tokuteiGross * 0.20315;
  const tokuteiNet = tokuteiGross - tokuteiTax;
  const tokuteiPrincipal = tokuteiShares * tokuteiPrice;

  const generalGross = generalShares * selectedStock.dps;
  const generalTax = generalGross * 0.20315;
  const generalNet = generalGross - generalTax;
  const generalPrincipal = generalShares * generalPrice;

  const totalShares = nisaShares + tsumitateShares + tokuteiShares + generalShares;
  const totalPrincipal = nisaPrincipal + tsumitatePrincipal + tokuteiPrincipal + generalPrincipal;
  const weightedAvgPrice = totalShares > 0 ? Math.round(totalPrincipal / totalShares) : selectedStock.price;
  const totalNetDividend = Math.round((nisaNet + tsumitateNet + tokuteiNet + generalNet) * fxRate);

  const applyCurrentPriceToAll = () => {
    setNisaPrice(selectedStock.price);
    setTsumitatePrice(selectedStock.price);
    setTokuteiPrice(selectedStock.price);
    setGeneralPrice(selectedStock.price);
  };

  const resetAllShares = () => {
    setNisaShares(0);
    setTsumitateShares(0);
    setTokuteiShares(0);
    setGeneralShares(0);
  };

  const handleSubmit = () => {
    if (totalShares <= 0) {
      alert('保有数量を1株以上指定してください');
      return;
    }

    const primaryAccount = nisaShares > 0 ? '新NISA成長枠' : tokuteiShares > 0 ? '特定口座' : '一般口座';
    const halfDiv = Math.round((totalShares * selectedStock.dps * fxRate) / 2);

    const newHolding: Holding = {
      id: 'h-' + selectedStock.code.toLowerCase() + '-' + Date.now(),
      code: selectedStock.code,
      name: selectedStock.name,
      sector: selectedStock.sector,
      market: selectedStock.category === 'us' ? 'NASDAQ' : '東証PRM',
      category: selectedStock.category,
      price: selectedStock.price,
      currency: selectedStock.currency,
      shares: totalShares,
      acqPrice: weightedAvgPrice,
      dps: selectedStock.dps,
      yieldRate: selectedStock.yieldRate,
      yoc: Number(((selectedStock.dps / weightedAvgPrice) * 100).toFixed(2)),
      nextPayoutTiming: '次回: 12月中旬',
      nextPayoutAmount: `¥${halfDiv.toLocaleString()}`,
      dividendMonths: selectedStock.dividendMonths,
      dividendType: selectedStock.dividendType,
      accountType: primaryAccount,
      dailyChange: '+¥0 (新規登録)',
      isPositive: true
    };

    addHolding(newHolding);
    closeAddHolding();
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex flex-col justify-end p-0"
      onClick={closeAddHolding}
    >
      <div
        className="w-full max-w-lg mx-auto bg-[#131b2e] border-t border-slate-700/80 rounded-t-3xl p-4 flex flex-col gap-3 shadow-2xl max-h-[85vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto -mt-1 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-1.5 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-[#171f33] border border-slate-700/60 flex items-center justify-center text-[#4edea3]">
              <span className="material-symbols-outlined text-[20px]">table_chart</span>
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <h3 className="font-bold text-sm text-white">複数口座一括配分登録</h3>
                <span className="px-1.5 py-0.2 rounded bg-[#10b981]/20 text-[#4edea3] text-[9px] font-bold">
                  マトリクス同時入力
                </span>
              </div>
              <p className="text-[10px] text-[#bbcabf]">口座間の株数・取得単価を1画面でまとめて設定</p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeAddHolding}
            className="w-8 h-8 rounded-full bg-[#171f33] text-[#bbcabf] hover:text-white flex items-center justify-center shrink-0"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Target Stock Selection & Summary */}
        <div className="space-y-2">
          {/* Ticker Search Box */}
          <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 flex flex-col gap-1.5">
            <div className="flex items-center justify-between text-[11px]">
              <span className="font-bold text-white flex items-center gap-1">
                <span className="material-symbols-outlined text-[#4edea3] text-[15px]">tag</span>
                銘柄コード / 企業名
              </span>
              <span className="text-[10px] text-[#4edea3] font-mono">
                {selectedStock.name} (利回り {selectedStock.yieldRate}%)
              </span>
            </div>

            <input
              type="text"
              value={tickerInput}
              onChange={e => onTickerInputChange(e.target.value)}
              placeholder="例: 8316, 7203, 9432, AAPL"
              className="w-full h-10 px-3 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono font-bold text-xs focus:outline-none focus:border-[#4edea3]"
            />

            {/* Quick Candidate Chips */}
            <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar pt-0.5">
              {Object.values(STOCK_PRESETS).map(item => (
                <button
                  key={item.code}
                  type="button"
                  onClick={() => handleStockSelect(item)}
                  className={`px-2 py-0.5 rounded-lg border text-[11px] font-mono shrink-0 flex items-center gap-1 transition-colors ${
                    selectedStock.code === item.code
                      ? 'bg-[#10b981]/20 border-[#4edea3] text-[#4edea3] font-bold'
                      : 'bg-[#171f33] border-slate-800 text-[#bbcabf] hover:text-white'
                  }`}
                >
                  <span className="font-bold">{item.code}</span>
                  <span>{item.name.split(' ')[0]}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Selected Stock Info Banner */}
          <div className="p-2.5 rounded-xl bg-[#171f33] border border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded bg-slate-800 font-mono font-bold text-xs text-[#4edea3]">
                {selectedStock.code}
              </span>
              <div>
                <p className="font-bold text-xs text-white leading-tight">{selectedStock.name}</p>
                <p className="text-[10px] text-[#bbcabf]">{selectedStock.sector}</p>
              </div>
            </div>
            <div className="text-right">
              <span className="font-mono font-bold text-xs text-white">
                {prefix}{selectedStock.price.toLocaleString()}
              </span>
              <span className="block text-[10px] text-[#4edea3] font-bold">
                配当利回り {selectedStock.yieldRate}%
              </span>
            </div>
          </div>
        </div>

        {/* Account Matrix Section Bar */}
        <div className="flex items-center justify-between px-1">
          <span className="text-[11px] font-bold text-white flex items-center gap-1">
            <span className="material-symbols-outlined text-[15px] text-[#4edea3]">account_balance</span>
            口座別 ポートフォリオ配分マトリクス
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={applyCurrentPriceToAll}
              className="text-[10px] text-[#4edea3] hover:underline flex items-center gap-0.5"
            >
              <span className="material-symbols-outlined text-[12px]">auto_fix_high</span>
              現在値を適用
            </button>
            <button
              type="button"
              onClick={resetAllShares}
              className="text-[10px] text-[#bbcabf] hover:text-white flex items-center gap-0.5"
            >
              <span className="material-symbols-outlined text-[12px]">refresh</span>
              株数クリア
            </button>
          </div>
        </div>

        {/* 4 Accounts Grid */}
        <div className="space-y-2">
          {/* Row 1: 新NISA 成長投資枠 (非課税 0%) */}
          <div className={`p-2.5 rounded-xl bg-[#0b1326] border transition-colors ${nisaShares > 0 ? 'border-[#4edea3]/50' : 'border-slate-800'}`}>
            <div className="flex items-center justify-between mb-1.5">
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-[#4edea3]" />
                <span className="text-xs font-bold text-white">新NISA 成長投資枠</span>
                <span className="px-1.5 py-0.2 rounded bg-[#10b981]/20 text-[#4edea3] text-[9px] font-bold">
                  非課税 0%
                </span>
              </div>
              <span className="text-xs font-mono font-bold text-[#4edea3]">
                {prefix}{Math.round(nisaNet).toLocaleString()}/年
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <div className="flex items-center justify-between text-[10px] text-[#bbcabf] mb-0.5">
                  <span>株数</span>
                  <div className="flex gap-1 font-mono">
                    <button type="button" onClick={() => setNisaShares(nisaShares + 10)} className="hover:text-white">+10</button>
                    <button type="button" onClick={() => setNisaShares(nisaShares + 100)} className="hover:text-white">+100</button>
                  </div>
                </div>
                <input
                  type="number"
                  min="0"
                  value={nisaShares}
                  onChange={e => setNisaShares(Math.max(0, parseInt(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
              <div>
                <span className="text-[10px] text-[#bbcabf] block mb-0.5">取得単価</span>
                <input
                  type="number"
                  min="0"
                  value={nisaPrice}
                  onChange={e => setNisaPrice(Math.max(0, parseFloat(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
            </div>
          </div>

          {/* Row 2: 新NISA つみたて枠 */}
          <div className={`p-2.5 rounded-xl bg-[#0b1326] border transition-colors ${tsumitateShares > 0 ? 'border-[#4edea3]/50' : 'border-slate-800'}`}>
            <div className="flex items-center justify-between mb-1.5">
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-teal-400" />
                <span className="text-xs font-bold text-white">新NISA つみたて枠</span>
                <span className="px-1.5 py-0.2 rounded bg-teal-500/20 text-teal-400 text-[9px] font-bold">
                  非課税 0%
                </span>
              </div>
              <span className="text-xs font-mono font-bold text-[#4edea3]">
                {prefix}{Math.round(tsumitateNet).toLocaleString()}/年
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <div className="flex items-center justify-between text-[10px] text-[#bbcabf] mb-0.5">
                  <span>株数 / 口数</span>
                  <div className="flex gap-1 font-mono">
                    <button type="button" onClick={() => setTsumitateShares(tsumitateShares + 10)} className="hover:text-white">+10</button>
                    <button type="button" onClick={() => setTsumitateShares(tsumitateShares + 100)} className="hover:text-white">+100</button>
                  </div>
                </div>
                <input
                  type="number"
                  min="0"
                  value={tsumitateShares}
                  onChange={e => setTsumitateShares(Math.max(0, parseInt(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
              <div>
                <span className="text-[10px] text-[#bbcabf] block mb-0.5">取得単価</span>
                <input
                  type="number"
                  min="0"
                  value={tsumitatePrice}
                  onChange={e => setTsumitatePrice(Math.max(0, parseFloat(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
            </div>
          </div>

          {/* Row 3: 特定口座 (課税 20.315%) */}
          <div className={`p-2.5 rounded-xl bg-[#0b1326] border transition-colors ${tokuteiShares > 0 ? 'border-indigo-400/50' : 'border-slate-800'}`}>
            <div className="flex items-center justify-between mb-1.5">
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-indigo-400" />
                <span className="text-xs font-bold text-white">特定口座 (源泉徴収あり)</span>
                <span className="px-1.5 py-0.2 rounded bg-slate-800 text-[#bbcabf] text-[9px] font-bold">
                  課税 20.315%
                </span>
              </div>
              <span className="text-xs font-mono font-bold text-slate-200">
                {prefix}{Math.round(tokuteiNet).toLocaleString()}/年
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <div className="flex items-center justify-between text-[10px] text-[#bbcabf] mb-0.5">
                  <span>株数</span>
                  <div className="flex gap-1 font-mono">
                    <button type="button" onClick={() => setTokuteiShares(tokuteiShares + 10)} className="hover:text-white">+10</button>
                    <button type="button" onClick={() => setTokuteiShares(tokuteiShares + 100)} className="hover:text-white">+100</button>
                  </div>
                </div>
                <input
                  type="number"
                  min="0"
                  value={tokuteiShares}
                  onChange={e => setTokuteiShares(Math.max(0, parseInt(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
              <div>
                <span className="text-[10px] text-[#bbcabf] block mb-0.5">取得単価</span>
                <input
                  type="number"
                  min="0"
                  value={tokuteiPrice}
                  onChange={e => setTokuteiPrice(Math.max(0, parseFloat(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
            </div>
          </div>

          {/* Row 4: 一般口座 */}
          <div className={`p-2.5 rounded-xl bg-[#0b1326] border transition-colors ${generalShares > 0 ? 'border-slate-500' : 'border-slate-800'}`}>
            <div className="flex items-center justify-between mb-1.5">
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-slate-500" />
                <span className="text-xs font-bold text-white">一般口座</span>
                <span className="px-1.5 py-0.2 rounded bg-slate-800 text-[#bbcabf] text-[9px] font-bold">
                  課税 20.315%
                </span>
              </div>
              <span className="text-xs font-mono font-bold text-slate-300">
                {prefix}{Math.round(generalNet).toLocaleString()}/年
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <span className="text-[10px] text-[#bbcabf] block mb-0.5">株数</span>
                <input
                  type="number"
                  min="0"
                  value={generalShares}
                  onChange={e => setGeneralShares(Math.max(0, parseInt(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
              <div>
                <span className="text-[10px] text-[#bbcabf] block mb-0.5">取得単価</span>
                <input
                  type="number"
                  min="0"
                  value={generalPrice}
                  onChange={e => setGeneralPrice(Math.max(0, parseFloat(e.target.value) || 0))}
                  className="w-full h-8 px-2 rounded-lg bg-[#171f33] border border-slate-700/80 text-white font-mono text-xs text-right focus:border-[#4edea3]"
                />
              </div>
            </div>
          </div>
        </div>

        {/* Consolidated Total Summary Bar */}
        <div className="p-3 rounded-2xl bg-gradient-to-br from-[#102436] via-[#171f33] to-[#0d1629] border border-[#4edea3]/30 space-y-2">
          <div className="flex items-center justify-between text-[11px]">
            <span className="font-bold text-white flex items-center gap-1">
              <span className="material-symbols-outlined text-[15px] text-[#4edea3]">query_stats</span>
              全口座 総合アロケーションサマリー
            </span>
            <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[9px] font-bold">
              手取り最大化
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 pt-0.5">
            <div className="bg-[#0b1326]/75 rounded-lg p-2 border border-slate-800">
              <span className="text-[9px] text-[#bbcabf] block">合計保有株数</span>
              <span className="font-mono text-sm font-bold text-white mt-0.5 block">
                {totalShares.toLocaleString()} 株
              </span>
            </div>
            <div className="bg-[#0b1326]/75 rounded-lg p-2 border border-slate-800">
              <span className="text-[9px] text-[#bbcabf] block">加重平均取得単価</span>
              <span className="font-mono text-sm font-bold text-white mt-0.5 block">
                {prefix}{weightedAvgPrice.toLocaleString()}
              </span>
            </div>
            <div className="bg-[#0b1326]/75 rounded-lg p-2 border border-[#4edea3]/30">
              <span className="text-[9px] text-[#4edea3] block font-semibold">年間配当 (手取り総額)</span>
              <span className="font-mono text-sm font-bold text-[#4edea3] mt-0.5 block">
                ¥{totalNetDividend.toLocaleString()} /年
              </span>
            </div>
            <div className="bg-[#0b1326]/75 rounded-lg p-2 border border-slate-800">
              <span className="text-[9px] text-indigo-300 block font-semibold">NISA節税メリット</span>
              <span className="font-mono text-sm font-bold text-indigo-300 mt-0.5 block">
                +¥{Math.round(nisaTaxSavings).toLocaleString()} /年
              </span>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex gap-2 pt-1">
          <button
            type="button"
            onClick={closeAddHolding}
            className="w-1/3 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
          >
            キャンセル
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            className="flex-1 h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs flex items-center justify-center gap-1.5 shadow-lg shadow-emerald-500/20 active:scale-98"
          >
            <span className="material-symbols-outlined text-[18px]">done_all</span>
            すべての口座にまとめて登録する
          </button>
        </div>
      </div>
    </div>
  );
};
