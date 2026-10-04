import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import { valuationScenarios } from '../data/mockData';

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

  // Status Tab selection
  const [selectedStatusTab, setSelectedStatusTab] = useState<'health' | 'value' | 'growth' | 'risk'>('value');

  // AI 50 Scenarios
  const [scenarioIdx, setScenarioIdx] = useState(0);
  const [sliderPER, setSliderPER] = useState(10.4);
  const [sliderPBR, setSliderPBR] = useState(1.12);

  // Buy-more simulator state
  const baseShares = currentHolding ? currentHolding.shares : 100;
  const currentPrice = currentHolding ? currentHolding.price : 2600;
  const dps = currentHolding ? currentHolding.dps : 72.8;

  const [simAddedShares, setSimAddedShares] = useState(100);
  const [isAlertOn, setIsAlertOn] = useState(true);

  const activeScenario = valuationScenarios[scenarioIdx] || valuationScenarios[0];

  const computedScore = (() => {
    let score = 95;
    // PER contribution
    if (sliderPER > 15) {
      score -= (sliderPER - 15) * 1.4;
    } else {
      score += (15 - sliderPER) * 0.5;
    }
    // PBR contribution
    if (sliderPBR > 1.2) {
      score -= (sliderPBR - 1.2) * 15;
    } else {
      score += (1.2 - sliderPBR) * 8;
    }
    return Math.min(99, Math.max(30, Math.round(score)));
  })();

  
  // Valuation database containing commentaries for all expected score ranges and metrics
  const valuationCommentsDb = {
    per: [
      { max: 8, label: '極限割安 (ディープバリュー)', text: '大恐慌時の底値圏に相当する極端な割安水準。事業継続性に対する懸念が株価に過度かつ非合理に織り込まれており、安全マージンが極限まで高まっています。' },
      { max: 12, label: '割安バリュー (仕込み推奨)', text: '東証プライム平均を大幅に下回るインカム仕込み優良水準。企業の高い営業キャッシュフロー創出力に対して割安に据え置かれており、長期高利回りを狙う王道の投資レンジです。' },
      { max: 16, label: '適正・巡航レンジ (中立)', text: 'ファンダメンタルズや企業力に見合うフェアバリュー。過熱感も急激な割安感もなく、スライド的な毎月積立や配当再投資を行うのに極めて適した健全な水準です。' },
      { max: 22, label: 'やや割高 (プレミアム先行)', text: '中期的な期待利益成長率が先行して高バリュエーション化。成長持続性の難易度が高く、インカムゲイン目線での配当利回りは縮小傾向にあります。' },
      { max: 999, label: '過熱警戒 (高値モメンタム)', text: '短期的な需給過熱やテーマ性の強い物色に牽引された過熱圏。マルチプルの剥落リスクが極めて高く、新規買い増しは一旦控え、株価の冷静な押し目を待つべき水準です。' }
    ],
    pbr: [
      { max: 0.6, label: '解散価値大幅割れ (東証ターゲット)', text: '実質的な会社清算価値の半額近くで取引される極限ディスカウント。資本効率の改善や大幅な株主還元方針への転換（DOE導入、大規模自社株買いなど）を誘発しやすい強固な防衛ラインです。' },
      { max: 1.0, label: 'PBR1倍未満 (改革・還元要請圏)', text: '東証主導の「1倍割れ是正勧告」の直接的な対象レンジ。純資産対比で手堅い割安感が保たれており、インカムの安全防衛力と増配コミットメントを強く担保しています。' },
      { max: 1.5, label: '中立・標準評価', text: '企業の株主資本効率（ROE）と巡航収益力に応じた適正水準。純資産対比でのプレミアム負担が少なく、インカムポートフォリオの核として手堅い評価ゾーンです。' },
      { max: 999, label: 'プレミアム・グロース評価', text: 'のれんや企業の強力なブランド力・無形資産価値を大きく織り込んだグロース評価。自己資本に対するプレミアムが高いため、純資産比での下値支持力は弱く値動きの荒さに警戒が必要です。' }
    ]
  };

  const computedDiagnostic = (() => {
    const score = computedScore;
    const perVal = sliderPER;
    const pbrVal = sliderPBR;

    // Retrieve corresponding database records
    const perMatch = valuationCommentsDb.per.find(p => perVal <= p.max) || valuationCommentsDb.per[4];
    const pbrMatch = valuationCommentsDb.pbr.find(p => pbrVal <= p.max) || valuationCommentsDb.pbr[3];

    let timing = '';
    let status = '';
    let tag = '';
    let badgeColor = '';
    let lead = '';
    let body = '';

    if (score >= 90) {
      timing = '極限ディスカウント (全力買増し推奨)';
      status = '絶好の押し目買場判定';
      tag = '超割安ゾーン';
      badgeColor = 'text-accent bg-accent-dim border-accent/30';
      lead = '最高峰の買い好機シグナル：';
      body = 'PER判定:【' + perMatch.label + '】' + perMatch.text + ' PBR判定:【' + pbrMatch.label + '】' + pbrMatch.text + ' 総合スコアは極めて高水準な' + score + '点に達しており、安全マージンが完全に確保された一世一代の買い増しチャンスです。';
    } else if (score >= 80) {
      timing = '割安バリュー圏 (推奨押し目買場)';
      status = '好タイミング割安判定';
      tag = '優良バリュー水準';
      badgeColor = 'text-accent bg-accent-dim border-accent/20';
      lead = '優良バリュー評価：';
      body = 'PER判定:【' + perMatch.label + '】' + perMatch.text + ' PBR判定:【' + pbrMatch.label + '】' + pbrMatch.text + ' 総合スコア' + score + '点。割安感に富み、配当再投資や余剰キャッシュによる積極的な株数積み増しに極めて優位なタイミングです。';
    } else if (score >= 65) {
      timing = 'フェアバリュー圏 (適正・中立)';
      status = '適正価格範囲内';
      tag = '中立ゾーン';
      badgeColor = 'text-sky-400 bg-sky-500/15 border-sky-500/30';
      lead = '適正価格判断：';
      body = 'PER判定:【' + perMatch.label + '】' + perMatch.text + ' PBR判定:【' + pbrMatch.label + '】' + pbrMatch.text + ' 総合スコア' + score + '点。適正な評価水準内です。慌てて一括買いする必要はありませんが、毎月のドルコスト積立や配当自動再投資に最適な中立ゾーンです。';
    } else {
      timing = 'プレミアム・高バリュエーション (様子見)';
      status = '割高・高マルチプル圏';
      tag = '様子見推奨ゾーン';
      badgeColor = 'text-amber-400 bg-amber-500/15 border-amber-500/30';
      lead = '過熱・様子見アラート：';
      body = 'PER判定:【' + perMatch.label + '】' + perMatch.text + ' PBR判定:【' + pbrMatch.label + '】' + pbrMatch.text + ' 総合スコア' + score + '点。相場モメンタムによって買われ過ぎの水準にあり、配当利回りは大きく圧縮されています。次の調整局面や権利落ちを待つのが最も賢明です。';
    }

    return { timing, status, tag, badgeColor, lead, body };
  })();

  const totalSimShares = baseShares + simAddedShares;
  const additionalCost = simAddedShares * currentPrice;
  const addedAnnualDiv = Math.round(simAddedShares * dps);
  const newTotalAnnualDiv = Math.round(totalSimShares * dps);
  const newMonthlyDiv = Math.round(newTotalAnnualDiv / 12);
  const multiplier = baseShares > 0 ? (totalSimShares / baseShares).toFixed(1) : '1.0';

  const isUSD = currentHolding.currency === '$';
  const prefix = isUSD ? '$' : '¥';

  const totalValuation = currentHolding.shares * currentHolding.price;
  const totalCost = currentHolding.shares * currentHolding.acqPrice;
  const gainLoss = totalValuation - totalCost;
  const gainLossPct = totalCost > 0 ? ((gainLoss / totalCost) * 100).toFixed(2) : '0.00';

  const isBookmarked = watchItem?.alertEnabled ?? false;

  const handleRandomScenario = () => {
    const r = Math.floor(Math.random() * valuationScenarios.length);
    setScenarioIdx(r);
    setSliderPER(valuationScenarios[r].per);
    setSliderPBR(valuationScenarios[r].pbr);
    showToast(`シナリオ #${r + 1} を適用しました`, 'shuffle');
  };

  const handleSliderChange = (newPer: number, newPbr: number) => {
    setSliderPER(newPer);
    setSliderPBR(newPbr);
    // Find closest scenario
    let best = 0;
    let minDist = Infinity;
    valuationScenarios.forEach((sc, i) => {
      const dist = Math.pow(sc.per - newPer, 2) + Math.pow((sc.pbr - newPbr) * 10, 2);
      if (dist < minDist) {
        minDist = dist;
        best = i;
      }
    });
    setScenarioIdx(best);
  };

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

        {/* External Research Pills */}
        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between gap-2">
          <span className="text-[10px] text-[#bbcabf] font-semibold flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px] text-[#4edea3]">travel_explore</span>
            外部リサーチ:
          </span>
          <div className="flex items-center gap-1.5">
            <a
              href={`https://finance.yahoo.co.jp/quote/${currentHolding.code}.T`}
              target="_blank"
              rel="noopener noreferrer"
              className="px-2.5 py-1 rounded-full bg-[#0b1326] hover:bg-slate-800 border border-slate-700 text-white text-[10px] font-bold flex items-center gap-1 transition-colors"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
              <span>Y! ファイナンス</span>
              <span className="material-symbols-outlined text-[12px] text-[#bbcabf]">open_in_new</span>
            </a>
            <a
              href={`https://irbank.net/${currentHolding.code}`}
              target="_blank"
              rel="noopener noreferrer"
              className="px-2.5 py-1 rounded-full bg-[#0b1326] hover:bg-slate-800 border border-slate-700 text-white text-[10px] font-bold flex items-center gap-1 transition-colors"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3]" />
              <span>IR BANK</span>
              <span className="material-symbols-outlined text-[12px] text-[#bbcabf]">open_in_new</span>
            </a>
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
            <span className="font-mono text-sm font-bold text-white mt-0.5 block">{currentHolding.yieldRate}%</span>
            <span className="text-[9px] text-[#bbcabf]">現在株価 {prefix}{currentHolding.price}換算</span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#171f33]">
            <span className="text-[10px] text-[#bbcabf]">取得ベース利回り (YOC)</span>
            <span className="font-mono text-sm font-bold text-[#4edea3] mt-0.5 block">{currentHolding.yoc}% YOC</span>
            <span className="text-[9px] text-[#bbcabf]">取得単価 {prefix}{currentHolding.acqPrice}換算</span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#171f33]">
            <span className="text-[10px] text-[#bbcabf]">次回配当予定</span>
            <span className="font-mono text-sm font-bold text-white mt-0.5 block">12月中旬</span>
            <span className="text-[9px] text-[#4edea3]">中間配当 ¥3,640</span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#171f33]">
            <span className="text-[10px] text-[#bbcabf]">権利確定月</span>
            <span className="font-mono text-sm font-bold text-white mt-0.5 block">3月末 / 9月末</span>
            <span className="text-[9px] text-[#bbcabf]">年2回中間・期末</span>
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

      {/* 4. 1株配当金の推移 (4期) Bar Chart Card */}
      <section className="rounded-2xl bg-[#131b2e] p-3.5 shadow-md border border-slate-800 flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">bar_chart</span>
            <h3 className="font-bold text-sm text-white">1株配当金の推移 (4期)</h3>
          </div>
          <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold">
            連続増配
          </span>
        </div>

        <div className="h-32 bg-[#0b1326] rounded-xl p-3 border border-slate-800 flex items-end justify-between gap-3">
          {[
            { yr: '22.3期', dps: '¥52', h: 'h-[50%]', bg: 'bg-indigo-400/60' },
            { yr: '23.3期', dps: '¥60', h: 'h-[65%]', bg: 'bg-indigo-400/80' },
            { yr: '24.3期', dps: '¥65', h: 'h-[75%]', bg: 'bg-indigo-400' },
            { yr: '25.3予', dps: '¥72.8', h: 'h-[95%]', bg: 'bg-gradient-to-t from-[#10b981] to-[#4edea3]' }
          ].map(col => (
            <div key={col.yr} className="flex-1 flex flex-col items-center h-full justify-end">
              <span className={`text-[9px] font-mono font-bold mb-1 ${col.yr === '25.3予' ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                {col.dps}
              </span>
              <div className={`w-full max-w-[28px] rounded-t-lg ${col.bg} ${col.h}`} />
              <span className={`text-[9px] font-mono mt-1 ${col.yr === '25.3予' ? 'text-[#4edea3] font-bold' : 'text-[#bbcabf]'}`}>
                {col.yr}
              </span>
            </div>
          ))}
        </div>

        <div className="flex items-center justify-between text-[10px] text-[#bbcabf] px-1">
          <span>4期増配率: <strong className="text-[#4edea3] font-bold">+40.0%</strong></span>
          <span>配当性向: <strong className="text-white font-bold">約35.2%</strong></span>
        </div>
      </section>

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
            className={`p-2.5 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'health'
                ? 'bg-[#171f33] border-[#4edea3]'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>① 健全性</span>
              <span className="material-symbols-outlined text-[15px] text-[#4edea3]">verified_user</span>
            </div>
            <div className="font-bold text-sm text-[#4edea3]">A判定 <span className="text-xs text-white">35.2%</span></div>
            <span className="text-[10px] text-[#4edea3] block mt-0.5">極めて安全</span>
          </button>

          <button
            type="button"
            onClick={() => setSelectedStatusTab('value')}
            className={`p-2.5 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'value'
                ? 'bg-[#171f33] border-[#4edea3]'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>② 買場シグナル</span>
              <span className="material-symbols-outlined text-[15px] text-[#4edea3]">radar</span>
            </div>
            <div className="font-bold text-sm text-[#4edea3]">{computedDiagnostic.status} <span className="text-xs text-white">{currentHolding.yieldRate}%</span></div>
            <span className="text-[10px] text-[#4edea3] block mt-0.5">{computedDiagnostic.tag}</span>
          </button>

          <button
            type="button"
            onClick={() => setSelectedStatusTab('growth')}
            className={`p-2.5 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'growth'
                ? 'bg-[#171f33] border-[#4edea3]'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>③ 増配耐性</span>
              <span className="material-symbols-outlined text-[15px] text-indigo-300">military_tech</span>
            </div>
            <div className="font-bold text-sm text-white">10期連続 <span className="text-xs text-[#4edea3]">+8.5%</span></div>
            <span className="text-[10px] text-[#4edea3] block mt-0.5">非減配25年</span>
          </button>

          <button
            type="button"
            onClick={() => setSelectedStatusTab('risk')}
            className={`p-2.5 rounded-xl border text-left transition-all ${
              selectedStatusTab === 'risk'
                ? 'bg-[#171f33] border-amber-500'
                : 'bg-[#0b1326] border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between text-[11px] text-[#bbcabf] mb-1">
              <span>④ 集中リスク</span>
              <span className="material-symbols-outlined text-[15px] text-amber-400">donut_large</span>
            </div>
            <div className="font-bold text-sm text-amber-400">やや高め <span className="text-xs text-white">18.2%</span></div>
            <span className="text-[10px] text-amber-400 block mt-0.5">上限目安10%</span>
          </button>
        </div>

        {/* Dynamic Detail Panel: VALUE SIGNAL WITH 50 SCENARIOS */}
        {selectedStatusTab === 'value' && (
          <div className="p-3 rounded-xl bg-[#0b1326] border border-[#4edea3]/40 flex flex-col gap-2.5 mt-1">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-white flex items-center gap-1">
                <span className="material-symbols-outlined text-[16px] text-[#4edea3]">psychology</span>
                AI買場診断・バリュエーション (50シナリオ)
              </span>
              <button
                type="button"
                onClick={handleRandomScenario}
                className="px-2 py-0.5 rounded-full bg-[#171f33] hover:bg-slate-700 text-[#4edea3] text-[10px] font-bold flex items-center gap-1"
              >
                <span className="material-symbols-outlined text-[12px]">shuffle</span>
                ランダム
              </button>
            </div>

            {/* Scenario Select */}
            <select
              value={scenarioIdx}
              onChange={e => {
                const idx = parseInt(e.target.value);
                setScenarioIdx(idx);
                setSliderPER(valuationScenarios[idx].per);
                setSliderPBR(valuationScenarios[idx].pbr);
              }}
              className="w-full h-9 px-2.5 rounded-lg bg-[#171f33] border border-slate-700 text-white text-xs font-medium focus:border-[#4edea3] outline-none"
            >
              {valuationScenarios.map((sc, i) => (
                <option key={sc.id} value={i}>
                  [スコア {sc.score}] {sc.title} (PER {sc.per}倍 / PBR {sc.pbr}倍)
                </option>
              ))}
            </select>

            {/* Interactive PER / PBR Sliders */}
            <div className="grid grid-cols-2 gap-2 pt-1 border-t border-slate-800 text-[10px]">
              <div>
                <div className="flex justify-between text-[#bbcabf] mb-0.5">
                  <span>PER:</span>
                  <strong className="text-[#4edea3] font-mono">{sliderPER}倍</strong>
                </div>
                <input
                  type="range"
                  min="3"
                  max="60"
                  step="0.5"
                  value={sliderPER}
                  onChange={e => handleSliderChange(parseFloat(e.target.value), sliderPBR)}
                  className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer"
                />
              </div>

              <div>
                <div className="flex justify-between text-[#bbcabf] mb-0.5">
                  <span>PBR:</span>
                  <strong className="text-[#4edea3] font-mono">{sliderPBR}倍</strong>
                </div>
                <input
                  type="range"
                  min="0.2"
                  max="5.0"
                  step="0.05"
                  value={sliderPBR}
                  onChange={e => handleSliderChange(sliderPER, parseFloat(e.target.value))}
                  className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer"
                />
              </div>
            </div>

            {/* Diagnosis Result Card */}
            <div className={`p-2.5 rounded-xl border flex flex-col gap-1 text-xs ${computedDiagnostic.badgeColor}`}>
              <div className="flex items-center justify-between text-[11px]">
                <span className="font-bold text-white">{computedDiagnostic.status}</span>
                <span className="px-2 py-0.2 rounded-full font-bold text-[10px] bg-white/10">
                  {computedDiagnostic.timing}
                </span>
              </div>
              <p className="text-[11px] leading-relaxed opacity-90">
                <strong className="text-white block mb-0.5">{computedDiagnostic.lead}</strong> {computedDiagnostic.body}
              </p>
            </div>
          </div>
        )}

        {/* HEALTH TAB */}
        {selectedStatusTab === 'health' && (
          <div className="p-3 rounded-xl bg-[#0b1326] border border-[#4edea3]/40 flex flex-col gap-2 text-xs">
            <div className="flex items-center justify-between">
              <span className="font-bold text-white">配当性向 (当期見込)</span>
              <span className="font-mono text-sm font-bold text-[#4edea3]">35.2% (適正黄金比率)</span>
            </div>
            <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
              <div className="h-full bg-[#10b981] rounded-full" style={{ width: '35.2%' }} />
            </div>
            <div className="grid grid-cols-2 gap-2 pt-1">
              <div className="p-2 rounded bg-[#171f33]">
                <span className="text-[10px] text-[#bbcabf] block">自己資本比率</span>
                <span className="font-bold text-white">39.8% (健全)</span>
              </div>
              <div className="p-2 rounded bg-[#171f33]">
                <span className="text-[10px] text-[#bbcabf] block">ネットD/Eレシオ</span>
                <span className="font-bold text-[#4edea3]">-0.22倍 (実質無借金)</span>
              </div>
            </div>
          </div>
        )}

        {/* GROWTH TAB */}
        {selectedStatusTab === 'growth' && (
          <div className="p-3 rounded-xl bg-[#0b1326] border border-indigo-400/40 flex flex-col gap-2 text-xs">
            <span className="font-bold text-white">10期連続増配・25年間非減配</span>
            <p className="text-[11px] text-[#bbcabf] leading-relaxed">
              リーマンショックやコロナショック局面でも減配せず配当を維持・増配。累進配当方針を掲げており、安定インカムの核となる銘柄です。
            </p>
          </div>
        )}

        {/* RISK TAB */}
        {selectedStatusTab === 'risk' && (
          <div className="p-3 rounded-xl bg-[#0b1326] border border-amber-500/40 flex flex-col gap-2 text-xs">
            <span className="font-bold text-amber-400">ポートフォリオ配当依存度 18.2%</span>
            <p className="text-[11px] text-[#bbcabf] leading-relaxed">
              総配当に占める比率が約18%と目安（10%以下）を上回っています。今後の追加投資では他業種を優先し、分散を図ることが推奨されます。
            </p>
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
