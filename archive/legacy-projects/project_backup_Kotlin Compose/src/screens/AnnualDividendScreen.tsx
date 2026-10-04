import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import { AnimatedNumber } from '../components/AnimatedNumber';
import { motion, AnimatePresence } from 'motion/react';

export const AnnualDividendScreen: React.FC = () => {
  const { holdings } = useApp();

  const [basis, setBasis] = useState<'eval' | 'cost'>('eval');
  const [activeTab, setActiveTab] = useState<'analysis' | 'dividend' | 'asset'>('dividend');

  // Interactive donut slice
  const [activeDonut, setActiveDonut] = useState<{ name: string; amount: string; pct: string }>({
    name: '年間配当合計',
    amount: '¥124,500',
    pct: '4銘柄分散中'
  });

  // Selected dividend year
  const [selectedDivYear, setSelectedDivYear] = useState('2025');

  // Asset duration: 5y or 12m
  const [assetPeriod, setAssetPeriod] = useState<'5y' | '12m'>('5y');
  const [selectedAssetYear, setSelectedAssetYear] = useState('2025');

  // Compounding simulator state
  const [simMonthlyDeposit, setSimMonthlyDeposit] = useState(30000); // default ¥30,000
  const [simYield, setSimYield] = useState(4.0); // default 4.0%
  const [simReinvest, setSimReinvest] = useState(true); // default reinvest

  const calculateCompoundProj = (years: number) => {
    let current = 3500000; // start with current asset ¥3.5M
    const monthlyVal = simMonthlyDeposit;
    const yieldRate = simYield / 100;
    const capAppreciation = 0.035; // conservative 3.5% capital growth
    // tax-adjusted yield (approx 20.315% tax)
    const annualGrowth = capAppreciation + (simReinvest ? yieldRate * 0.8 : 0);

    let totalInvested = 3050000; // start with current cost
    for (let i = 0; i < years; i++) {
      current = current * (1 + annualGrowth) + (monthlyVal * 12) * (1 + annualGrowth / 2);
      totalInvested += (monthlyVal * 12);
    }
    return {
      assets: Math.round(current),
      invested: totalInvested,
      monthlyPassive: Math.round((current * yieldRate) / 12)
    };
  };

  const proj10 = calculateCompoundProj(10);
  const proj20 = calculateCompoundProj(20);
  const proj30 = calculateCompoundProj(30);

  // Accordion state for sectors
  const [expandedSectors, setExpandedSectors] = useState<Record<string, boolean>>({
    transport: false,
    tech: false,
    fund: false
  });

  const toggleSector = (sec: string) => {
    setExpandedSectors(prev => ({ ...prev, [sec]: !prev[sec] }));
  };

  const divYearDetails: Record<string, { val: string; monthly: string; growth: string; pct: string; status: string }> = {
    '2021': { val: '¥48,000', monthly: '¥4,000', growth: '¥48,000', pct: '基準年', status: '実績' },
    '2022': { val: '¥66,000', monthly: '¥5,500', growth: '+¥18,000', pct: '+37.5%', status: '実績' },
    '2023': { val: '¥87,000', monthly: '¥7,250', growth: '+¥21,000', pct: '+31.8%', status: '実績' },
    '2024': { val: '¥110,800', monthly: '¥9,233', growth: '+¥23,800', pct: '+27.4%', status: '実績' },
    '2025': { val: '¥124,500', monthly: '¥10,375', growth: '+¥13,700', pct: '+12.4%', status: '予想' }
  };

  const currentDivInfo = divYearDetails[selectedDivYear] || divYearDetails['2025'];

  // Pre-created portfolio health comments database (backend-style static data structure)
  const portfolioHealthDatabase = {
    S: {
      grade: 'S',
      power: '極めて高 (盤石)',
      risk: '極めて低 (0.2%)',
      color: 'text-emerald-400 bg-emerald-500/15 border-emerald-500/30',
      title: '超堅牢分散ポートフォリオ (理想形)',
      comment: '日米・オルカンの理想的な地域分散が実現されています。複数セクターへの高度な分散に加え、主要銘柄の増配実績も強固。YOC（簿価利回り）も極めて高水準で、インカムの防衛力・成長力ともに申し分ない最高峰の「超堅牢ポートフォリオ」です。継続的な積立と配当自動再投資を行うことで、複利の爆発力を最大化できます。'
    },
    A: {
      grade: 'A',
      power: '高 (安定成長レンジ)',
      risk: '極めて低 (0.8%)',
      color: 'text-accent bg-accent-dim border-accent/30',
      title: '優良分散ポートフォリオ',
      comment: '主力銘柄の連続増配実績が強固で、YOC（簿価利回り）は高水準を維持している「優良分散ポートフォリオ」です。通貨および地域（日本株・米国株・オルカン）の分散バランスも極めて良好で、将来的なインカムの安定的・累積的成長が期待できます。さらに別セクターの王道高配当株を少数加えることで、完璧な防衛壁へと進化します。'
    },
    B: {
      grade: 'B',
      power: '中 (成長発展期)',
      risk: '中 (2.5%)',
      color: 'text-sky-400 bg-sky-500/15 border-sky-500/30',
      title: '発展途上・準分散ポートフォリオ',
      comment: 'インカムフローは一定量稼げていますが、セクターや地域（日米）に多少の偏りが見られる「発展途上ポートフォリオ」です。特定のセクターや銘柄に依存すると、減配局面でのダメージを直接受けやすくなります。次回の買増しタイミングで、別業種（非資源系、ディフェンシブ通信・ヘルスケアなど）の王道銘柄や米国ETFなどを加えるとさらに強固になります。'
    },
    C: {
      grade: 'C',
      power: '中 (要経過観察)',
      risk: 'やや高 (8.4%)',
      color: 'text-amber-400 bg-amber-500/15 border-amber-500/30',
      title: '集中投資型・高依存ポートフォリオ',
      comment: '保有銘柄数が少なく、個別銘柄の業績や減配リスクに対する比重が高い「集中投資型」です。特定の1〜2社が不祥事や業績不振で減配すると、年間不労所得が大きく減少してしまいます。複数の異なるセクターや、全世界株式・高配当分散ETFなどを少額からポートフォリオに組み入れることで、インカムのボラティリティを下げ、長期安定性を高めましょう。'
    },
    D: {
      grade: 'D',
      power: '判定不可 (登録なし)',
      risk: '判定不可 (0.0%)',
      color: 'text-rose-400 bg-rose-500/15 border-rose-500/30',
      title: '初期未構築ステージ',
      comment: '現在ポートフォリオに保有銘柄が登録されていません。ポートフォリオを追加するか、Watchlistの目標買場を活用して、最初のインカム基盤を構築しましょう。1株からでも優良連続増配株を購入することで、不労所得が生まれる喜びと複利の第一歩を実感できます。'
    }
  };

  const getAIDiagnosis = () => {
    const totalCount = holdings.length;
    const catSet = new Set(holdings.map(h => h.category));
    const sectorSet = new Set(holdings.map(h => h.sector));

    if (totalCount === 0) {
      return {
        ...portfolioHealthDatabase.D,
        score: 35,
        text: portfolioHealthDatabase.D.comment
      };
    }

    // Dynamic scoring formula
    let score = 50;
    score += Math.min(25, totalCount * 6); // up to 25 points for number of holdings
    score += Math.min(15, sectorSet.size * 5); // up to 15 points for sector diversification
    score += Math.min(10, catSet.size * 5); // up to 10 points for region/asset class diversification

    // Apply grade based on final calculated score
    let item = portfolioHealthDatabase.D;
    if (score >= 90) {
      item = portfolioHealthDatabase.S;
    } else if (score >= 80) {
      item = portfolioHealthDatabase.A;
    } else if (score >= 70) {
      item = portfolioHealthDatabase.B;
    } else if (score >= 60) {
      item = portfolioHealthDatabase.C;
    }

    return {
      grade: item.grade,
      score: Math.min(100, score),
      text: item.comment,
      power: item.power,
      risk: item.risk,
      color: item.color,
      title: item.title
    };
  };

  const aiDiagnosis = getAIDiagnosis();

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-3.5">
      {/* 1. Hero Projected Dividend Summary Card */}
      <section className="relative overflow-hidden rounded-2xl bg-[#131b2e] p-4 shadow-xl border border-slate-800">
        <div className="absolute -top-16 -left-16 w-56 h-56 rounded-full bg-[#10b981]/10 blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col gap-3">
          {/* Top Row: Label & Basis Pill Switcher */}
          <div className="flex items-center justify-between">
            <span className="text-[11px] text-[#bbcabf] uppercase tracking-wider font-semibold flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-[#4edea3] animate-pulse" />
              年間受取見込額 (税引前)
            </span>

            <div className="relative flex items-center bg-[#171f33] p-0.5 rounded-full border border-slate-700/60 shadow-inner">
              <button
                type="button"
                onClick={() => setBasis('eval')}
                className={`relative z-10 px-2.5 py-1 rounded-full text-[11px] transition-colors ${
                  basis === 'eval' ? 'text-[#003824] font-bold bg-[#4edea3]' : 'text-[#bbcabf] hover:text-white'
                }`}
              >
                評価額基準
              </button>
              <button
                type="button"
                onClick={() => setBasis('cost')}
                className={`relative z-10 px-2.5 py-1 rounded-full text-[11px] transition-colors ${
                  basis === 'cost' ? 'text-[#003824] font-bold bg-[#4edea3]' : 'text-[#bbcabf] hover:text-white'
                }`}
              >
                取得額基準
              </button>
            </div>
          </div>

          {/* Hero Number & Change Pill */}
          <div className="flex items-baseline justify-between gap-2 mt-0.5">
            <div className="flex items-baseline gap-1.5">
              <span className="font-mono text-3xl sm:text-4xl text-white font-bold tracking-tight">
                <AnimatedNumber value={basis === 'eval' ? 124500 : 118200} prefix="¥ " />
              </span>
              <span className="text-xs text-[#bbcabf] font-medium">/ 年</span>
            </div>
            <div className="flex items-center gap-1 text-[#4edea3] bg-[#10b981]/15 border border-[#4edea3]/30 px-2.5 py-1 rounded-full text-xs font-bold shrink-0">
              <span className="material-symbols-outlined text-[15px]">trending_up</span>
              <span>+12.4% 前年比</span>
            </div>
          </div>

          {/* Secondary Metrics */}
          <div className="grid grid-cols-2 gap-2 pt-1 border-t border-slate-800">
            <div className="flex flex-col p-2.5 rounded-xl bg-[#0b1326]/60 border border-slate-800/80">
              <span className="text-[11px] text-[#bbcabf] flex items-center gap-1 font-medium">
                <span className="material-symbols-outlined text-[#4edea3] text-[14px]">calendar_today</span>
                月間平均配当
              </span>
              <div className="flex items-baseline gap-1 mt-0.5">
                <span className="font-mono text-base text-white font-bold">
                  約 <AnimatedNumber value={basis === 'eval' ? 10375 : 9850} prefix="¥" />
                </span>
                <span className="text-[10px] text-[#bbcabf]">/ 月</span>
              </div>
            </div>

            <div className="flex flex-col p-2.5 rounded-xl bg-[#0b1326]/60 border border-slate-800/80">
              <span className="text-[11px] text-[#bbcabf] flex items-center gap-1 font-medium">
                <span className="material-symbols-outlined text-[#4edea3] text-[14px]">percent</span>
                平均配当利回り
              </span>
              <div className="flex items-baseline gap-0.5 mt-0.5">
                <span className="font-mono text-lg text-[#4edea3] font-bold">
                  {basis === 'eval' ? '3.56' : '4.18'}
                </span>
                <span className="text-xs text-[#4edea3] font-bold">%</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 2. 3-Way Tab Switcher */}
      <div className="relative w-full flex p-1 rounded-xl bg-[#131b2e] shadow-md border border-slate-800">
        <button
          type="button"
          onClick={() => setActiveTab('analysis')}
          className={`flex-1 py-2 rounded-lg text-xs font-semibold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
            activeTab === 'analysis'
              ? 'bg-[#171f33] text-[#4edea3] font-bold shadow'
              : 'text-[#bbcabf] hover:text-white'
          }`}
        >
          <span className="material-symbols-outlined text-[18px]">donut_large</span>
          <span>分析</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('dividend')}
          className={`flex-1 py-2 rounded-lg text-xs font-semibold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
            activeTab === 'dividend'
              ? 'bg-[#171f33] text-[#4edea3] font-bold shadow'
              : 'text-[#bbcabf] hover:text-white'
          }`}
        >
          <span className="material-symbols-outlined text-[18px]">trending_up</span>
          <span>配当推移</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('asset')}
          className={`flex-1 py-2 rounded-lg text-xs font-semibold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
            activeTab === 'asset'
              ? 'bg-[#171f33] text-[#4edea3] font-bold shadow'
              : 'text-[#bbcabf] hover:text-white'
          }`}
        >
          <span className="material-symbols-outlined text-[18px]">account_balance</span>
          <span>資産推移</span>
        </button>
      </div>

      {/* ================= TAB 1: 分析 (DONUT & SECTOR) ================= */}
      {activeTab === 'analysis' && (
        <div className="flex flex-col gap-3.5">
          {/* Donut Chart Section */}
          <section className="rounded-2xl bg-[#131b2e] p-4 flex flex-col items-center shadow-lg border border-slate-800">
            <div className="w-full flex items-center justify-between mb-1">
              <h3 className="text-sm text-white font-bold flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[#4edea3] text-[20px]">donut_small</span>
                構成銘柄・配当比率
              </h3>
              <span className="text-[11px] text-[#bbcabf] bg-slate-800 px-2 py-0.5 rounded-full font-medium">
                4銘柄保有
              </span>
            </div>

            {/* Interactive SVG Donut */}
            <div className="relative w-52 h-52 flex items-center justify-center my-2">
              <svg viewBox="0 0 100 100" className="w-full h-full -rotate-90 transform">
                <circle cx="50" cy="50" r="38" fill="transparent" stroke="#171f33" strokeWidth="12" />
                {/* Toyota: 38% */}
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="transparent"
                  stroke="#10b981"
                  strokeWidth="12"
                  strokeDasharray="90.73 238.76"
                  strokeDashoffset="0"
                  className="cursor-pointer hover:stroke-[14] transition-all"
                  onClick={() => setActiveDonut({ name: 'トヨタ自動車 (7203)', amount: '¥47,310', pct: '38.0%' })}
                />
                {/* Apple: 28% */}
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="transparent"
                  stroke="#38bdf8"
                  strokeWidth="12"
                  strokeDasharray="66.85 238.76"
                  strokeDashoffset="-90.73"
                  className="cursor-pointer hover:stroke-[14] transition-all"
                  onClick={() => setActiveDonut({ name: 'Apple Inc. (AAPL)', amount: '¥34,860', pct: '28.0%' })}
                />
                {/* eMAXIS: 18% */}
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="transparent"
                  stroke="#c0c1ff"
                  strokeWidth="12"
                  strokeDasharray="42.98 238.76"
                  strokeDashoffset="-157.58"
                  className="cursor-pointer hover:stroke-[14] transition-all"
                  onClick={() => setActiveDonut({ name: 'eMAXIS Slim 全世界', amount: '¥22,410', pct: '18.0%' })}
                />
                {/* NTT: 16% */}
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="transparent"
                  stroke="#ffb3ad"
                  strokeWidth="12"
                  strokeDasharray="38.20 238.76"
                  strokeDashoffset="-200.56"
                  className="cursor-pointer hover:stroke-[14] transition-all"
                  onClick={() => setActiveDonut({ name: 'NTT (9432)', amount: '¥19,920', pct: '16.0%' })}
                />
              </svg>

              <div className="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none px-4">
                <span className="text-[11px] text-[#bbcabf] font-medium">{activeDonut.name}</span>
                <span className="font-mono text-xl text-white font-bold mt-0.5">{activeDonut.amount}</span>
                <span className="text-[11px] text-[#4edea3] mt-0.5 font-bold flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3] inline-block" />
                  {activeDonut.pct}
                </span>
              </div>
            </div>

            {/* Legend Pills */}
            <div className="grid grid-cols-2 gap-2 mt-2 w-full">
              {[
                { name: 'トヨタ自動車', code: '7203', pct: '38.0%', amt: '¥47,310', color: 'bg-[#10b981]', border: 'border-[#10b981]/40' },
                { name: 'Apple Inc.', code: 'AAPL', pct: '28.0%', amt: '¥34,860', color: 'bg-sky-400', border: 'border-sky-400/40' },
                { name: 'eMAXIS Slim', code: 'オルカン', pct: '18.0%', amt: '¥22,410', color: 'bg-indigo-300', border: 'border-indigo-400/40' },
                { name: 'NTT', code: '9432', pct: '16.0%', amt: '¥19,920', color: 'bg-rose-300', border: 'border-rose-400/40' }
              ].map(leg => (
                <div
                  key={leg.name}
                  onClick={() => setActiveDonut({ name: leg.name, amount: leg.amt, pct: leg.pct })}
                  className={`flex items-center justify-between p-2 rounded-xl bg-[#0b1326]/70 border border-slate-800 hover:${leg.border} cursor-pointer transition-all`}
                >
                  <div className="flex items-center gap-1.5 truncate">
                    <span className={`w-2.5 h-2.5 rounded-full ${leg.color} shrink-0`} />
                    <span className="text-xs text-white font-semibold truncate">{leg.name}</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-slate-200 shrink-0">{leg.pct}</span>
                </div>
              ))}
            </div>
          </section>

          {/* Sector Breakdown */}
          <section className="rounded-2xl bg-[#131b2e] p-4 flex flex-col gap-3 shadow-lg border border-slate-800">
            <div className="flex items-center justify-between">
              <h3 className="text-sm text-white font-bold flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[#4edea3] text-[18px]">category</span>
                セクター別比率
              </h3>
              <span className="text-[10px] text-[#4edea3] bg-[#10b981]/15 px-2 py-0.5 rounded-full font-bold">
                3業種分散
              </span>
            </div>

            {/* Stacked Progress Bar */}
            <div className="flex flex-col gap-1.5">
              <div className="w-full h-3 rounded-full bg-slate-800 overflow-hidden flex shadow-inner">
                <div className="h-full bg-[#10b981]" style={{ width: '45.2%' }} title="輸送用機器 45.2%" />
                <div className="h-full bg-sky-400" style={{ width: '32.8%' }} title="情報・通信 32.8%" />
                <div className="h-full bg-indigo-400" style={{ width: '22.0%' }} title="全世界・米国 22.0%" />
              </div>
              <div className="flex items-center justify-between text-[11px] text-[#bbcabf] px-0.5">
                <span className="flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full bg-[#10b981]" />輸送用機器 45.2%
                </span>
                <span className="flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full bg-sky-400" />情報・通信 32.8%
                </span>
                <span className="flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full bg-indigo-400" />全世界・米国 22.0%
                </span>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2 pt-1">
              <div className="flex flex-col p-2 rounded-xl bg-[#0b1326]/60 border border-slate-800">
                <span className="text-[10px] text-[#bbcabf]">輸送用機器</span>
                <span className="font-mono text-xs font-bold text-white mt-0.5">¥56,274</span>
                <span className="text-[9px] text-[#4edea3] font-bold">45.2%</span>
              </div>
              <div className="flex flex-col p-2 rounded-xl bg-[#0b1326]/60 border border-slate-800">
                <span className="text-[10px] text-[#bbcabf]">情報・通信</span>
                <span className="font-mono text-xs font-bold text-white mt-0.5">¥40,836</span>
                <span className="text-[9px] text-sky-400 font-bold">32.8%</span>
              </div>
              <div className="flex flex-col p-2 rounded-xl bg-[#0b1326]/60 border border-slate-800">
                <span className="text-[10px] text-[#bbcabf]">全世界・米国</span>
                <span className="font-mono text-xs font-bold text-white mt-0.5">¥27,390</span>
                <span className="text-[9px] text-indigo-300 font-bold">22.0%</span>
              </div>
            </div>
          </section>

          {/* Sector Details Accordion */}
          <section className="flex flex-col gap-2.5">
            <h3 className="text-sm font-bold text-white flex items-center gap-1.5 px-1">
              <span className="material-symbols-outlined text-[#4edea3] text-[20px]">view_list</span>
              セクター・銘柄構成詳細
            </h3>

            {/* Transport Sector */}
            <div className="rounded-2xl bg-[#131b2e] p-3 border border-slate-800 flex flex-col gap-2">
              <button
                type="button"
                onClick={() => toggleSector('transport')}
                className="w-full flex items-center justify-between text-left cursor-pointer"
              >
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-[#10b981]" />
                  <span className="text-xs font-bold text-white">輸送用機器</span>
                  <span className="text-[10px] text-[#4edea3] bg-[#10b981]/15 px-1.5 py-0.2 rounded font-semibold">45.2%</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="font-mono text-xs font-bold text-[#4edea3]">¥56,274 / 年</span>
                  <span className={`material-symbols-outlined text-[#bbcabf] text-[18px] transition-transform ${expandedSectors.transport ? 'rotate-180' : ''}`}>
                    expand_more
                  </span>
                </div>
              </button>

              {expandedSectors.transport && (
                <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 mt-1">
                  <div className="flex items-center gap-2">
                    <span className="w-8 h-8 rounded-lg bg-slate-800 flex items-center justify-center font-mono text-[11px] font-bold text-[#4edea3]">7203</span>
                    <div>
                      <p className="text-xs font-bold text-white">トヨタ自動車</p>
                      <p className="text-[10px] text-[#bbcabf]">保有 100株 • 利回り 2.8%</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-mono text-xs font-bold text-white">¥7,280</span>
                    <span className="block text-[9px] text-[#4edea3]">ポートフォリオ 45.2%</span>
                  </div>
                </div>
              )}
            </div>

            {/* Tech Sector */}
            <div className="rounded-2xl bg-[#131b2e] p-3 border border-slate-800 flex flex-col gap-2">
              <button
                type="button"
                onClick={() => toggleSector('tech')}
                className="w-full flex items-center justify-between text-left cursor-pointer"
              >
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-sky-400" />
                  <span className="text-xs font-bold text-white">情報・通信 / テック</span>
                  <span className="text-[10px] text-sky-400 bg-sky-500/15 px-1.5 py-0.2 rounded font-semibold">32.8%</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="font-mono text-xs font-bold text-sky-400">¥40,836 / 年</span>
                  <span className={`material-symbols-outlined text-[#bbcabf] text-[18px] transition-transform ${expandedSectors.tech ? 'rotate-180' : ''}`}>
                    expand_more
                  </span>
                </div>
              </button>

              {expandedSectors.tech && (
                <div className="flex flex-col gap-1.5 mt-1">
                  <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#0b1326] border border-slate-800">
                    <div className="flex items-center gap-2">
                      <span className="w-8 h-8 rounded-lg bg-indigo-950 flex items-center justify-center font-mono text-[11px] font-bold text-indigo-300">AAPL</span>
                      <div>
                        <p className="text-xs font-bold text-white">Apple Inc.</p>
                        <p className="text-[10px] text-[#bbcabf]">保有 10株 • 利回り 0.5% + 連続増配</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <span className="font-mono text-xs font-bold text-white">¥1,700</span>
                      <span className="block text-[9px] text-sky-400">米国株</span>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Fund Sector */}
            <div className="rounded-2xl bg-[#131b2e] p-3 border border-slate-800 flex flex-col gap-2">
              <button
                type="button"
                onClick={() => toggleSector('fund')}
                className="w-full flex items-center justify-between text-left cursor-pointer"
              >
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-indigo-400" />
                  <span className="text-xs font-bold text-white">投資信託 / 全世界株式</span>
                  <span className="text-[10px] text-indigo-300 bg-indigo-500/15 px-1.5 py-0.2 rounded font-semibold">22.0%</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="font-mono text-xs font-bold text-indigo-300">再投資型</span>
                  <span className={`material-symbols-outlined text-[#bbcabf] text-[18px] transition-transform ${expandedSectors.fund ? 'rotate-180' : ''}`}>
                    expand_more
                  </span>
                </div>
              </button>

              {expandedSectors.fund && (
                <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 mt-1">
                  <div className="flex items-center gap-2">
                    <span className="w-8 h-8 rounded-lg bg-rose-950 flex items-center justify-center font-mono text-[11px] font-bold text-rose-300">ETF</span>
                    <div>
                      <p className="text-xs font-bold text-white">eMAXIS Slim 全世界株式</p>
                      <p className="text-[10px] text-[#bbcabf]">オルカン積立 • 複利再投資枠</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-mono text-xs font-bold text-white">¥0</span>
                    <span className="block text-[9px] text-indigo-300">複利最大化</span>
                  </div>
                </div>
              )}
            </div>
          </section>

          {/* AI Portfolio Health Card */}
          <section className="rounded-2xl bg-[#131b2e] p-4 shadow-xl border border-slate-800 flex flex-col gap-2.5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-lg bg-accent-dim text-accent flex items-center justify-center border border-accent/20">
                  <span className="material-symbols-outlined text-[18px]">verified</span>
                </div>
                <div>
                  <h4 className="text-xs font-bold text-white">AIポートフォリオ診断</h4>
                  <span className="text-[10px] text-[#bbcabf]">分散スコア & リスク評価</span>
                </div>
              </div>
              <span className={`text-xs font-bold px-2.5 py-0.5 rounded-full border ${aiDiagnosis.color}`}>
                健全度: {aiDiagnosis.grade} ({aiDiagnosis.score}/100)
              </span>
            </div>
            <p className="text-xs text-[#bbcabf] leading-relaxed">
              {aiDiagnosis.text}
            </p>
            <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-800">
              <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800/80">
                <span className="text-[10px] text-[#bbcabf] block">増配継続力</span>
                <div className="font-bold text-xs text-accent mt-0.5">{aiDiagnosis.power}</div>
              </div>
              <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800/80">
                <span className="text-[10px] text-[#bbcabf] block">減配リスク</span>
                <div className="font-bold text-xs text-accent mt-0.5">{aiDiagnosis.risk}</div>
              </div>
            </div>
          </section>
        </div>
      )}

      {/* ================= TAB 2: 配当推移 (COMPUNDING CURVE & HISTORICAL) ================= */}
      {activeTab === 'dividend' && (
        <div className="flex flex-col gap-3.5">
          {/* KPI Badge Card */}
          <section className="rounded-2xl bg-[#131b2e] p-4 shadow-xl border border-slate-800 flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-[#4edea3] inline-block" />
                  配当受取額の複利成長推移
                </h3>
                <p className="text-[11px] text-[#bbcabf] mt-0.5">
                  2021年〜2025年(予想) 増配と再投資による累積カーブ
                </p>
              </div>
              <span className="text-[10px] text-[#4edea3] bg-[#10b981]/15 border border-[#4edea3]/30 px-2 py-0.5 rounded-full font-bold">
                5期連続増配達成
              </span>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div className="bg-[#0b1326]/80 p-2 rounded-xl border border-slate-800 flex flex-col items-center text-center">
                <span className="text-[10px] text-[#bbcabf]">5年平均増配率</span>
                <span className="font-mono text-base text-[#4edea3] font-bold mt-0.5">+21.4%</span>
                <span className="text-[9px] text-[#bbcabf]">年平均CAGR</span>
              </div>
              <div className="bg-[#0b1326]/80 p-2 rounded-xl border border-slate-800 flex flex-col items-center text-center">
                <span className="text-[10px] text-[#bbcabf]">累計受取総額</span>
                <span className="font-mono text-base text-white font-bold mt-0.5">¥401,500</span>
                <span className="text-[9px] text-[#bbcabf]">通算インカム</span>
              </div>
              <div className="bg-[#0b1326]/80 p-2 rounded-xl border border-slate-800 flex flex-col items-center text-center">
                <span className="text-[10px] text-[#bbcabf]">簿価利回り (YOC)</span>
                <span className="font-mono text-base text-indigo-300 font-bold mt-0.5">4.08%</span>
                <span className="text-[9px] text-[#bbcabf]">取得元本比</span>
              </div>
            </div>
          </section>

          {/* Chart Card */}
          <div className="relative w-full rounded-2xl bg-[#131b2e] p-3.5 shadow-lg border border-slate-800 flex flex-col">
            <div className="flex items-center justify-between pb-2.5 border-b border-slate-800">
              <div className="flex items-center gap-3 text-xs">
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-sm bg-[#4edea3]" />
                  <span className="text-white font-semibold text-[11px]">年次配当額 (受取)</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-1 bg-sky-400 rounded-full inline-block" />
                  <span className="text-[#bbcabf] font-medium text-[11px]">複利成長曲線 (CAGR)</span>
                </div>
              </div>
              <span className="text-[10px] text-[#4edea3] font-bold bg-[#10b981]/15 px-2 py-0.5 rounded-full border border-[#4edea3]/30">
                +12.4% 増配 (2025予想)
              </span>
            </div>

            {/* Strip summary */}
            <div className="grid grid-cols-5 gap-1.5 my-2 text-center">
              {[
                { y: '2021', val: '¥48,000', sub: '基準期' },
                { y: '2022', val: '¥66,000', sub: '+37.5%' },
                { y: '2023', val: '¥87,000', sub: '+31.8%' },
                { y: '2024', val: '¥110,800', sub: '+27.4%' },
                { y: '2025予', val: '¥124,500', sub: '+12.4%' }
              ].map(st => (
                <div
                  key={st.y}
                  onClick={() => setSelectedDivYear(st.y.replace('予', ''))}
                  className={`flex flex-col items-center p-1 rounded-md border cursor-pointer transition-all ${
                    selectedDivYear === st.y.replace('予', '')
                      ? 'bg-[#10b981]/20 border-[#4edea3] text-white'
                      : 'bg-[#0b1326]/60 border-slate-800 text-[#bbcabf]'
                  }`}
                >
                  <span className="text-[8px] text-[#bbcabf]">{st.y}</span>
                  <span className="text-[9px] font-bold font-mono">{st.val}</span>
                  <span className="text-[7.5px] text-[#4edea3] font-bold">{st.sub}</span>
                </div>
              ))}
            </div>

            {/* SVG Chart: Bars + Spline Curve */}
            <div className="relative w-full h-44">
              <svg viewBox="0 0 320 150" preserveAspectRatio="none" className="w-full h-full overflow-visible">
                <defs>
                  <linearGradient id="divBarGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#4edea3" stopOpacity="0.95" />
                    <stop offset="100%" stopColor="#10b981" stopOpacity="0.45" />
                  </linearGradient>
                  <linearGradient id="divSplineAreaGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#38bdf8" stopOpacity="0.25" />
                    <stop offset="100%" stopColor="#0b1326" stopOpacity="0" />
                  </linearGradient>
                </defs>

                {/* Horizontal Guide lines */}
                <line x1="5" y1="20" x2="315" y2="20" stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" />
                <text x="6" y="17" fill="#86948a" fontSize="7">¥130,000</text>
                <line x1="5" y1="56" x2="315" y2="56" stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" />
                <text x="6" y="53" fill="#86948a" fontSize="7">¥90,000</text>
                <line x1="5" y1="92" x2="315" y2="92" stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" />
                <text x="6" y="89" fill="#86948a" fontSize="7">¥50,000</text>
                <line x1="5" y1="130" x2="315" y2="130" stroke="rgba(255,255,255,0.12)" />

                {/* Spline Area & Line */}
                <path
                  d="M 31 102 C 60 90, 80 84, 95 78 C 125 68, 145 56, 159 52 C 185 45, 205 32, 223 27 C 250 20, 275 16, 287 14 L 287 130 L 31 130 Z"
                  fill="url(#divSplineAreaGrad)"
                />
                <path
                  d="M 31 102 C 60 90, 80 84, 95 78 C 125 68, 145 56, 159 52 C 185 45, 205 32, 223 27 C 250 20, 275 16, 287 14"
                  fill="none"
                  stroke="#38bdf8"
                  strokeWidth="2"
                  strokeLinecap="round"
                />

                {/* Bars */}
                {[
                  { yr: '2021', x: 20, y: 102, h: 28, cx: 31 },
                  { yr: '2022', x: 84, y: 78, h: 52, cx: 95 },
                  { yr: '2023', x: 148, y: 52, h: 78, cx: 159 },
                  { yr: '2024', x: 212, y: 27, h: 103, cx: 223 },
                  { yr: '2025', x: 276, y: 14, h: 116, cx: 287 }
                ].map(b => (
                  <g
                    key={b.yr}
                    className="cursor-pointer"
                    onClick={() => setSelectedDivYear(b.yr)}
                  >
                    <rect
                      x={b.x}
                      y={b.y}
                      width="22"
                      height={b.h}
                      rx="3"
                      fill="url(#divBarGrad)"
                      className={selectedDivYear === b.yr ? 'filter drop-shadow-[0_0_8px_rgba(78,222,163,0.8)]' : ''}
                    />
                    <circle
                      cx={b.cx}
                      cy={b.y}
                      r={selectedDivYear === b.yr ? 4 : 2.5}
                      fill={selectedDivYear === b.yr ? '#ffffff' : '#38bdf8'}
                      stroke={selectedDivYear === b.yr ? '#4edea3' : 'none'}
                      strokeWidth="1.5"
                    />
                  </g>
                ))}

                {/* X labels */}
                <g className="font-mono text-[9px] fill-[#bbcabf]">
                  <text x="31" y="144" textAnchor="middle">2021</text>
                  <text x="95" y="144" textAnchor="middle">2022</text>
                  <text x="159" y="144" textAnchor="middle">2023</text>
                  <text x="223" y="144" textAnchor="middle">2024</text>
                  <text x="287" y="144" textAnchor="middle" className="font-bold fill-[#4edea3]">2025予</text>
                </g>
              </svg>
            </div>

            {/* Inspection Focus Banner */}
            <div className="mt-2.5 p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-[10px] text-[#bbcabf] font-medium flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3] inline-block" />
                  {selectedDivYear}年 {currentDivInfo.status}
                </span>
                <span className="font-mono text-base text-[#4edea3] font-bold mt-0.5">
                  {currentDivInfo.val} / 年
                </span>
              </div>
              <div className="flex flex-col items-end">
                <span className="text-[10px] text-[#bbcabf]">月換算 約{currentDivInfo.monthly} / 月</span>
                <div className="flex items-center gap-1.5 mt-0.5">
                  <span className="text-xs font-bold text-[#4edea3] font-mono">{currentDivInfo.growth}</span>
                  <span className="text-[10px] text-[#4edea3] font-bold bg-[#10b981]/15 px-1.5 py-0.2 rounded">
                    {currentDivInfo.pct}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Year-by-Year Cards */}
          <section className="flex flex-col gap-2">
            <h4 className="text-xs font-bold text-white flex items-center gap-1.5 px-1">
              <span className="material-symbols-outlined text-[#4edea3] text-[18px]">calendar_month</span>
              年別インカム詳細・月割シミュレーション
            </h4>

            {[
              { yr: '2025', badge: '過去最高配当', monthly: '¥10,375', yoc: '4.08%', total: '¥124,500', growth: '+12.4% 前年比', isProjected: true },
              { yr: '2024', badge: '確定', monthly: '¥9,233', yoc: '3.62%', total: '¥110,800', growth: '+27.4% 前年比', isProjected: false },
              { yr: '2023', badge: '確定', monthly: '¥7,250', yoc: '3.45%', total: '¥87,000', growth: '+31.8% 前年比', isProjected: false },
              { yr: '2022', badge: '確定', monthly: '¥5,500', yoc: '3.20%', total: '¥66,000', growth: '+37.5% 前年比', isProjected: false },
              { yr: '2021', badge: '基準年', monthly: '¥4,000', yoc: '3.00%', total: '¥48,000', growth: '基準年', isProjected: false }
            ].map(row => (
              <article
                key={row.yr}
                onClick={() => setSelectedDivYear(row.yr)}
                className={`p-3 rounded-2xl bg-[#131b2e] border transition-all cursor-pointer flex items-center justify-between ${
                  selectedDivYear === row.yr ? 'border-[#4edea3]/50 bg-[#171f33]' : 'border-slate-800'
                }`}
              >
                <div className="flex items-center gap-3">
                  <div className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold text-xs font-mono shrink-0 ${
                    row.isProjected ? 'bg-[#10b981]/20 text-[#4edea3]' : 'bg-slate-800 text-white'
                  }`}>
                    {row.isProjected ? "'25予" : `'${row.yr.slice(2)}`}
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h4 className="text-xs font-bold text-white">{row.yr}年 {row.isProjected ? '予想' : '実績'}</h4>
                      <span className="text-[10px] bg-[#10b981]/15 text-[#4edea3] px-1.5 py-0.2 rounded font-semibold">
                        {row.badge}
                      </span>
                    </div>
                    <span className="text-[11px] text-[#bbcabf]">
                      月換算 約{row.monthly} / 月 • YOC {row.yoc}
                    </span>
                  </div>
                </div>
                <div className="text-right">
                  <span className={`font-mono text-xs font-bold ${row.isProjected ? 'text-[#4edea3]' : 'text-white'}`}>
                    {row.total}
                  </span>
                  <span className="block text-[10px] text-[#4edea3] font-bold">{row.growth}</span>
                </div>
              </article>
            ))}
          </section>
        </div>
      )}

      {/* ================= TAB 3: 資産推移 (ASSET VIEW) ================= */}
      {activeTab === 'asset' && (
        <div className="flex flex-col gap-3.5">
          {/* Header with 5Y vs 12M toggle */}
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
                <span className="material-symbols-outlined text-accent text-[18px]">ssid_chart</span>
                資産・配当金推移
              </h3>
              <p className="text-[10px] text-[#bbcabf] mt-0.5">
                {assetPeriod === '5y' ? '2021年〜2025年(現在) • 年次成長トラッキング' : '2024年1月〜12月 • 月次複利スプライン推移'}
              </p>
            </div>

            <div className="inline-flex p-0.5 rounded-xl bg-[#171f33] border border-slate-800/80 shadow-inner">
              <button
                type="button"
                onClick={() => setAssetPeriod('5y')}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  assetPeriod === '5y' ? 'bg-accent text-black shadow font-extrabold' : 'text-[#bbcabf] hover:text-white'
                }`}
              >
                5年
              </button>
              <button
                type="button"
                onClick={() => setAssetPeriod('12m')}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  assetPeriod === '12m' ? 'bg-accent text-black shadow font-extrabold' : 'text-[#bbcabf] hover:text-white'
                }`}
              >
                12ヶ月
              </button>
            </div>
          </div>

          {/* Metrics summary */}
          <div className="grid grid-cols-2 gap-2.5">
            <div className="flex flex-col p-3 rounded-2xl bg-[#131b2e] border border-slate-800/80 shadow-sm relative overflow-hidden">
              <div className="absolute -top-6 -left-6 w-16 h-16 rounded-full bg-accent/5 blur-xl pointer-events-none" />
              <div className="flex items-center gap-1.5 justify-between">
                <span className="text-[11px] text-[#bbcabf] font-semibold flex items-center gap-1">
                  <span className="material-symbols-outlined text-[13px] text-accent">account_balance</span>
                  資産評価額
                </span>
                <span className="text-[9px] text-accent bg-accent-dim px-1.5 py-0.5 rounded-full font-bold">+14.7%</span>
              </div>
              <span className="font-mono text-xl sm:text-2xl font-bold text-white mt-1.5">¥3,500,000</span>
              <span className="text-[10px] text-[#bbcabf] mt-1">投資元本 ¥3,050,000</span>
            </div>

            <div className="flex flex-col p-3 rounded-2xl bg-[#131b2e] border border-slate-800/80 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="text-[11px] text-[#bbcabf] font-semibold flex items-center gap-1">
                  <span className="material-symbols-outlined text-[13px] text-accent">trending_up</span>
                  通算含み益
                </span>
                <span className="text-[10px] text-accent font-bold">簿価利回り</span>
              </div>
              <span className="font-mono text-xl sm:text-2xl font-bold text-accent mt-1.5">+¥450,000</span>
              <span className="text-[10px] text-[#bbcabf] mt-1">Yield on Cost: <strong className="text-accent font-bold">4.08%</strong></span>
            </div>
          </div>

          {/* 5-Year Step-Ladder Bar Chart */}
          {assetPeriod === '5y' && (
            <div className="p-4 rounded-2xl bg-[#131b2e] border border-slate-800 flex flex-col gap-3.5 shadow-md">
              <div className="flex items-center justify-between text-xs pb-2 border-b border-slate-800/80">
                <div className="flex items-center gap-2.5">
                  <span className="flex items-center gap-1 text-white font-medium text-[11px]">
                    <span className="w-2.5 h-2.5 rounded-sm bg-accent inline-block" />
                    評価額 (時価)
                  </span>
                  <span className="flex items-center gap-1 text-[#bbcabf] font-medium text-[11px]">
                    <span className="w-2.5 h-2.5 rounded-sm bg-slate-700 inline-block" />
                    累積投資元本 (簿価)
                  </span>
                </div>
                {/* Dynamic Selection Pill */}
                <span className="text-[10px] text-accent font-bold font-mono bg-accent-dim px-2 py-0.5 rounded-full border border-accent/20">
                  含み益 +{selectedAssetYear === '2021' ? '¥50,000 (+6.2%)' : selectedAssetYear === '2022' ? '¥130,000 (+9.0%)' : selectedAssetYear === '2023' ? '¥320,000 (+15.2%)' : selectedAssetYear === '2024' ? '¥430,000 (+15.6%)' : '¥450,000 (+14.7%)'}
                </span>
              </div>

              {/* 5-Year SVG Step Ladder + Dynamic Top Tooltips */}
              <div className="relative w-full pt-12 pb-2">
                {/* HTML Float Tooltips matching the image exactly */}
                <div className="absolute inset-x-0 top-0 grid grid-cols-5 gap-1 text-center pointer-events-none">
                  {[
                    { yr: '2021', val: '¥85万', diff: '+¥5万', pct: '+6.2%' },
                    { yr: '2022', val: '¥158万', diff: '+¥13万', pct: '+9.0%' },
                    { yr: '2023', val: '¥242万', diff: '+¥32万', pct: '+15.2%' },
                    { yr: '2024', val: '¥318万', diff: '+¥43万', pct: '+15.6%' },
                    { yr: '2025', val: '¥350万', diff: '+¥45万', pct: '+14.7%', isCurrent: true }
                  ].map(item => {
                    const isSelected = selectedAssetYear === item.yr;
                    return (
                      <div
                        key={item.yr}
                        className={`flex flex-col items-center justify-center p-1 rounded-lg text-[8px] leading-tight transition-all duration-200 ${
                          isSelected
                            ? 'bg-[#0f172a] border border-accent/80 shadow-md text-white'
                            : 'bg-[#0b1326]/40 text-[#bbcabf]'
                        }`}
                      >
                        <span className="font-bold">時価: {item.val}</span>
                        <span className="text-accent font-bold">{item.diff}</span>
                        <span className="text-accent font-extrabold">{item.pct}</span>
                      </div>
                    );
                  })}
                </div>

                <svg viewBox="0 0 320 150" preserveAspectRatio="none" className="w-full h-36 overflow-visible">
                  <defs>
                    <linearGradient id="assetBarGradV3" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.95" />
                      <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.55" />
                    </linearGradient>
                    <linearGradient id="costBarGradV3" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#31394d" stopOpacity="0.95" />
                      <stop offset="100%" stopColor="#171f33" stopOpacity="0.85" />
                    </linearGradient>
                  </defs>

                  <line x1="5" y1="18" x2="315" y2="18" stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" />
                  <text x="6" y="15" fill="#86948a" fontSize="7">¥360万</text>
                  <line x1="5" y1="55" x2="315" y2="55" stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" />
                  <text x="6" y="52" fill="#86948a" fontSize="7">¥240万</text>
                  <line x1="5" y1="92" x2="315" y2="92" stroke="rgba(255,255,255,0.06)" strokeDasharray="3 3" />
                  <text x="6" y="89" fill="#86948a" fontSize="7">¥120万</text>
                  <line x1="5" y1="130" x2="315" y2="130" stroke="rgba(255,255,255,0.12)" />

                  {/* Step Ladder Dash Path */}
                  <path d="M 23 103 L 43 103 L 87 79 L 107 79 L 151 56 L 171 56 L 215 34 L 235 34 L 279 23 L 299 23" fill="none" stroke="#86948a" strokeDasharray="2 2" strokeWidth="1.2" />

                  {/* Pairs */}
                  {[
                    { yr: '2021', cx1: 14, cx2: 32, costH: 27, costY: 103, valH: 29, valY: 101 },
                    { yr: '2022', cx1: 78, cx2: 96, costH: 51, costY: 79, valH: 56, valY: 74 },
                    { yr: '2023', cx1: 142, cx2: 160, costH: 74, costY: 56, valH: 85, valY: 45 },
                    { yr: '2024', cx1: 206, cx2: 224, costH: 96, costY: 34, valH: 111, valY: 19 },
                    { yr: '2025', cx1: 270, cx2: 288, costH: 107, costY: 23, valH: 122, valY: 8 }
                  ].map(p => (
                    <g key={p.yr} className="cursor-pointer" onClick={() => setSelectedAssetYear(p.yr)}>
                      <rect x={p.cx1} y={p.costY} width="16" height={p.costH} rx="3" fill="url(#costBarGradV3)" />
                      <rect x={p.cx2} y={p.valY} width="16" height={p.valH} rx="3" fill="url(#assetBarGradV3)" className={selectedAssetYear === p.yr ? 'filter drop-shadow-[0_0_8px_var(--accent-glow)]' : ''} />
                    </g>
                  ))}

                  <g className="font-mono text-[9px] fill-[#bbcabf]">
                    <text x="31" y="144" textAnchor="middle">2021</text>
                    <text x="95" y="144" textAnchor="middle">2022</text>
                    <text x="159" y="144" textAnchor="middle">2023</text>
                    <text x="223" y="144" textAnchor="middle">2024</text>
                    <text x="287" y="144" textAnchor="middle" className="font-bold fill-accent">2025現</text>
                  </g>
                </svg>
              </div>

              {/* Data Grid with natural spacing */}
              <div>
                <div className="flex items-center justify-between text-[11px] text-[#bbcabf] px-1 mb-1.5 font-medium">
                  <span>年次推移データグリッド (元本 vs 時価対比)</span>
                  <span className="text-accent flex items-center gap-0.5 text-[10px]">
                    <span className="material-symbols-outlined text-[13px] animate-pulse">touch_app</span>タップで選択
                  </span>
                </div>
                <div className="grid grid-cols-5 gap-1.5 text-[9.5px] font-mono">
                  {[
                    { yr: '2021', cost: '80万', val: '85万', diff: '+5万' },
                    { yr: '2022', cost: '145万', val: '158万', diff: '+13万' },
                    { yr: '2023', cost: '210万', val: '242万', diff: '+32万' },
                    { yr: '2024', cost: '275万', val: '318万', diff: '+43万' },
                    { yr: '2025', cost: '305万', val: '350万', diff: '+45万', label: '2025現' }
                  ].map(cell => {
                    const isSelected = selectedAssetYear === cell.yr;
                    return (
                      <div
                        key={cell.yr}
                        onClick={() => setSelectedAssetYear(cell.yr)}
                        className={`flex flex-col p-2 rounded-xl border transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-accent-dim border-accent text-accent shadow-sm'
                            : 'bg-[#0b1326]/60 border-slate-800 text-[#bbcabf] hover:bg-[#0b1326]'
                        }`}
                      >
                        <span className="font-bold text-center block text-[10px] text-white border-b border-slate-800/60 pb-0.5 mb-1">
                          {cell.label || cell.yr}
                        </span>
                        <span className="text-[8px] text-[#bbcabf] truncate">元: {cell.cost}</span>
                        <span className="font-bold text-white truncate">時: {cell.val}</span>
                        <span className="text-[8.5px] text-accent font-bold text-right mt-0.5">{cell.diff}</span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Dynamic summary block matching the image exactly */}
              <div className="p-3.5 rounded-xl bg-[#0b1326] border border-slate-800/80 flex flex-col gap-2 mt-1">
                <div className="flex items-center justify-between text-xs text-[#bbcabf] font-semibold">
                  <span className="text-white">
                    {selectedAssetYear === '2025' ? '2025年 現在' : `${selectedAssetYear}年 実績`}
                  </span>
                  <span className="font-mono">
                    投資元本 ¥{selectedAssetYear === '2021' ? '800,000' : selectedAssetYear === '2022' ? '1,450,000' : selectedAssetYear === '2023' ? '2,100,000' : selectedAssetYear === '2024' ? '2,750,000' : '3,050,000'}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-base font-black text-white">
                    評価額: ¥{selectedAssetYear === '2021' ? '850,000' : selectedAssetYear === '2022' ? '1,580,000' : selectedAssetYear === '2023' ? '2,420,000' : selectedAssetYear === '2024' ? '3,180,000' : '3,500,000'}
                  </span>
                  <div className="flex items-center gap-1.5">
                    <span className="font-mono text-xs font-bold text-accent">
                      含み益 {selectedAssetYear === '2021' ? '+¥50,000' : selectedAssetYear === '2022' ? '+¥130,000' : selectedAssetYear === '2023' ? '+¥320,000' : selectedAssetYear === '2024' ? '+¥430,000' : '+¥450,000'}
                    </span>
                    <span className="text-[9.5px] text-accent bg-accent-dim px-2 py-0.5 rounded-full font-bold">
                      {selectedAssetYear === '2021' ? '+6.2%' : selectedAssetYear === '2022' ? '+9.0%' : selectedAssetYear === '2023' ? '+15.2%' : selectedAssetYear === '2024' ? '+15.6%' : '+14.7%'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Future 30-Year Compounding Growth Simulator (High density) */}
              <div className="p-4 rounded-2xl bg-gradient-to-br from-[#131b2e] to-[#0f172a] border border-accent/30 flex flex-col gap-3.5 mt-3 shadow-lg shadow-accent/5">
                <div className="flex items-center justify-between pb-1.5 border-b border-slate-800">
                  <div className="flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-accent text-[20px] animate-pulse">hourglass_empty</span>
                    <div className="text-left">
                      <h4 className="font-bold text-xs text-white">未来30年・複利成長シミュレーター</h4>
                      <span className="text-[9.5px] text-[#bbcabf] block">毎月の積立額と配当再投資による複利試算</span>
                    </div>
                  </div>
                  <span className="text-[9px] bg-accent/25 text-accent border border-accent/30 px-2 py-0.5 rounded-full font-bold">
                    複利シミュレーション
                  </span>
                </div>

                {/* Input Fields Grid */}
                <div className="grid grid-cols-2 gap-3 text-xs text-left">
                  {/* Monthly Deposit Slider & Input */}
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between">
                      <span className="text-[#bbcabf] font-semibold">毎月の新規積立額:</span>
                      <strong className="text-white font-mono">¥{(simMonthlyDeposit).toLocaleString()}</strong>
                    </div>
                    <input
                      type="range"
                      min="5000"
                      max="150000"
                      step="5000"
                      value={simMonthlyDeposit}
                      onChange={e => setSimMonthlyDeposit(parseInt(e.target.value) || 0)}
                      className="w-full h-1 bg-slate-800 rounded-lg appearance-none cursor-pointer"
                    />
                    {/* Presets */}
                    <div className="grid grid-cols-3 gap-1">
                      {[10000, 30000, 50000].map(amt => (
                        <button
                          key={amt}
                          type="button"
                          onClick={() => setSimMonthlyDeposit(amt)}
                          className={`py-0.5 rounded text-[9px] font-bold ${
                            simMonthlyDeposit === amt
                              ? 'bg-accent text-black font-black'
                              : 'bg-[#171f33] text-[#bbcabf] hover:text-white border border-slate-800'
                          }`}
                        >
                          ¥{amt / 1000}万
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Expected Yield Slider */}
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between">
                      <span className="text-[#bbcabf] font-semibold">想定配当利回り:</span>
                      <strong className="text-accent font-mono">{simYield}%</strong>
                    </div>
                    <input
                      type="range"
                      min="1.0"
                      max="8.0"
                      step="0.1"
                      value={simYield}
                      onChange={e => setSimYield(parseFloat(e.target.value) || 0)}
                      className="w-full h-1 bg-slate-800 rounded-lg appearance-none cursor-pointer"
                    />
                    {/* Presets */}
                    <div className="grid grid-cols-3 gap-1">
                      {[3.0, 4.0, 5.0].map(y => (
                        <button
                          key={y}
                          type="button"
                          onClick={() => setSimYield(y)}
                          className={`py-0.5 rounded text-[9px] font-bold ${
                            simYield === y
                              ? 'bg-accent text-black font-black'
                              : 'bg-[#171f33] text-[#bbcabf] hover:text-white border border-slate-800'
                          }`}
                        >
                          {y}%
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Reinvestment Toggle */}
                <div className="p-2.5 rounded-xl bg-[#0b1326]/60 border border-slate-800/80 flex items-center justify-between text-left">
                  <div>
                    <span className="text-xs font-bold text-white block">配当金を自動で再投資する (複利効果)</span>
                    <span className="text-[10px] text-[#bbcabf] mt-0.5 block">税引後(20.315%控除)の配当金を元本に自動組み入れ</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSimReinvest(!simReinvest)}
                    className={`w-10 h-5 rounded-full p-0.5 transition-colors shrink-0 ${simReinvest ? 'bg-accent' : 'bg-slate-700'}`}
                  >
                    <span className={`block w-4 h-4 rounded-full bg-white shadow transition-transform ${simReinvest ? 'translate-x-5' : 'translate-x-0'}`} />
                  </button>
                </div>

                {/* Projection Timeline Row */}
                <div className="space-y-2">
                  <span className="text-[10px] text-[#bbcabf] font-bold uppercase tracking-wider block text-left">複利成長シミュレーション結果</span>
                  <div className="grid grid-cols-3 gap-2 text-center text-xs">
                    {/* 10y */}
                    <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 flex flex-col items-center">
                      <span className="text-[9px] text-[#bbcabf] font-bold">10年後 (2035)</span>
                      <span className="font-mono text-[13px] font-extrabold text-white mt-1">
                        ¥{(proj10.assets / 10000).toFixed(0)}万
                      </span>
                      <span className="text-[8.5px] text-[#bbcabf] mt-0.5">元本: ¥{(proj10.invested / 10000).toFixed(0)}万</span>
                      <div className="w-full border-t border-slate-800/60 mt-1.5 pt-1">
                        <span className="text-[8px] text-accent block font-medium">月配当(目安)</span>
                        <span className="font-mono text-xs text-accent font-bold">
                          ¥{proj10.monthlyPassive.toLocaleString()}
                        </span>
                        <span className="text-[8px] text-[#bbcabf]">/月</span>
                      </div>
                    </div>

                    {/* 20y */}
                    <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 flex flex-col items-center relative overflow-hidden">
                      <div className="absolute top-0 right-0 w-3 h-3 bg-accent/20 rounded-bl-lg pointer-events-none" />
                      <span className="text-[9px] text-[#bbcabf] font-bold">20年後 (2045)</span>
                      <span className="font-mono text-[13px] font-extrabold text-white mt-1">
                        ¥{(proj20.assets / 10000).toFixed(0)}万
                      </span>
                      <span className="text-[8.5px] text-[#bbcabf] mt-0.5">元本: ¥{(proj20.invested / 10000).toFixed(0)}万</span>
                      <div className="w-full border-t border-slate-800/60 mt-1.5 pt-1">
                        <span className="text-[8px] text-accent block font-medium">月配当(目安)</span>
                        <span className="font-mono text-xs text-accent font-bold">
                          ¥{proj20.monthlyPassive.toLocaleString()}
                        </span>
                        <span className="text-[8px] text-[#bbcabf]">/月</span>
                      </div>
                    </div>

                    {/* 30y */}
                    <div className="p-2.5 rounded-xl bg-[#10b981]/5 border border-accent/30 flex flex-col items-center">
                      <span className="text-[9px] text-accent font-black">30年後 (2055)</span>
                      <span className="font-mono text-sm font-black text-white mt-1">
                        ¥{(proj30.assets / 10000).toFixed(0)}万
                      </span>
                      <span className="text-[8.5px] text-[#bbcabf] mt-0.5">元本: ¥{(proj30.invested / 10000).toFixed(0)}万</span>
                      <div className="w-full border-t border-accent/20 mt-1.5 pt-1">
                        <span className="text-[8px] text-accent block font-medium">月配当(目安)</span>
                        <span className="font-mono text-xs text-accent font-extrabold">
                          ¥{proj30.monthlyPassive.toLocaleString()}
                        </span>
                        <span className="text-[8px] text-[#bbcabf]">/月</span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Educational Banner */}
                <div className="p-2 rounded-xl bg-[#171f33]/40 border border-slate-800/60 text-[10px] text-[#bbcabf] leading-relaxed flex gap-1.5 items-start text-left">
                  <span className="material-symbols-outlined text-[13px] text-accent shrink-0 mt-0.5">info</span>
                  <p>
                    現在の資産評価額 ¥350万から、毎月 <strong className="text-white">¥{simMonthlyDeposit.toLocaleString()}</strong> を積立、利回り <strong className="text-accent">{simYield}%</strong> で運用し、さらに配当金を再投資し続けた場合のシミュレーションです。
                    {simReinvest && (
                      <span> 複利効果により、30年間の累計不労所得・資産価値は加速度的に成長します。</span>
                    )}
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* 12-Month Continuous Spline View */}
          {assetPeriod === '12m' && (
            <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 flex flex-col gap-2">
              <div className="flex items-center justify-between text-xs pb-1 border-b border-slate-800">
                <span className="text-white font-semibold">月次資産スプライン曲線</span>
                <span className="text-[10px] text-accent font-mono">積立元本ライン (月+¥2.5万)</span>
              </div>

              <div className="relative w-full h-44 pt-2">
                <svg viewBox="0 0 330 160" preserveAspectRatio="none" className="w-full h-full overflow-visible">
                  <defs>
                    <linearGradient id="splineAreaGrad2" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.4" />
                      <stop offset="60%" stopColor="var(--accent)" stopOpacity="0.1" />
                      <stop offset="100%" stopColor="#0b1326" stopOpacity="0" />
                    </linearGradient>
                  </defs>

                  <path d="M 18 115 L 45 111 L 72 107 L 99 103 L 126 99 L 153 95 L 180 91 L 207 87 L 234 83 L 261 79 L 288 74 L 315 68" fill="none" stroke="#86948a" strokeDasharray="3 3" strokeWidth="1.5" />
                  <path d="M 18 110 C 32 108, 40 102, 45 102 C 58 102, 65 96, 72 95 C 85 93, 92 88, 99 87 C 112 85, 119 82, 126 80 C 139 76, 146 72, 153 70 C 166 66, 173 63, 180 61 C 193 57, 200 52, 207 50 C 220 46, 227 44, 234 43 C 247 41, 254 35, 261 34 C 274 32, 281 28, 288 27 C 301 24, 308 22, 315 22 L 315 138 L 18 138 Z" fill="url(#splineAreaGrad2)" />
                  <path d="M 18 110 C 32 108, 40 102, 45 102 C 58 102, 65 96, 72 95 C 85 93, 92 88, 99 87 C 112 85, 119 82, 126 80 C 139 76, 146 72, 153 70 C 166 66, 173 63, 180 61 C 193 57, 200 52, 207 50 C 220 46, 227 44, 234 43 C 247 41, 254 35, 261 34 C 274 32, 281 28, 288 27 C 301 24, 308 22, 315 22" fill="none" stroke="var(--accent)" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" />

                  {/* Scrubber points */}
                  {[
                    { cx: 18, cy: 110 }, { cx: 45, cy: 102 }, { cx: 72, cy: 95 },
                    { cx: 99, cy: 87 }, { cx: 126, cy: 80 }, { cx: 153, cy: 70 },
                    { cx: 180, cy: 61 }, { cx: 207, cy: 50 }, { cx: 234, cy: 43 },
                    { cx: 261, cy: 34 }, { cx: 288, cy: 27 }, { cx: 315, cy: 22 }
                  ].map((pt, i) => (
                    <circle key={i} cx={pt.cx} cy={pt.cy} r="3.5" fill="var(--accent)" className="hover:r-5 cursor-pointer" />
                  ))}

                  <g className="font-mono text-[9px] fill-[#bbcabf]">
                    <text x="18" y="152" textAnchor="middle">1月</text>
                    <text x="99" y="152" textAnchor="middle">4月</text>
                    <text x="180" y="152" textAnchor="middle">7月</text>
                    <text x="261" y="152" textAnchor="middle">10月</text>
                    <text x="315" y="152" textAnchor="middle" className="font-bold fill-accent">12月</text>
                  </g>
                </svg>
              </div>

              <div className="flex items-center justify-between text-[11px] text-[#bbcabf] pt-1">
                <span>12月 時価: <strong className="text-white font-mono">¥3,500,000</strong></span>
                <span className="text-accent font-bold">年間累計含み益 +¥450,000</span>
              </div>
            </div>
          )}

          {/* Expanded descending history block exactly matching Image 1 */}
          <section className="space-y-2.5">
            <div className="flex items-center justify-between px-1">
              <h3 className="text-xs font-bold text-white flex items-center gap-1.5 uppercase tracking-wide">
                <span className="material-symbols-outlined text-accent text-[20px]">history</span>
                年次推移・元本 vs 評価額
              </h3>
              <span className="text-[10px] text-[#bbcabf] font-mono">2021〜2025</span>
            </div>

            <div className="flex flex-col gap-2">
              {/* 2025年 現在 */}
              <article className="p-3.5 rounded-2xl bg-[#131b2e] border border-accent/40 shadow-sm shadow-accent/5 flex items-center justify-between transition-transform hover:scale-[1.01]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-accent-dim border border-accent/60 flex items-center justify-center font-mono text-[11px] font-bold text-accent shrink-0">
                    '25現
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h4 className="font-bold text-sm text-white">2025年 現在</h4>
                      <span className="px-2 py-0.5 rounded-full bg-accent/25 text-accent text-[9px] font-bold">
                        含み益最大
                      </span>
                    </div>
                    <p className="text-[11px] text-[#bbcabf] mt-0.5">
                      元本 ¥3,050,000 · 評価額 ¥3,500,000
                    </p>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <span className="font-mono text-sm font-black text-accent block">
                    +¥450,000
                  </span>
                  <span className="text-[10px] text-accent font-semibold block">
                    +14.7% 利益
                  </span>
                </div>
              </article>

              {/* 2024年 実績 */}
              <article className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800/80 shadow-sm flex items-center justify-between transition-transform hover:scale-[1.01]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-[#171f33] border border-slate-800 flex items-center justify-center font-mono text-[11px] font-bold text-slate-300 shrink-0">
                    '24
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h4 className="font-bold text-sm text-white">2024年 実績</h4>
                      <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#bbcabf] text-[9px] font-bold">
                        確定
                      </span>
                    </div>
                    <p className="text-[11px] text-[#bbcabf] mt-0.5">
                      元本 ¥2,750,000 · 評価額 ¥3,180,000
                    </p>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <span className="font-mono text-sm font-bold text-[#4edea3] block">
                    +¥430,000
                  </span>
                  <span className="text-[10px] text-accent font-semibold block">
                    +15.6% 利益
                  </span>
                </div>
              </article>

              {/* 2023年 実績 */}
              <article className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800/80 shadow-sm flex items-center justify-between transition-transform hover:scale-[1.01]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-[#171f33] border border-slate-800 flex items-center justify-center font-mono text-[11px] font-bold text-slate-300 shrink-0">
                    '23
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h4 className="font-bold text-sm text-white">2023年 実績</h4>
                      <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#bbcabf] text-[9px] font-bold">
                        確定
                      </span>
                    </div>
                    <p className="text-[11px] text-[#bbcabf] mt-0.5">
                      元本 ¥2,100,000 · 評価額 ¥2,420,000
                    </p>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <span className="font-mono text-sm font-bold text-[#4edea3] block">
                    +¥320,000
                  </span>
                  <span className="text-[10px] text-accent font-semibold block">
                    +15.2% 利益
                  </span>
                </div>
              </article>

              {/* 2022年 実績 */}
              <article className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800/80 shadow-sm flex items-center justify-between transition-transform hover:scale-[1.01]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-[#171f33] border border-slate-800 flex items-center justify-center font-mono text-[11px] font-bold text-slate-300 shrink-0">
                    '22
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h4 className="font-bold text-sm text-white">2022年 実績</h4>
                      <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#bbcabf] text-[9px] font-bold">
                        確定
                      </span>
                    </div>
                    <p className="text-[11px] text-[#bbcabf] mt-0.5">
                      元本 ¥1,450,000 · 評価額 ¥1,580,000
                    </p>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <span className="font-mono text-sm font-bold text-[#4edea3] block">
                    +¥130,000
                  </span>
                  <span className="text-[10px] text-accent font-semibold block">
                    +9.0% 利益
                  </span>
                </div>
              </article>

              {/* 2021年 実績 */}
              <article className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800/80 shadow-sm flex items-center justify-between transition-transform hover:scale-[1.01]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-[#171f33] border border-slate-800 flex items-center justify-center font-mono text-[11px] font-bold text-slate-400 shrink-0">
                    '21
                  </div>
                  <div>
                    <h4 className="font-bold text-sm text-white">2021年 実績</h4>
                    <p className="text-[11px] text-[#bbcabf] mt-0.5">
                      元本 ¥800,000 · 評価額 ¥850,000
                    </p>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <span className="font-mono text-sm font-bold text-[#4edea3] block">
                    +¥50,000
                  </span>
                  <span className="text-[10px] text-[#bbcabf] block">
                    基準年
                  </span>
                </div>
              </article>
            </div>
          </section>
        </div>
      )}
    </div>
  );
};
