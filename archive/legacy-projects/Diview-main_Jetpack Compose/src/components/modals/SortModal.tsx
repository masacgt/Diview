import React from 'react';
import { SortCriteria, SortDirection } from '../../types';

interface SortModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentCriteria: SortCriteria;
  currentDirection: SortDirection;
  onApply: (criteria: SortCriteria, direction: SortDirection) => void;
}

export const SortModal: React.FC<SortModalProps> = ({
  isOpen,
  onClose,
  currentCriteria,
  currentDirection,
  onApply
}) => {
  const [selectedCriteria, setSelectedCriteria] = React.useState<SortCriteria>(currentCriteria);
  const [direction, setDirection] = React.useState<SortDirection>(currentDirection);

  React.useEffect(() => {
    setSelectedCriteria(currentCriteria);
    setDirection(currentDirection);
  }, [currentCriteria, currentDirection]);

  if (!isOpen) return null;

  const criteriaOptions: { id: SortCriteria; label: string; icon: string }[] = [
    { id: 'val', label: '評価額 (保有資産ウェイト)', icon: 'account_balance' },
    { id: 'gain', label: '評価損益率 (%)', icon: 'trending_up' },
    { id: 'div', label: '配当利回り (高配当順)', icon: 'percent' },
    { id: 'div_amt', label: '予想年間受取配当額 (受取貢献度)', icon: 'payments' },
    { id: 'code', label: '銘柄コード / ティッカー順', icon: 'pin' }
  ];

  const handleApply = () => {
    onApply(selectedCriteria, direction);
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 backdrop-blur-md flex flex-col justify-end p-0"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg mx-auto bg-[#131b2e] rounded-t-3xl p-4 flex flex-col gap-3.5 shadow-2xl border-t border-slate-700/80"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto -mt-1 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">tune</span>
            <h3 className="font-bold text-sm text-white">並び替え条件の選択</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-[#171f33] text-[#bbcabf] hover:text-white flex items-center justify-center"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Direction Segment Switcher */}
        <div className="flex flex-col gap-1.5">
          <span className="text-[11px] font-semibold text-[#bbcabf]">順序</span>
          <div className="grid grid-cols-2 p-1 rounded-xl bg-[#0b1326] border border-slate-800">
            <button
              type="button"
              onClick={() => setDirection('desc')}
              className={`h-9 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1 ${
                direction === 'desc'
                  ? 'bg-[#10b981] text-[#003824] shadow-sm'
                  : 'text-[#bbcabf] hover:text-white'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">arrow_downward</span>
              <span>降順 (大きい順)</span>
            </button>
            <button
              type="button"
              onClick={() => setDirection('asc')}
              className={`h-9 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1 ${
                direction === 'asc'
                  ? 'bg-[#10b981] text-[#003824] shadow-sm'
                  : 'text-[#bbcabf] hover:text-white'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">arrow_upward</span>
              <span>昇順 (小さい順)</span>
            </button>
          </div>
        </div>

        {/* Criteria List */}
        <div className="flex flex-col gap-1.5">
          <span className="text-[11px] font-semibold text-[#bbcabf]">ソート項目</span>
          {criteriaOptions.map(opt => {
            const isSelected = selectedCriteria === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                onClick={() => setSelectedCriteria(opt.id)}
                className={`p-3 rounded-xl flex items-center justify-between text-xs font-semibold transition-all ${
                  isSelected
                    ? 'bg-[#171f33] text-white border border-[#4edea3]/50 shadow-sm'
                    : 'bg-[#0b1326] text-[#bbcabf] hover:text-white border border-slate-800'
                }`}
              >
                <div className="flex items-center gap-2">
                  <span className={`material-symbols-outlined text-[18px] ${isSelected ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                    {opt.icon}
                  </span>
                  <span>{opt.label}</span>
                </div>
                {isSelected && (
                  <span className="material-symbols-outlined text-[#4edea3] text-[18px]">
                    check_circle
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* Action Buttons */}
        <div className="flex gap-2 pt-1">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
          >
            キャンセル
          </button>
          <button
            type="button"
            onClick={handleApply}
            className="flex-1 h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs shadow-lg shadow-emerald-500/20 active:scale-98"
          >
            適用する
          </button>
        </div>
      </div>
    </div>
  );
};
