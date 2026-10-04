import React from 'react';
import { useApp } from '../../context/AppContext';

interface MonthlyForecastModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const MonthlyForecastModal: React.FC<MonthlyForecastModalProps> = ({ isOpen, onClose }) => {
  const { showToast } = useApp();

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex flex-col justify-end p-0"
      onClick={onClose}
    >
      <div
        className="bg-[#131b2e] border-t border-slate-700/80 rounded-t-3xl p-4 w-full max-w-lg mx-auto shadow-2xl relative flex flex-col max-h-[85vh] overflow-y-auto no-scrollbar"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700/90 rounded-full mx-auto mb-2.5 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-2.5 border-b border-slate-800/80 shrink-0">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-500/15 border border-indigo-400/30 flex items-center justify-center text-indigo-300 shrink-0">
              <span className="material-symbols-outlined text-[20px]">bar_chart</span>
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <h2 className="text-sm font-bold text-white tracking-tight">受取月別グラフ試算</h2>
                <span className="px-1.5 py-0.2 rounded-full bg-indigo-500/20 text-indigo-300 text-[9px] font-bold">
                  平準化シミュレーション
                </span>
              </div>
              <p className="text-[10px] text-[#bbcabf]">監視4銘柄の入金タイミング・キャッシュフロー偏重分析</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center text-[#bbcabf] hover:text-white rounded-full bg-[#171f33] border border-slate-800 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* 3 Mini KPI Strip */}
        <div className="grid grid-cols-3 gap-2 my-2.5 shrink-0">
          <div className="flex flex-col bg-[#0b1326]/85 rounded-xl p-2 border border-slate-800/90">
            <span className="text-[9px] text-[#bbcabf] font-medium">年間受取予定</span>
            <div className="flex items-baseline gap-0.5 mt-0.5">
              <span className="text-[10px] text-[#4edea3] font-bold">¥</span>
              <span className="font-mono text-sm font-bold text-[#4edea3]">42,800</span>
              <span className="text-[8px] text-[#bbcabf]">/年</span>
            </div>
          </div>
          <div className="flex flex-col bg-[#0b1326]/85 rounded-xl p-2 border border-slate-800/90">
            <span className="text-[9px] text-[#bbcabf] font-medium">月平均手取り</span>
            <div className="flex items-baseline gap-0.5 mt-0.5">
              <span className="text-[10px] text-white font-bold">¥</span>
              <span className="font-mono text-sm font-bold text-white">3,567</span>
              <span className="text-[8px] text-[#bbcabf]">/月</span>
            </div>
          </div>
          <div className="flex flex-col bg-[#0b1326]/85 rounded-xl p-2 border border-slate-800">
            <span className="text-[9px] text-[#ffb4ab] font-medium flex items-center gap-0.5">
              <span className="material-symbols-outlined text-[11px]">warning</span>インカム空白
            </span>
            <div className="flex items-baseline gap-0.5 mt-0.5">
              <span className="font-mono text-sm font-bold text-slate-300">4</span>
              <span className="text-[9px] text-slate-300 font-bold">ヶ月</span>
              <span className="text-[8px] text-[#bbcabf] ml-0.5">(無収入)</span>
            </div>
          </div>
        </div>

        {/* Quarterly Cash Flow Comparison */}
        <div className="bg-[#171f33] rounded-2xl p-3 border border-slate-800/90 mb-3 space-y-2.5 shadow-md shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5 text-xs text-white font-bold">
              <span className="material-symbols-outlined text-[#4edea3] text-[16px]">pie_chart</span>
              <span>四半期キャッシュフロー対比</span>
            </div>
            <span className="text-[9px] font-mono text-indigo-300 font-semibold bg-indigo-500/10 px-2 py-0.5 rounded-full border border-indigo-500/30">
              Q2・Q4偏重 85.0%
            </span>
          </div>

          <div className="w-full h-3 rounded-full bg-slate-800/90 flex overflow-hidden p-0.5 gap-0.5 border border-slate-700/60">
            <div className="h-full rounded-l-full bg-indigo-400" style={{ width: '7.5%' }} title="Q1: ¥3,200 (7.5%)" />
            <div className="h-full bg-gradient-to-r from-[#10b981] to-[#4edea3]" style={{ width: '42.5%' }} title="Q2: ¥18,200 (42.5%)" />
            <div className="h-full bg-indigo-400" style={{ width: '7.5%' }} title="Q3: ¥3,200 (7.5%)" />
            <div className="h-full rounded-r-full bg-gradient-to-r from-teal-500 to-[#10b981]" style={{ width: '42.5%' }} title="Q4: ¥18,200 (42.5%)" />
          </div>

          <div className="grid grid-cols-4 gap-1.5 text-center">
            <div className="flex flex-col items-center bg-[#0b1326]/75 rounded-lg py-1.5 px-1 border border-slate-800">
              <span className="text-[9px] text-[#bbcabf] font-mono">Q1 (1-3月)</span>
              <span className="font-mono text-[11px] font-bold text-indigo-300 mt-0.5">¥3,200</span>
              <span className="text-[8px] text-[#bbcabf] font-mono">7.5%</span>
            </div>
            <div className="flex flex-col items-center bg-[#0b1326]/75 rounded-lg py-1.5 px-1 border border-[#4edea3]/40 relative overflow-hidden bg-[#10b981]/5">
              <div className="absolute top-0 right-0 w-2 h-2 bg-[#4edea3] rounded-bl" />
              <span className="text-[9px] text-[#4edea3] font-mono font-bold">Q2 (4-6月)</span>
              <span className="font-mono text-[11px] font-bold text-[#4edea3] mt-0.5">¥18,200</span>
              <span className="text-[8px] text-[#4edea3] font-mono font-bold">42.5%</span>
            </div>
            <div className="flex flex-col items-center bg-[#0b1326]/75 rounded-lg py-1.5 px-1 border border-slate-800">
              <span className="text-[9px] text-[#bbcabf] font-mono">Q3 (7-9月)</span>
              <span className="font-mono text-[11px] font-bold text-indigo-300 mt-0.5">¥3,200</span>
              <span className="text-[8px] text-[#bbcabf] font-mono">7.5%</span>
            </div>
            <div className="flex flex-col items-center bg-[#0b1326]/75 rounded-lg py-1.5 px-1 border border-[#4edea3]/40 relative overflow-hidden bg-[#10b981]/5">
              <div className="absolute top-0 right-0 w-2 h-2 bg-[#4edea3] rounded-bl" />
              <span className="text-[9px] text-[#4edea3] font-mono font-bold">Q4 (10-12月)</span>
              <span className="font-mono text-[11px] font-bold text-[#4edea3] mt-0.5">¥18,200</span>
              <span className="text-[8px] text-[#4edea3] font-mono font-bold">42.5%</span>
            </div>
          </div>
        </div>

        {/* 12-Month Calendar Bar Chart */}
        <div className="bg-[#171f33] rounded-2xl p-3 border border-slate-800/90 mb-3 space-y-2.5 shadow-md shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5 text-xs text-white font-bold">
              <span className="material-symbols-outlined text-[#4edea3] text-[16px]">calendar_view_month</span>
              <span>12ヶ月 受取配当カレンダー棒グラフ</span>
            </div>
            <div className="flex items-center gap-2 text-[9px]">
              <span className="flex items-center gap-1 text-[#4edea3]"><span className="w-1.5 h-1.5 rounded-full bg-[#4edea3] inline-block" />日株集中</span>
              <span className="flex items-center gap-1 text-indigo-300"><span className="w-1.5 h-1.5 rounded-full bg-indigo-400 inline-block" />米株</span>
              <span className="flex items-center gap-1 text-slate-500"><span className="w-1.5 h-1.5 rounded-full bg-slate-700 inline-block border border-dashed border-slate-600" />空白</span>
            </div>
          </div>

          <div className="h-28 bg-[#0b1326]/85 rounded-xl px-1.5 pt-3 pb-1 border border-slate-800/90 flex items-end justify-between gap-1">
            {[
              { m: 1, val: '-', h: 'h-2 border border-dashed border-slate-700 rounded', type: 'blank' },
              { m: 2, val: '0.8k', h: 'h-[16%] bg-indigo-400', type: 'us' },
              { m: 3, val: '2.4k', h: 'h-[25%] bg-indigo-400', type: 'us' },
              { m: 4, val: '-', h: 'h-2 border border-dashed border-slate-700 rounded', type: 'blank' },
              { m: 5, val: '0.8k', h: 'h-[16%] bg-indigo-400', type: 'us' },
              { m: 6, val: '17.4k', h: 'h-[88%] bg-gradient-to-t from-[#10b981] to-[#4edea3]', type: 'jp' },
              { m: 7, val: '-', h: 'h-2 border border-dashed border-slate-700 rounded', type: 'blank' },
              { m: 8, val: '0.8k', h: 'h-[16%] bg-indigo-400', type: 'us' },
              { m: 9, val: '2.4k', h: 'h-[25%] bg-indigo-400', type: 'us' },
              { m: 10, val: '-', h: 'h-2 border border-dashed border-slate-700 rounded', type: 'blank' },
              { m: 11, val: '0.8k', h: 'h-[16%] bg-indigo-400', type: 'us' },
              { m: 12, val: '17.4k', h: 'h-[88%] bg-gradient-to-t from-[#10b981] to-[#4edea3]', type: 'jp' }
            ].map(col => (
              <div key={col.m} className="flex-1 flex flex-col items-center h-full justify-end">
                <div className="w-full flex-1 flex flex-col justify-end items-center mb-0.5">
                  {col.val !== '-' && (
                    <span className={`text-[7.5px] font-mono leading-none mb-0.5 font-bold ${col.type === 'jp' ? 'text-[#4edea3]' : 'text-indigo-300'}`}>
                      {col.val}
                    </span>
                  )}
                  <div className={`w-full max-w-[18px] rounded-t ${col.h}`} />
                </div>
                <span className={`text-[8.5px] font-mono font-bold ${col.type === 'jp' ? 'text-[#4edea3]' : col.type === 'us' ? 'text-indigo-300' : 'text-slate-500'}`}>
                  {col.m}
                </span>
              </div>
            ))}
          </div>
          <div className="flex items-center justify-between text-[10px] text-[#bbcabf] px-1 pt-0.5">
            <span>日本株(3/9月期)は <strong className="text-white">6月・12月</strong> 入金</span>
            <span className="text-[#ffb4ab] font-semibold flex items-center gap-0.5">
              <span className="material-symbols-outlined text-[13px]">block</span>空白月: 1, 4, 7, 10月
            </span>
          </div>
        </div>

        {/* Advice Card */}
        <div className="bg-gradient-to-br from-[#102436] via-[#131b2e] to-[#0d1629] rounded-2xl p-3 border border-indigo-400/40 space-y-2 mb-3 shadow-md shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <div className="w-6 h-6 rounded-md bg-indigo-500/20 border border-indigo-400/40 flex items-center justify-center text-indigo-300">
                <span className="material-symbols-outlined text-[15px]">tips_and_updates</span>
              </div>
              <span className="text-xs font-bold text-white">インカム空白月解消アドバイス</span>
            </div>
            <span className="text-[9px] bg-indigo-500/20 text-indigo-300 border border-indigo-400/30 px-2 py-0.5 rounded-full font-bold">
              平準化おすすめ
            </span>
          </div>
          <p className="text-[10px] text-[#bbcabf] leading-relaxed">
            <strong className="text-white font-semibold">1月・4月・7月・10月決算</strong> の高配当銘柄（積水ハウス等）やREITを追加すると、入金タイムラグを補完し、<strong className="text-[#4edea3] font-bold">毎月途切れない配当キャッシュフロー</strong>が完成します。
          </p>
          <div className="flex items-center justify-between pt-1 border-t border-slate-700/60">
            <div className="flex items-center gap-2">
              <span className="px-1.5 py-0.2 rounded font-mono text-[10px] bg-slate-800 text-white font-bold">1928</span>
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white">積水ハウス</span>
                <span className="text-[9px] text-[#bbcabf]">1月・7月決算（4月・10月受取）</span>
              </div>
              <span className="text-[11px] text-[#4edea3] font-mono font-bold ml-1">4.12%</span>
            </div>
            <button
              type="button"
              onClick={() => showToast('積水ハウス (1928) をウォッチリストに追加しました！', 'bookmark_add')}
              className="px-2.5 py-1.5 rounded-lg bg-[#10b981] text-[#003824] text-[10px] font-bold flex items-center gap-1 active:scale-95 transition-all shadow-sm shrink-0"
            >
              <span className="material-symbols-outlined text-[13px]">playlist_add</span>
              <span>空白月解消に追加</span>
            </button>
          </div>
        </div>

        {/* Close Button */}
        <div className="flex flex-col gap-2 pt-1 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="w-full h-10 bg-slate-800 hover:bg-slate-700 text-[#dae2fd] rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 active:scale-[0.98] transition-all cursor-pointer border border-slate-700/60"
          >
            <span className="material-symbols-outlined text-[16px]">close</span>
            シミュレーターを閉じる
          </button>
        </div>
      </div>
    </div>
  );
};
