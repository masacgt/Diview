import React, { useState } from 'react';
import { useApp } from '../../context/AppContext';

export const SearchModal: React.FC = () => {
  const { isSearchOpen, closeSearch, navigateTo } = useApp();
  const [searchQuery, setSearchQuery] = useState('');
  const [category, setCategory] = useState<'all' | 'jp' | 'us' | 'fund' | 'high-div'>('all');

  if (!isSearchOpen) return null;

  const candidateList = [
    { code: '7203', name: 'トヨタ自動車', desc: '東証P • 輸送用機器', price: '¥2,600', yieldRate: '2.80%', cat: 'jp', isHigh: false },
    { code: '8316', name: '三井住友FG', desc: '東証P • 銀行業', price: '¥3,150', yieldRate: '4.13%', cat: 'jp', isHigh: true },
    { code: '9432', name: '日本電信電話 (NTT)', desc: '東証P • 情報通信', price: '¥154.2', yieldRate: '3.37%', cat: 'jp', isHigh: true },
    { code: '8058', name: '三菱商事', desc: '東証P • 卸売業', price: '¥3,240', yieldRate: '3.52%', cat: 'jp', isHigh: true },
    { code: 'AAPL', name: 'Apple Inc.', desc: 'NASDAQ • 米国株', price: '$220.00', yieldRate: '0.52%', cat: 'us', isHigh: false },
    { code: 'MSFT', name: 'Microsoft Corp.', desc: 'NASDAQ • ソフトウェア', price: '$440.00', yieldRate: '0.76%', cat: 'us', isHigh: false },
    { code: 'eMAXIS', name: 'eMAXIS Slim 全世界株式', desc: 'オルカン • 三菱UFJ', price: '¥24,460', yieldRate: '再投資型', cat: 'fund', isHigh: false }
  ];

  const filteredCandidates = candidateList.filter(item => {
    const q = searchQuery.toLowerCase().trim();
    const matchesQuery = !q || item.code.toLowerCase().includes(q) || item.name.toLowerCase().includes(q);
    const matchesCat =
      category === 'all' ||
      (category === 'high-div' ? item.isHigh : item.cat === category);
    return matchesQuery && matchesCat;
  });

  const handleSelect = (code: string) => {
    closeSearch();
    navigateTo('stock-detail', code);
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 backdrop-blur-md flex flex-col justify-end p-0"
      onClick={closeSearch}
    >
      <div
        className="w-full max-w-lg mx-auto bg-[#131b2e] border-t border-slate-700/80 rounded-t-3xl p-4 flex flex-col gap-3.5 shadow-2xl max-h-[85vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        {/* Grab Handle */}
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto -mt-1 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-[#171f33] border border-slate-700/60 flex items-center justify-center text-[#4edea3]">
              <span className="material-symbols-outlined text-[20px]">search</span>
            </div>
            <div>
              <h3 className="font-bold text-sm text-white">銘柄検索・絞り込み</h3>
              <p className="text-[10px] text-[#bbcabf]">保有銘柄の絞り込みまたは詳細分析を開く</p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeSearch}
            className="w-8 h-8 rounded-full bg-[#171f33] text-[#bbcabf] hover:text-white flex items-center justify-center"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Search Input Box */}
        <div className="relative flex items-center">
          <span className="absolute left-3 material-symbols-outlined text-[20px] text-[#bbcabf] pointer-events-none">
            search
          </span>
          <input
            type="text"
            autoFocus
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="銘柄名・コード・ティッカー (例: 7203, トヨタ, AAPL)"
            className="w-full h-11 pl-10 pr-9 rounded-xl bg-[#0b1326] border border-slate-700/80 text-white text-xs placeholder:text-[#bbcabf]/50 focus:outline-none focus:border-[#4edea3] transition-colors"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              className="absolute right-2.5 w-6 h-6 rounded-full bg-slate-800 text-[#bbcabf] hover:text-white flex items-center justify-center"
            >
              <span className="material-symbols-outlined text-[14px]">close</span>
            </button>
          )}
        </div>

        {/* Category Filter Chips */}
        <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
          <button
            type="button"
            onClick={() => setCategory('all')}
            className={`px-3 py-1 rounded-full text-[11px] font-bold shrink-0 transition-all ${
              category === 'all'
                ? 'bg-[#10b981] text-[#003824]'
                : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
            }`}
          >
            すべて
          </button>
          <button
            type="button"
            onClick={() => setCategory('jp')}
            className={`px-3 py-1 rounded-full text-[11px] font-semibold shrink-0 transition-all ${
              category === 'jp'
                ? 'bg-[#10b981] text-[#003824]'
                : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
            }`}
          >
            日本株
          </button>
          <button
            type="button"
            onClick={() => setCategory('us')}
            className={`px-3 py-1 rounded-full text-[11px] font-semibold shrink-0 transition-all ${
              category === 'us'
                ? 'bg-[#10b981] text-[#003824]'
                : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
            }`}
          >
            米国株
          </button>
          <button
            type="button"
            onClick={() => setCategory('fund')}
            className={`px-3 py-1 rounded-full text-[11px] font-semibold shrink-0 transition-all ${
              category === 'fund'
                ? 'bg-[#10b981] text-[#003824]'
                : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
            }`}
          >
            投信
          </button>
          <button
            type="button"
            onClick={() => setCategory('high-div')}
            className={`px-3 py-1 rounded-full text-[11px] font-semibold shrink-0 transition-all ${
              category === 'high-div'
                ? 'bg-[#10b981] text-[#003824]'
                : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
            }`}
          >
            高配当 (3%以上)
          </button>
        </div>

        {/* Quick Hot Search Tags */}
        <div className="flex flex-col gap-1.5">
          <span className="text-[10px] text-[#bbcabf] font-semibold flex items-center gap-1">
            <span className="material-symbols-outlined text-[13px] text-[#4edea3]">local_fire_department</span>
            注目の高配当・主力銘柄
          </span>
          <div className="flex flex-wrap gap-1.5">
            {['7203 トヨタ', '8316 三井住友FG', '9432 NTT', '8058 三菱商事', 'AAPL Apple'].map(tag => (
              <button
                key={tag}
                type="button"
                onClick={() => setSearchQuery(tag.split(' ')[0])}
                className="px-2.5 py-1 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-[11px] font-mono flex items-center gap-1 transition-colors"
              >
                <span className="font-bold text-[#4edea3]">{tag.split(' ')[0]}</span>
                <span>{tag.split(' ')[1]}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Results List */}
        <div className="flex flex-col gap-1.5 pt-1">
          <div className="flex items-center justify-between text-[11px] text-[#bbcabf]">
            <span>候補銘柄 ({filteredCandidates.length}件)</span>
            <span className="text-[#4edea3] text-[10px]">タップで銘柄詳細を表示</span>
          </div>
          <div className="flex flex-col gap-2 max-h-56 overflow-y-auto pr-0.5">
            {filteredCandidates.map(item => (
              <div
                key={item.code}
                onClick={() => handleSelect(item.code)}
                className="p-2.5 rounded-xl bg-[#0b1326] hover:bg-[#171f33] border border-slate-800 hover:border-[#4edea3]/40 flex items-center justify-between cursor-pointer transition-colors"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <span className="px-2 py-0.5 rounded bg-slate-800 font-mono text-[11px] text-white font-bold">
                    {item.code}
                  </span>
                  <div className="truncate">
                    <p className="font-bold text-xs text-white truncate">{item.name}</p>
                    <p className="text-[10px] text-[#bbcabf] truncate">{item.desc}</p>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <span className="block font-mono font-bold text-xs text-white">{item.price}</span>
                  <span className="text-[10px] text-[#4edea3] font-bold">
                    {item.yieldRate.includes('%') ? `利回り ${item.yieldRate}` : item.yieldRate}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom Action */}
        <div className="flex gap-2 pt-1">
          <button
            type="button"
            onClick={closeSearch}
            className="flex-1 h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
          >
            閉じる
          </button>
        </div>
      </div>
    </div>
  );
};
