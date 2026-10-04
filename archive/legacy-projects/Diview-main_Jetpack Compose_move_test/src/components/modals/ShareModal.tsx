import React, { useState } from 'react';
import { useApp } from '../../context/AppContext';

interface ShareModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ShareModal: React.FC<ShareModalProps> = ({ isOpen, onClose }) => {
  const { showToast } = useApp();
  const [activeTab, setActiveTab] = useState<'progress' | 'cert'>('progress');
  const [isMasked, setIsMasked] = useState(false);

  if (!isOpen) return null;

  const currentMonthly = isMasked ? '¥ ••••••••' : '¥31,208';
  const currentAnnual = isMasked ? '¥ ••••••••' : '¥374,500';

  const handleShareX = () => {
    const valText = isMasked ? '非公開' : '月¥31,208';
    const text = encodeURIComponent(
      `【不労所得ロードマップ Quest】\n現在、配当金で ${valText} を自給中！\nLEVEL 1を完全制覇し、現在 STEP 6「食費・日用品の全額自給 (月5万円)」を攻略中 🛒✨\n#配当管理 #高配当株 #FIRE #RoadmapQuest`
    );
    window.open(`https://twitter.com/intent/tweet?text=${text}`, '_blank');
  };

  const handleCopy = () => {
    const text = `配当金ロードマップQuest進捗: 現在月31,208円達成 (STEP 6: 食費・日用品自給 62.4%達成)`;
    navigator.clipboard?.writeText(text);
    showToast('テキストをクリップボードにコピーしました');
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/80 backdrop-blur-md p-0"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg bg-[#131b2e] rounded-t-3xl p-4 flex flex-col gap-3.5 max-h-[85vh] overflow-y-auto no-scrollbar shadow-2xl border-t border-slate-700/80"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto -mt-1 shrink-0" />

        {/* Modal Header */}
        <div className="flex items-center justify-between pb-1 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-[#10b981]/20 flex items-center justify-center text-[#4edea3]">
              <span className="material-symbols-outlined text-[18px]">share</span>
            </div>
            <div>
              <span className="font-bold text-sm text-white block">達成状況をシェア</span>
              <span className="text-[10px] text-[#bbcabf]">SNSや画像で進捗を共有できます</span>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-[#171f33] flex items-center justify-center text-[#bbcabf] hover:text-white"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Tab Switcher: Card vs Certificate */}
        <div className="grid grid-cols-2 p-1 rounded-xl bg-[#0b1326] border border-slate-800">
          <button
            type="button"
            onClick={() => setActiveTab('progress')}
            className={`py-2 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'progress'
                ? 'bg-[#171f33] text-[#4edea3] shadow-sm'
                : 'text-[#bbcabf] hover:text-white'
            }`}
          >
            <span className="material-symbols-outlined text-[16px]">shopping_cart</span>
            <span>QUEST 6 進捗カード</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('cert')}
            className={`py-2 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'cert'
                ? 'bg-[#171f33] text-[#4edea3] shadow-sm'
                : 'text-[#bbcabf] hover:text-white'
            }`}
          >
            <span className="material-symbols-outlined text-[16px]">military_tech</span>
            <span>LV.1 踏破認定証</span>
          </button>
        </div>

        {/* Mask Privacy Toggle */}
        <div className="flex items-center justify-between px-3 py-2.5 rounded-xl bg-[#0b1326] border border-slate-800">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[18px] text-[#bbcabf]">
              {isMasked ? 'visibility_off' : 'visibility'}
            </span>
            <span className="text-xs text-white font-medium">金額を伏字にする</span>
          </div>
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={isMasked}
              onChange={e => setIsMasked(e.target.value === 'true' || e.target.checked)}
              className="sr-only peer"
            />
            <div className="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#10b981]" />
          </label>
        </div>

        {/* Preview: Progress Card */}
        {activeTab === 'progress' && (
          <div className="relative w-full rounded-2xl bg-gradient-to-br from-[#171f33] to-[#0d1629] p-4 shadow-2xl flex flex-col gap-3 overflow-hidden border border-slate-800">
            <div className="absolute -right-8 -top-8 w-32 h-32 bg-[#10b981]/15 rounded-full blur-xl pointer-events-none" />
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[#4edea3] text-[20px]">sports_esports</span>
                <span className="font-bold text-sm text-white">Roadmap Quest</span>
              </div>
              <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold">
                LEVEL 2 • STEP 6 挑戦中
              </span>
            </div>

            <div className="flex flex-col gap-0.5 my-1">
              <span className="text-[10px] text-[#bbcabf]">現在の月間配当 (受取実績換算)</span>
              <div className="flex items-baseline gap-2">
                <span className="font-mono text-2xl font-bold text-[#4edea3]">{currentMonthly}</span>
                <span className="text-xs text-[#bbcabf]">/ 月</span>
              </div>
              <span className="text-[11px] text-[#bbcabf]">
                年間予想: <strong className="text-white">{currentAnnual}</strong>
              </span>
            </div>

            <div className="flex flex-col gap-1.5 p-2.5 rounded-xl bg-[#0b1326]/80 border border-slate-800">
              <div className="flex justify-between items-center text-xs">
                <span className="text-white flex items-center gap-1">
                  目標: 月50,000円 (食費・日用品の自給 <span className="material-symbols-outlined text-[14px] text-[#4edea3]">shopping_cart</span>)
                </span>
                <span className="text-[#4edea3] font-bold">62.4% 達成</span>
              </div>
              <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
                <div className="h-full bg-[#10b981] rounded-full" style={{ width: '62.4%' }} />
              </div>
            </div>

            <div className="flex items-center justify-between pt-1 text-[#bbcabf] text-[10px]">
              <span>#高配当株投資 #配当金生活 #FIRE</span>
              <span>Roadmap Quest</span>
            </div>
          </div>
        )}

        {/* Preview: Certificate */}
        {activeTab === 'cert' && (
          <div className="relative w-full rounded-2xl bg-gradient-to-br from-[#171f33] via-[#102436] to-[#0b1326] p-4 shadow-2xl flex flex-col items-center text-center gap-3 overflow-hidden border border-[#4edea3]/30">
            <div className="w-14 h-14 rounded-2xl bg-[#10b981]/20 text-[#4edea3] flex items-center justify-center mt-1 shadow-[0_0_20px_rgba(78,222,163,0.3)]">
              <span className="material-symbols-outlined text-[34px]">workspace_premium</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-[10px] text-[#4edea3] tracking-widest uppercase font-bold">
                LEVEL 1 COMPLETE
              </span>
              <h4 className="font-bold text-base text-white">生活インフラ自給期 踏破認定証</h4>
              <p className="text-xs text-[#bbcabf]">STEP 1〜5 (月3万円・年36万円) を完全達成</p>
            </div>
            <div className="p-3 rounded-xl bg-[#0b1326]/90 w-full flex flex-col gap-1 border border-slate-800">
              <span className="text-[10px] text-[#bbcabf]">獲得した生活権益</span>
              <span className="font-bold text-xs text-[#4edea3] flex items-center justify-center gap-1">
                水道光熱費・通信費の全額自給権
                <span className="material-symbols-outlined text-[15px]">bolt</span>
                <span className="material-symbols-outlined text-[15px]">smartphone</span>
              </span>
              <span className="text-[11px] text-[#bbcabf] mt-1">
                到達実績: <strong className="text-white">{currentMonthly}</strong> / 月
              </span>
            </div>
            <div className="flex items-center justify-between w-full pt-1 text-[#bbcabf] text-[10px]">
              <span>認定ランク: LEVEL 1 MASTER</span>
              <span>Roadmap Quest</span>
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex flex-col gap-2 pt-1">
          <button
            type="button"
            onClick={handleShareX}
            className="w-full h-11 rounded-xl bg-white hover:bg-slate-200 text-slate-900 font-bold text-xs flex items-center justify-center gap-2 active:scale-95 shadow-md"
          >
            <span className="font-bold text-sm">𝕏</span>
            <span>𝕏 (Twitter) でクエスト進捗をシェア</span>
          </button>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => showToast('クエスト達成カードを保存しました', 'download_done')}
              className="h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold flex items-center justify-center gap-1 border border-slate-700/60"
            >
              <span className="material-symbols-outlined text-[16px] text-[#4edea3]">download</span>
              <span>画像で保存</span>
            </button>
            <button
              type="button"
              onClick={handleCopy}
              className="h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold flex items-center justify-center gap-1 border border-slate-700/60"
            >
              <span className="material-symbols-outlined text-[16px] text-[#4edea3]">content_copy</span>
              <span>テキストをコピー</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
