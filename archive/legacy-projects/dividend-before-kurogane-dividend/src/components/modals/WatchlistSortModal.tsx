import React from 'react';

export type WatchlistSortCriteria = 'ach' | 'yield' | 'price' | 'code';
export type WatchlistSortDirection = 'asc' | 'desc';

interface WatchlistSortModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentCriteria: WatchlistSortCriteria;
  currentDirection: WatchlistSortDirection;
  onApply: (criteria: WatchlistSortCriteria, direction: WatchlistSortDirection) => void;
}

export const WatchlistSortModal: React.FC<WatchlistSortModalProps> = ({
  isOpen,
  onClose,
  currentCriteria,
  currentDirection,
  onApply
}) => {
  const [selectedCriteria, setSelectedCriteria] = React.useState<WatchlistSortCriteria>(currentCriteria);
  const [direction, setDirection] = React.useState<WatchlistSortDirection>(currentDirection);

  React.useEffect(() => {
    setSelectedCriteria(currentCriteria);
    setDirection(currentDirection);
  }, [currentCriteria, currentDirection]);

  if (!isOpen) return null;

  const criteriaOptions: { id: WatchlistSortCriteria; label: string; icon: string }[] = [
    { id: 'ach', label: '利回り達成率 (目安到達に近い順)', icon: 'bolt' },
    { id: 'yield', label: '現在利回り (高配当順)', icon: 'percent' },
    { id: 'price', label: '株価 (購入必要額基準)', icon: 'monetization_on' },
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
        className="w-full max-w-lg mx-auto bg-[#131b2e] rounded-t-3xl p-4 flex flex-col gap-3.5 shadow-2xl border-t border-slate-700/80 animate-in slide-in-from-bottom duration-250 ease-out"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto -mt-1 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">tune</span>
            <h3 className="font-bold text-sm text-white">監視銘柄の並び替え条件</h3>
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
              className={`h-9 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1 cursor-pointer ${
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
              className={`h-9 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1 cursor-pointer ${
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
                className={`p-3 rounded-xl flex items-center justify-between text-xs font-semibold transition-all cursor-pointer ${
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
            className="flex-1 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold cursor-pointer"
          >
            キャンセル
          </button>
          <button
            type="button"
            onClick={handleApply}
            className="flex-1 h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs shadow-lg shadow-emerald-500/20 active:scale-98 cursor-pointer"
          >
            適用する
          </button>
        </div>
      </div>
    </div>
  );
};
