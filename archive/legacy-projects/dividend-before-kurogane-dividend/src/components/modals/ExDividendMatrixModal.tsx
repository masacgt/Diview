import React from 'react';

interface ExDividendMatrixModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ExDividendMatrixModal: React.FC<ExDividendMatrixModalProps> = ({ isOpen, onClose }) => {
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
        {/* Drag Handle */}
        <div className="w-12 h-1 bg-slate-700/90 rounded-full mx-auto mb-2 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-2.5 border-b border-slate-800 shrink-0">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-[#10b981]/15 border border-[#10b981]/30 flex items-center justify-center text-[#4edea3] shrink-0">
              <span className="material-symbols-outlined text-[20px]">calendar_month</span>
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <h2 className="text-sm font-bold text-white tracking-tight">権利落ち月マトリクス</h2>
                <span className="px-1.5 py-0.2 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[9px] font-bold">
                  ヒートマップ分析
                </span>
              </div>
              <p className="text-[10px] text-[#bbcabf]">監視4銘柄の想定権利落ちスケジュールと配当シミュレーション</p>
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

        {/* Top 3 KPIs */}
        <div className="grid grid-cols-3 gap-2 my-2.5 shrink-0">
          <div className="flex flex-col bg-[#0b1326]/85 rounded-xl p-2.5 border border-slate-800/90">
            <span className="text-[9px] text-[#bbcabf] font-medium flex items-center gap-0.5">
              <span className="material-symbols-outlined text-[11px] text-[#4edea3]">event_repeat</span>
              想定配当回数
            </span>
            <div className="flex items-baseline gap-0.5 mt-1">
              <span className="font-mono text-lg font-bold text-white">10</span>
              <span className="text-[10px] text-[#bbcabf] font-bold">回/年</span>
            </div>
            <span className="text-[8px] text-[#4edea3] mt-0.5 font-medium">日6回 + 米4回 (想定)</span>
          </div>

          <div className="flex flex-col bg-[#0b1326]/85 rounded-xl p-2.5 border border-[#4edea3]/30 relative overflow-hidden bg-[#10b981]/5">
            <div className="absolute top-0 right-0 w-2.5 h-2.5 bg-[#4edea3]/40 rounded-bl" />
            <span className="text-[9px] text-[#4edea3] font-medium flex items-center gap-0.5">
              <span className="material-symbols-outlined text-[11px]">schedule</span>次回権利落ち
            </span>
            <div className="flex items-baseline gap-1 mt-1">
              <span className="font-mono text-sm font-bold text-[#4edea3]">3月27日</span>
            </div>
            <span className="text-[8px] text-[#4edea3]/90 mt-0.5 font-bold">あと42日 (買付検討期)</span>
          </div>

          <div className="flex flex-col bg-[#0b1326]/85 rounded-xl p-2.5 border border-slate-800/90">
            <span className="text-[9px] text-[#bbcabf] font-medium flex items-center gap-0.5">
              <span className="material-symbols-outlined text-[11px] text-indigo-300">payments</span>
              潜在配当 (各1単元)
            </span>
            <div className="flex items-baseline gap-0.5 mt-1">
              <span className="text-[10px] text-white font-bold">¥</span>
              <span className="font-mono text-sm font-bold text-white">42,800</span>
            </div>
            <span className="text-[8px] text-[#bbcabf] mt-0.5 font-mono">想定利回り 3.84%</span>
          </div>
        </div>

        {/* Heatmap Legend */}
        <div className="flex items-center justify-between mb-1.5 px-0.5 shrink-0">
          <div className="flex items-center gap-1.5 text-xs text-white font-bold">
            <span className="material-symbols-outlined text-[#4edea3] text-[16px]">grid_view</span>
            <span>12ヶ月配当密度ヒートマップマトリクス</span>
          </div>
          <div className="flex items-center gap-1 text-[8.5px] text-[#bbcabf]">
            <span>少</span>
            <span className="w-2.5 h-2.5 rounded-sm bg-slate-800/80 inline-block border border-slate-700" />
            <span className="w-2.5 h-2.5 rounded-sm bg-indigo-500/30 inline-block" />
            <span className="w-2.5 h-2.5 rounded-sm bg-[#10b981]/40 inline-block" />
            <span className="w-2.5 h-2.5 rounded-sm bg-[#10b981] inline-block" />
            <span>多</span>
          </div>
        </div>

        {/* Heatmap Matrix Table */}
        <div className="bg-[#0b1326]/95 rounded-2xl border border-slate-800/90 overflow-hidden mb-3 shadow-xl shrink-0">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-center border-collapse">
              <thead>
                <tr className="bg-[#171f33] border-b border-slate-800 text-[10px] text-[#bbcabf] font-mono">
                  <th className="p-2 text-left font-semibold sticky left-0 bg-[#171f33] z-20 min-w-[76px] border-r border-slate-800/60">銘柄</th>
                  <th className="py-2 px-1 font-semibold">1月</th>
                  <th className="py-2 px-1 font-semibold">2月</th>
                  <th className="py-2 px-1 font-bold text-indigo-300 bg-indigo-500/10">3月<span className="block text-[7.5px] text-indigo-300 font-normal">権利集中</span></th>
                  <th className="py-2 px-1 font-semibold">4月</th>
                  <th className="py-2 px-1 font-semibold">5月</th>
                  <th className="py-2 px-1 font-bold text-[#4edea3] bg-[#10b981]/10">6月<span className="block text-[7.5px] text-[#4edea3] font-normal">入金集中</span></th>
                  <th className="py-2 px-1 font-semibold">7月</th>
                  <th className="py-2 px-1 font-semibold">8月</th>
                  <th className="py-2 px-1 font-bold text-indigo-300 bg-indigo-500/10">9月<span className="block text-[7.5px] text-indigo-300 font-normal">権利集中</span></th>
                  <th className="py-2 px-1 font-semibold">10月</th>
                  <th className="py-2 px-1 font-semibold">11月</th>
                  <th className="py-2 px-1 font-bold text-[#4edea3] bg-[#10b981]/10">12月<span className="block text-[7.5px] text-[#4edea3] font-normal">入金集中</span></th>
                </tr>
              </thead>
              <tbody className="text-[9px] divide-y divide-slate-800/80">
                {/* 1. 三井住友FG */}
                <tr className="hover:bg-slate-800/40 transition-colors">
                  <td className="p-2 text-left font-bold text-white sticky left-0 bg-[#0b1326] z-10 border-r border-slate-800/60 truncate max-w-[80px]">
                    <span className="block font-mono text-[8px] text-[#4edea3] font-bold">8316</span>
                    <span className="text-[10px]">三井住友</span>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/15">
                    <div className="px-1 py-1 rounded bg-indigo-500/25 border border-indigo-400/40 text-indigo-300 font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-normal">権利落</span>¥6,500
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-[#10b981]/15">
                    <div className="px-1 py-1 rounded bg-gradient-to-br from-[#10b981] to-[#4edea3] text-[#003824] font-mono font-bold shadow-sm leading-tight">
                      <span className="text-[7.5px] block font-semibold">入金</span>¥6,500
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/15">
                    <div className="px-1 py-1 rounded bg-indigo-500/25 border border-indigo-400/40 text-indigo-300 font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-normal">権利落</span>¥6,500
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-[#10b981]/15">
                    <div className="px-1 py-1 rounded bg-gradient-to-br from-[#10b981] to-[#4edea3] text-[#003824] font-mono font-bold shadow-sm leading-tight">
                      <span className="text-[7.5px] block font-semibold">入金</span>¥6,500
                    </div>
                  </td>
                </tr>

                {/* 2. トヨタ自動車 */}
                <tr className="hover:bg-slate-800/40 transition-colors">
                  <td className="p-2 text-left font-bold text-white sticky left-0 bg-[#0b1326] z-10 border-r border-slate-800/60 truncate max-w-[80px]">
                    <span className="block font-mono text-[8px] text-slate-300 font-bold">7203</span>
                    <span className="text-[10px]">トヨタ</span>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/15">
                    <div className="px-1 py-1 rounded bg-indigo-500/20 border border-indigo-400/30 text-indigo-300 font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-normal">権利落</span>¥3,640
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-[#10b981]/15">
                    <div className="px-1 py-1 rounded bg-[#4edea3] text-[#003824] font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-semibold">入金</span>¥3,640
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/15">
                    <div className="px-1 py-1 rounded bg-indigo-500/20 border border-indigo-400/30 text-indigo-300 font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-normal">権利落</span>¥3,640
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-[#10b981]/15">
                    <div className="px-1 py-1 rounded bg-[#4edea3] text-[#003824] font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-semibold">入金</span>¥3,640
                    </div>
                  </td>
                </tr>

                {/* 3. NTT */}
                <tr className="hover:bg-slate-800/40 transition-colors">
                  <td className="p-2 text-left font-bold text-white sticky left-0 bg-[#0b1326] z-10 border-r border-slate-800/60 truncate max-w-[80px]">
                    <span className="block font-mono text-[8px] text-slate-300 font-bold">9432</span>
                    <span className="text-[10px]">NTT</span>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/15">
                    <div className="px-1 py-1 rounded bg-indigo-500/20 border border-indigo-400/30 text-indigo-300 font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-normal">権利落</span>¥2,600
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-[#10b981]/15">
                    <div className="px-1 py-1 rounded bg-[#4edea3] text-[#003824] font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-semibold">入金</span>¥2,600
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/15">
                    <div className="px-1 py-1 rounded bg-indigo-500/20 border border-indigo-400/30 text-indigo-300 font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-normal">権利落</span>¥2,600
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-[#10b981]/15">
                    <div className="px-1 py-1 rounded bg-[#4edea3] text-[#003824] font-mono font-bold leading-tight">
                      <span className="text-[7.5px] block font-semibold">入金</span>¥2,600
                    </div>
                  </td>
                </tr>

                {/* 4. Apple Inc. */}
                <tr className="hover:bg-slate-800/40 transition-colors bg-indigo-950/20">
                  <td className="p-2 text-left font-bold text-white sticky left-0 bg-[#0b1326] z-10 border-r border-slate-800/60 truncate max-w-[80px]">
                    <span className="block font-mono text-[8px] text-indigo-300 font-bold">AAPL</span>
                    <span className="text-[10px]">Apple</span>
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/20">
                    <div className="px-1 py-1 rounded bg-indigo-400 text-[#0b1326] font-mono font-bold shadow-sm leading-tight">
                      <span className="text-[7.5px] block font-semibold">権/入</span>$2.5
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono bg-indigo-500/10">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/20">
                    <div className="px-1 py-1 rounded bg-indigo-400 text-[#0b1326] font-mono font-bold shadow-sm leading-tight">
                      <span className="text-[7.5px] block font-semibold">権/入</span>$2.5
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono bg-[#10b981]/10">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/20">
                    <div className="px-1 py-1 rounded bg-indigo-400 text-[#0b1326] font-mono font-bold shadow-sm leading-tight">
                      <span className="text-[7.5px] block font-semibold">権/入</span>$2.5
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono bg-indigo-500/10">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 bg-indigo-500/20">
                    <div className="px-1 py-1 rounded bg-indigo-400 text-[#0b1326] font-mono font-bold shadow-sm leading-tight">
                      <span className="text-[7.5px] block font-semibold">権/入</span>$2.5
                    </div>
                  </td>
                  <td className="text-slate-500 font-mono bg-[#10b981]/10">-</td>
                </tr>

                {/* Total Row */}
                <tr className="bg-[#171f33]/90 font-bold border-t-2 border-slate-700/80">
                  <td className="p-2 text-left sticky left-0 bg-[#171f33] z-10 border-r border-slate-800/80 text-[10px] text-white">
                    想定受取計
                  </td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 font-mono text-indigo-300 text-[8.5px]">¥385</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 font-mono text-indigo-300 text-[8.5px]">¥385</td>
                  <td className="p-1 font-mono text-[#4edea3] font-bold text-[9px] bg-[#10b981]/20 rounded">¥12,740</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 font-mono text-indigo-300 text-[8.5px]">¥385</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="text-slate-500 font-mono">-</td>
                  <td className="p-1 font-mono text-indigo-300 text-[8.5px]">¥385</td>
                  <td className="p-1 font-mono text-[#4edea3] font-bold text-[9px] bg-[#10b981]/20 rounded">¥12,740</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        {/* Analytics Card */}
        <div className="bg-gradient-to-br from-[#102436] via-[#131b2e] to-[#0d1629] rounded-2xl p-3 border border-indigo-400/30 mb-3 space-y-2 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5 text-xs font-bold text-white">
              <span className="material-symbols-outlined text-indigo-300 text-[16px]">balance</span>
              <span>権利落ち・受取平準化アナリティクス</span>
            </div>
            <span className="text-[9px] bg-indigo-500/20 text-indigo-300 border border-indigo-400/30 px-2 py-0.5 rounded-full font-bold">
              偏重度: 82%
            </span>
          </div>
          <p className="text-[10px] text-[#bbcabf] leading-relaxed">
            監視中の日本株3銘柄は <strong className="text-white">3月・9月末</strong> に権利落ちが集中しており、実際の配当受取は <strong className="text-[#4edea3] font-bold">6月・12月</strong> に偏ります。米国株（AAPL）をポートフォリオに組み入れることで <strong className="text-indigo-300 font-bold">2/5/8/11月</strong> のインカム空白月を補完できます。
          </p>
          <div className="grid grid-cols-2 gap-2 pt-1 border-t border-slate-700/60">
            <div className="flex items-center gap-1.5 text-[10px]">
              <span className="w-2 h-2 rounded-full bg-[#4edea3] shrink-0" />
              <span className="text-[#bbcabf]">日株 6/12月想定計:</span>
              <strong className="text-[#4edea3] font-mono font-bold">¥25,480</strong>
            </div>
            <div className="flex items-center gap-1.5 text-[10px]">
              <span className="w-2 h-2 rounded-full bg-indigo-300 shrink-0" />
              <span className="text-[#bbcabf]">米株 想定年間計:</span>
              <strong className="text-indigo-300 font-mono font-bold">¥1,540</strong>
            </div>
          </div>
        </div>

        {/* Footer Close Button */}
        <div className="flex flex-col gap-2 pt-1 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="w-full h-10 bg-slate-800 hover:bg-slate-700 text-[#dae2fd] rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 active:scale-[0.98] transition-all cursor-pointer border border-slate-700/60"
          >
            <span className="material-symbols-outlined text-[16px]">close</span>
            マトリクスを閉じる
          </button>
        </div>
      </div>
    </div>
  );
};
