import React, { useEffect, useMemo, useState } from 'react';
import { useApp } from '../../context/AppContext';
import { getSearchCatalog, searchCatalog, SearchCatalogItem } from './searchCatalog';

export const SearchModal: React.FC = () => {
  const { isSearchOpen, closeSearch, navigateTo, holdings, showToast } = useApp();
  const [searchQuery, setSearchQuery] = useState('');
  const [category, setCategory] = useState<'all' | 'jp' | 'us' | 'fund' | 'high-div'>('all');
  const [catalog, setCatalog] = useState<SearchCatalogItem[]>([]);
  const [catalogError, setCatalogError] = useState(false);
  const popular = useMemo(() => [...catalog]
    .filter(item => item.estimated_average_trading_value_20d != null)
    .sort((a, b) => (b.estimated_average_trading_value_20d ?? 0) - (a.estimated_average_trading_value_20d ?? 0))
    .slice(0, 5), [catalog]);

  useEffect(() => {
    if (!isSearchOpen) return;
    let active = true;
    getSearchCatalog().then(data => {
      if (active) { setCatalog(data.items); setCatalogError(false); }
    }).catch(() => { if (active) setCatalogError(true); });
    return () => { active = false; };
  }, [isSearchOpen]);

  if (!isSearchOpen) return null;

  const query = searchQuery.trim().toLowerCase();
  const queryLength = Array.from(query).length;
  const canSuggest = queryLength >= 2;
  const filteredCandidates = searchCatalog(catalog, query, category);

  const handleSelect = (code: string) => {
    closeSearch();
    if (!holdings.some(holding => holding.code === code)) {
      showToast('銘柄詳細は現在、保有銘柄のみ表示できます');
      return;
    }
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
              <p className="text-[10px] text-[#bbcabf]">全銘柄から検索・保有銘柄の詳細分析を開く</p>
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
            placeholder="銘柄名は2文字、コードは3文字から検索"
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

        {/* Popular securities from the latest catalog */}
        {!searchQuery && popular.length > 0 && <div className="flex flex-col gap-1.5">
          <span className="text-[10px] text-[#bbcabf] font-semibold flex items-center gap-1">
            <span className="material-symbols-outlined text-[13px] text-[#4edea3]">local_fire_department</span>
            推定売買代金の多い銘柄
          </span>
          <div className="flex flex-wrap gap-1.5">
            {popular.map(item => (
              <button
                key={item.code}
                type="button"
                onClick={() => setSearchQuery(item.code)}
                className="px-2.5 py-1 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-[11px] font-mono flex items-center gap-1 transition-colors"
              >
                <span className="font-bold text-[#4edea3]">{item.code}</span>
                <span className="truncate max-w-20">{item.name}</span>
              </button>
            ))}
          </div>
        </div>}

        {/* Results List */}
        <div className="flex flex-col gap-1.5 pt-1">
          <div className="flex items-center justify-between text-[11px] text-[#bbcabf]">
            <span>候補銘柄 ({filteredCandidates.length}件・最大20件)</span>
            <span className="text-[#4edea3] text-[10px]">保有銘柄はタップで詳細表示</span>
          </div>
          <div className="flex flex-col gap-2 max-h-56 overflow-y-auto pr-0.5">
            {!canSuggest && <p className="text-xs text-[#bbcabf] py-3">銘柄名は2文字、銘柄コードは3文字入力すると候補を表示します。</p>}
            {catalogError && <p className="text-xs text-red-300 py-3">銘柄一覧を取得できませんでした。通信状況を確認して開き直してください。</p>}
            {canSuggest && !catalogError && catalog.length === 0 && <p className="text-xs text-[#bbcabf] py-3">銘柄一覧を読み込み中です。</p>}
            {canSuggest && !catalogError && catalog.length > 0 && filteredCandidates.length === 0 && <p className="text-xs text-[#bbcabf] py-3">一致する銘柄はありません。</p>}
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
                    <p className="text-[10px] text-[#bbcabf] truncate">日本株</p>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <span className="block font-mono font-bold text-xs text-white">{item.price == null ? '株価未取得' : `¥${item.price.toLocaleString('ja-JP')}`}</span>
                  {item.yield_rate != null && <span className="text-[10px] text-[#4edea3] font-bold">利回り {item.yield_rate.toFixed(2)}%</span>}
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
