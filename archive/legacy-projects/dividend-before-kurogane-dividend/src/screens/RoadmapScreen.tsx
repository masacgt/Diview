import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import { roadmapMasterSteps } from '../data/mockData';
import { ShareModal } from '../components/modals/ShareModal';

export const RoadmapScreen: React.FC = () => {
  const { holdings, showToast } = useApp();
  const [openStep, setOpenStep] = useState<number | null>(null);
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);

  // 1. Calculate actual real-time expected dividends from live holdings
  const totalAnnualExpectedDividend = holdings.reduce((sum, h) => {
    // If USD, convert to JPY using standard rate
    const rate = h.currency === '$' ? 150 : 1;
    return sum + Math.round(h.shares * h.dps * rate);
  }, 0);
  const monthlyIncome = Math.round(totalAnnualExpectedDividend / 12);

  // 2. Map static roadmap steps into dynamic reactive structures based on live monthlyIncome
  const dynamicSteps = roadmapMasterSteps.map((step) => {
    const targetMonthly = step.targetMonthly;
    const isCompleted = monthlyIncome >= targetMonthly;
    
    // Determine if previous step is completed to label as challenging/active or locked
    const prevStep = roadmapMasterSteps.find(s => s.step === step.step - 1);
    const isPrevCompleted = prevStep ? (monthlyIncome >= prevStep.targetMonthly) : true;
    
    let status: 'completed' | 'active' | 'locked' = 'locked';
    let statusLabel = 'ロック中';
    let rateLabel = '0%';
    let subStatusLabel = 'LOCKED';
    let currentRate = 0;

    if (isCompleted) {
      status = 'completed';
      statusLabel = '達成済';
      rateLabel = '100%';
      subStatusLabel = 'CLEAR';
      currentRate = 100;
    } else if (isPrevCompleted) {
      status = 'active';
      statusLabel = '挑戦中';
      currentRate = Math.min(99, Math.round((monthlyIncome / targetMonthly) * 100));
      rateLabel = `${currentRate}%`;
      subStatusLabel = 'CHALLENGING';
    } else {
      status = 'locked';
      statusLabel = 'ロック中';
      rateLabel = '0%';
      subStatusLabel = 'LOCKED';
      currentRate = 0;
    }

    const shortageMonthly = Math.max(0, targetMonthly - monthlyIncome);
    const shortageAnnual = shortageMonthly * 12;

    // Dynamically calculate necessary capital for the remaining shortage
    const neededCapitalSim = isCompleted ? null : {
      yield35: `¥${Math.round((shortageAnnual / 0.035) / 10000).toLocaleString()}万`,
      yield40: `¥${Math.round((shortageAnnual / 0.04) / 10000).toLocaleString()}万`,
      yield45: `¥${Math.round((shortageAnnual / 0.045) / 10000).toLocaleString()}万`
    };

    return {
      ...step,
      status,
      statusLabel,
      currentRate,
      rateLabel,
      subStatusLabel,
      shortageMonthly,
      shortageAnnual,
      neededCapitalSim
    };
  });

  // 3. Find highest completed level and current active quest step
  const highestCompletedStep = [...dynamicSteps].reverse().find(s => s.status === 'completed')?.step || 0;
  const currentActiveStepObj = dynamicSteps.find(s => s.status === 'active') || dynamicSteps.find(s => s.status === 'locked') || dynamicSteps[dynamicSteps.length - 1];
  const currentActiveStep = currentActiveStepObj.step;
  const currentLevel = highestCompletedStep >= 10 ? 3 : highestCompletedStep >= 7 ? 3 : highestCompletedStep >= 5 ? 2 : 1;

  const toggleStep = (stepNum: number) => {
    setOpenStep(prev => (prev === stepNum ? null : stepNum));
  };

  const jumpToLevel = (level: number) => {
    if (level === 1) setOpenStep(1);
    if (level === 2) setOpenStep(6);
    if (level === 3) setOpenStep(8);
    const el = document.getElementById(`level-group-${level}`);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-3.5">
      {/* Top Gamification Quest Status Card */}
      <section className="relative w-full rounded-2xl bg-gradient-to-b from-[#222a3d] via-[#171f33] to-[#131b2e] p-4 shadow-xl border border-slate-800 overflow-hidden">
        <div className="absolute -top-14 -right-14 w-40 h-40 bg-gradient-to-br from-[#10b981]/25 to-transparent rounded-full blur-2xl pointer-events-none" />

        {/* Level Badges & Trophy Banner */}
        <div className="flex items-center justify-between gap-2 mb-3 relative z-10">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#10b981]/15 text-[#4edea3] border border-[#4edea3]/30">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#4edea3] opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-[#4edea3]" />
            </span>
            <span className="text-[11px] font-bold tracking-wide">
              {highestCompletedStep >= 10 
                ? '全ステージ完全制覇！' 
                : `LEVEL ${currentLevel} 進行中: STEP ${currentActiveStep}`}
            </span>
          </div>

          <div className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-indigo-950/60 text-indigo-300 text-[11px] font-semibold border border-indigo-800/40">
            <span className="material-symbols-outlined text-[15px] text-amber-400">emoji_events</span>
            <span>
              {highestCompletedStep >= 10 
                ? 'ALL CLEAR (10/10)'
                : highestCompletedStep >= 7 
                ? `LV.3 クリア度 (${highestCompletedStep - 7}/3)`
                : highestCompletedStep >= 5 
                ? `LV.2 クリア度 (${highestCompletedStep - 5}/2)`
                : `LV.1 クリア度 (${highestCompletedStep}/5)`}
            </span>
          </div>
        </div>

        {/* Trophy Alert Card: Dynamic per level */}
        {highestCompletedStep >= 10 ? (
          <div className="relative z-10 flex items-center justify-between p-2.5 mb-3 rounded-xl bg-gradient-to-r from-amber-500/20 via-amber-500/10 to-transparent border border-amber-400/40">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-amber-400/20 text-amber-300 flex items-center justify-center shrink-0 border border-amber-400/30 shadow-sm">
                <span className="material-symbols-outlined text-[22px] text-amber-400">workspace_premium</span>
              </div>
              <div className="flex flex-col">
                <span className="text-xs font-bold text-amber-300 flex items-center gap-1">
                  LEVEL 3 完全制覇＆FIRE達成！ 👑
                </span>
                <span className="text-[10px] text-[#bbcabf]">完全経済的自由期 (STEP 8〜10) を完全クリア！</span>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded bg-amber-400/20 text-amber-300 text-[10px] font-bold shrink-0">
              FIRE 達成
            </span>
          </div>
        ) : highestCompletedStep >= 7 ? (
          <div className="relative z-10 flex items-center justify-between p-2.5 mb-3 rounded-xl bg-gradient-to-r from-purple-500/20 via-purple-500/10 to-transparent border border-purple-400/30">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-purple-400/20 text-purple-300 flex items-center justify-center shrink-0 border border-purple-400/30 shadow-sm">
                <span className="material-symbols-outlined text-[22px] text-purple-400">rocket_launch</span>
              </div>
              <div className="flex flex-col">
                <span className="text-xs font-bold text-purple-300 flex items-center gap-1">
                  LEVEL 2 完全制覇達成！ 🚀
                </span>
                <span className="text-[10px] text-[#bbcabf]">生活費自給・ミニFIRE期 (STEP 6〜7) を攻略完了</span>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded bg-purple-400/20 text-purple-300 text-[10px] font-bold shrink-0">
              LV.2 CLEAR
            </span>
          </div>
        ) : highestCompletedStep >= 5 ? (
          <div className="relative z-10 flex items-center justify-between p-2.5 mb-3 rounded-xl bg-gradient-to-r from-[#10b981]/20 via-[#10b981]/10 to-transparent border border-[#4edea3]/30">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-amber-400/20 text-amber-300 flex items-center justify-center shrink-0 border border-amber-400/30 shadow-sm">
                <span className="material-symbols-outlined text-[22px] text-amber-400">military_tech</span>
              </div>
              <div className="flex flex-col">
                <span className="text-xs font-bold text-[#4edea3] flex items-center gap-1">
                  LEVEL 1 完全制覇達成！
                  <span className="material-symbols-outlined text-[14px]">verified</span>
                </span>
                <span className="text-[10px] text-[#bbcabf]">生活インフラ自給期 (STEP 1〜5) をすべて攻略完了</span>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold shrink-0">
              LV.1 CLEAR
            </span>
          </div>
        ) : (
          <div className="relative z-10 flex items-center justify-between p-2.5 mb-3 rounded-xl bg-slate-800/50 border border-slate-700/60">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-indigo-500/10 text-indigo-300 flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[20px] text-indigo-400">hourglass_empty</span>
              </div>
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white">LEVEL 1 攻略進行中...</span>
                <span className="text-[10px] text-[#bbcabf]">目標: 基礎生活固定費の全額自給 (月3万円)</span>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-300 text-[10px] font-bold shrink-0">
              {highestCompletedStep * 20}% CLEAR
            </span>
          </div>
        )}

        {/* Current Dividends Counter */}
        <div className="flex flex-col gap-0.5 mb-3 relative z-10">
          <span className="text-[10px] uppercase tracking-wider text-[#bbcabf] font-semibold">
            現在の配当獲得ステータス
          </span>
          <div className="flex items-baseline gap-2">
            <span className="font-mono text-2xl sm:text-3xl text-white font-bold tracking-tight">
              ¥{totalAnnualExpectedDividend.toLocaleString()}
            </span>
            <span className="text-xs text-[#bbcabf]">/ 年</span>
          </div>
          <div className="flex items-center gap-1.5 text-xs text-[#bbcabf]">
            <span>月間換算不労所得:</span>
            <span className="font-mono text-base text-[#4edea3] font-bold">
              約 ¥{monthlyIncome.toLocaleString()}
            </span>
            <span>/ 月</span>
          </div>
        </div>

        {/* Quest Progress to Current active Step */}
        <div className="flex flex-col gap-2 p-3 rounded-xl bg-[#0b1326]/80 border border-slate-800 mb-3 relative z-10">
          <div className="flex justify-between items-center text-xs">
            <div className="flex items-center gap-1.5 font-bold text-white">
              <span className="material-symbols-outlined text-[17px] text-[#4edea3]">{currentActiveStepObj.icon || 'star'}</span>
              <span>
                QUEST {currentActiveStep} 目標: <span className="text-[#4edea3]">¥{currentActiveStepObj.targetMonthly.toLocaleString()} / 月</span>
              </span>
            </div>
            <span className="font-mono text-xs font-bold text-[#4edea3]">
              {currentActiveStepObj.currentRate}% 達成
            </span>
          </div>

          <div className="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden p-[1px]">
            <div
              className="h-full bg-gradient-to-r from-[#10b981] via-[#4edea3] to-teal-400 rounded-full shadow-[0_0_10px_rgba(78,222,163,0.5)] transition-all duration-500"
              style={{ width: `${currentActiveStepObj.currentRate}%` }}
            />
          </div>

          <div className="flex justify-between items-center text-[10px] text-[#bbcabf] pt-0.5">
            <span>現在実績: <strong className="text-white font-mono">¥{monthlyIncome.toLocaleString()}</strong></span>
            {currentActiveStepObj.status === 'completed' ? (
              <span className="text-[#4edea3] font-bold flex items-center gap-0.5">
                <span className="material-symbols-outlined text-[13px]">verified</span>
                マイルストーン全クリア！
              </span>
            ) : (
              <span className="text-[#ffb4ab] font-bold flex items-center gap-0.5">
                <span className="material-symbols-outlined text-[13px]">flag</span>
                あと ¥{(currentActiveStepObj.shortageMonthly || 0).toLocaleString()} / 月 で攻略
              </span>
            )}
          </div>
        </div>

        {/* Additional Principal Note */}
        <div className="flex items-start gap-2 p-2.5 rounded-lg bg-[#171f33]/80 mb-3 border border-slate-700/60 text-xs text-[#bbcabf]">
          <span className="material-symbols-outlined text-[#4edea3] text-[18px] shrink-0 mt-0.5">query_stats</span>
          <div className="leading-relaxed">
            {currentActiveStepObj.status === 'completed' ? (
              <span>おめでとうございます！すべての目標マイルストーンをクリアしました。次の投資ステージを切り拓きましょう。</span>
            ) : (
              <span>
                年額 <span className="text-[#4edea3] font-bold">+¥{(currentActiveStepObj.shortageAnnual || 0).toLocaleString()}</span> の配当追加でクリア。利回り <span className="text-white font-semibold">4.0%</span> 想定時、必要な追加投資元本は約 <span className="text-[#4edea3] font-bold underline decoration-[#4edea3]/40">¥{Math.round((currentActiveStepObj.shortageAnnual || 0) / 0.04 / 10000).toLocaleString()}万円</span> です。
              </span>
            )}
          </div>
        </div>

        {/* Action CTAs */}
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => setIsShareModalOpen(true)}
            className="h-10 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] text-xs font-bold flex items-center justify-center gap-1.5 active:scale-95 shadow cursor-pointer"
          >
            <span className="material-symbols-outlined text-[17px]">share</span>
            <span>達成度を共有する</span>
          </button>
          <button
            type="button"
            onClick={() => showToast('達成カード画像を保存しました', 'download_done')}
            className="h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 border border-slate-700/60 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[17px] text-[#4edea3]">download</span>
            <span>画像で保存</span>
          </button>
        </div>
      </section>

      {/* 3-Tier Level Tier Navigator Pills */}
      <section className="flex flex-col gap-2">
        <div className="flex items-center justify-between text-xs px-1">
          <span className="font-bold text-white flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[#4edea3] text-[18px]">hotel_class</span>
            3大ステージ・レベル帯
          </span>
          <span className="text-[10px] text-[#bbcabf]">タップしてジャンプ</span>
        </div>

        <div className="grid grid-cols-3 gap-2">
          <button
            type="button"
            onClick={() => jumpToLevel(1)}
            className={`flex flex-col items-center p-2 rounded-xl border text-center active:scale-95 transition-all cursor-pointer ${
              currentLevel === 1
                ? 'bg-[#171f33] border-2 border-[#4edea3] shadow-[0_0_12px_rgba(78,222,163,0.25)]'
                : 'bg-[#131b2e] border-slate-800'
            }`}
          >
            <div className="flex items-center gap-1 text-[11px] font-bold text-[#4edea3]">
              <span>LV.1 {highestCompletedStep >= 5 ? '制覇' : '挑戦中'}</span>
              <span className="material-symbols-outlined text-[13px]">
                {highestCompletedStep >= 5 ? 'check_circle' : 'play_circle'}
              </span>
            </div>
            <span className="text-[10px] text-[#bbcabf] truncate w-full mt-0.5">インフラ自給期</span>
            <span className="text-[10px] text-[#4edea3] font-bold">STEP 1-5</span>
          </button>

          <button
            type="button"
            onClick={() => jumpToLevel(2)}
            className={`flex flex-col items-center p-2 rounded-xl border text-center active:scale-95 transition-all cursor-pointer ${
              currentLevel === 2
                ? 'bg-[#171f33] border-2 border-[#4edea3] shadow-[0_0_12px_rgba(78,222,163,0.25)]'
                : 'bg-[#131b2e] border-slate-800'
            }`}
          >
            <div className={`flex items-center gap-1 text-[11px] font-bold ${currentLevel === 2 ? 'text-[#4edea3] animate-pulse' : 'text-[#bbcabf]'}`}>
              <span>LV.2 {highestCompletedStep >= 7 ? '制覇' : '挑戦中'}</span>
              <span className="material-symbols-outlined text-[13px]">
                {highestCompletedStep >= 7 ? 'check_circle' : 'play_circle'}
              </span>
            </div>
            <span className="text-[10px] text-white font-semibold truncate w-full mt-0.5">生活費・ミニFIRE</span>
            <span className="text-[10px] text-[#4edea3] font-bold">STEP 6-7</span>
          </button>

          <button
            type="button"
            onClick={() => jumpToLevel(3)}
            className="flex flex-col items-center p-2 rounded-xl bg-[#131b2e] border border-slate-800 text-center active:scale-95 transition-all opacity-85 cursor-pointer"
          >
            <div className="flex items-center gap-1 text-[11px] font-medium text-[#bbcabf]">
              <span>LV.3 ロック</span>
              <span className="material-symbols-outlined text-[13px]">lock</span>
            </div>
            <span className="text-[10px] text-[#bbcabf] truncate w-full mt-0.5">完全FIRE期</span>
            <span className="text-[10px] text-[#bbcabf]">STEP 8-10</span>
          </button>
        </div>
      </section>

      {/* Full 10 Milestones Quest Map */}
      <section className="flex flex-col gap-3.5">
        <h3 className="font-bold text-sm text-white flex items-center gap-1.5 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[20px]">assignment_turned_in</span>
          全10マイルストーン・クエストマップ
        </h3>

        {/* Level 1 Group: STEP 1-5 */}
        <div id="level-group-1" className={`flex flex-col gap-2 p-3 rounded-2xl bg-[#131b2e] border ${highestCompletedStep >= 5 ? 'border-[#10b981]/50' : 'border-slate-800'}`}>
          <div className="flex items-center justify-between pb-1 px-1">
            <span className="text-xs font-bold text-[#4edea3] flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px]">bolt</span>
              LEVEL 1: 生活インフラ自給期
            </span>
            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold flex items-center gap-1 ${highestCompletedStep >= 5 ? 'bg-[#10b981]/20 text-[#4edea3]' : 'bg-slate-800 text-[#bbcabf]'}`}>
              <span className="material-symbols-outlined text-[12px]">done_all</span>
              {Math.min(5, highestCompletedStep)}/5 CLEAR
            </span>
          </div>

          {dynamicSteps.filter(s => s.level === 1).map(step => (
            <div
              key={step.step}
              className="rounded-xl overflow-hidden bg-[#171f33] border border-slate-800/80 transition-all"
            >
              <div
                onClick={() => toggleStep(step.step)}
                className="flex items-center justify-between p-3 cursor-pointer hover:bg-slate-700/60 active:scale-[0.99] transition-all"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${step.status === 'completed' ? 'bg-[#10b981]/15 text-[#4edea3]' : 'bg-slate-800 text-[#bbcabf]'}`}>
                    <span className="material-symbols-outlined text-[18px]">{step.icon}</span>
                  </div>
                  <div className="truncate">
                    <div className="flex items-center gap-1.5">
                      <span className="font-bold text-xs text-white truncate">{step.name}</span>
                      <span className={`text-[9px] px-1.5 py-0.2 rounded font-bold ${
                        step.status === 'completed'
                          ? 'bg-[#10b981]/20 text-[#4edea3]'
                          : step.status === 'active'
                          ? 'bg-[#f59e0b]/20 text-[#fbbf24]'
                          : 'bg-slate-800 text-[#bbcabf]'
                      }`}>
                        {step.statusLabel}
                      </span>
                    </div>
                    <span className="text-[10px] text-[#bbcabf] truncate block">{step.title}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <span className={`font-mono text-xs font-bold ${step.status === 'completed' ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                    {step.rateLabel}
                  </span>
                  <span className={`material-symbols-outlined text-[18px] text-[#bbcabf] transition-transform ${openStep === step.step ? 'rotate-180' : ''}`}>
                    expand_more
                  </span>
                </div>
              </div>

              {openStep === step.step && (
                <div className="px-3 pb-3 pt-1 border-t border-slate-800 text-xs text-[#bbcabf] space-y-2">
                  <p className="leading-relaxed text-[11px]">{step.description}</p>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="p-2 rounded-lg bg-[#0b1326]">
                      <span className="text-[9px] block">目標月額 / 年額換算</span>
                      <span className="font-bold text-white text-xs">¥{step.targetMonthly.toLocaleString()} /月</span>
                      <span className="text-[9px] block text-[#bbcabf]">年額 ¥{step.targetAnnual.toLocaleString()}</span>
                    </div>
                    <div className="p-2 rounded-lg bg-[#0b1326]">
                      <span className="text-[9px] block">達成ステータス</span>
                      {step.status === 'completed' ? (
                        <>
                          <span className="font-bold text-[#4edea3] text-xs">達成済 ✨</span>
                          <span className="text-[9px] block text-[#4edea3]">現在月額 ¥{monthlyIncome.toLocaleString()} で超過中</span>
                        </>
                      ) : (
                        <>
                          <span className="font-bold text-[#fbbf24] text-xs">未達成 (あと ¥{step.shortageMonthly?.toLocaleString()} /月)</span>
                          <span className="text-[9px] block text-[#bbcabf]">必要追加元本 約 ¥{Math.round((step.shortageAnnual || 0) / 0.04 / 10000).toLocaleString()}万円</span>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Level 2 Group: STEP 6-7 */}
        <div id="level-group-2" className={`flex flex-col gap-2 p-3 rounded-2xl bg-[#131b2e] border ${currentLevel === 2 ? 'border-[#10b981]/60 border-2' : 'border-slate-800'}`}>
          <div className="flex items-center justify-between pb-1 px-1">
            <span className="text-xs font-bold text-white flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-[#4edea3]">shopping_cart</span>
              LEVEL 2: 生活費自給・ミニFIRE期
            </span>
            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${currentLevel === 2 ? 'bg-[#10b981] text-[#003824] animate-pulse' : 'bg-slate-800 text-[#bbcabf]'}`}>
              {currentLevel === 2 ? '挑戦中ステージ' : `${highestCompletedStep >= 7 ? '2/2 CLEAR' : '0/2 クリア'}`}
            </span>
          </div>

          {dynamicSteps.filter(s => s.level === 2).map(step => (
            <div
              key={step.step}
              className={`rounded-xl overflow-hidden transition-all bg-[#171f33] border ${
                step.status === 'active'
                  ? 'border-2 border-[#10b981]/50 shadow-md'
                  : 'border-slate-800'
              }`}
            >
              <div
                onClick={() => toggleStep(step.step)}
                className="flex items-center justify-between p-3 cursor-pointer hover:bg-slate-700/60 active:scale-[0.99] transition-all"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
                    step.status === 'completed'
                      ? 'bg-[#10b981]/20 text-[#4edea3]'
                      : step.status === 'active'
                      ? 'bg-[#10b981] text-[#003824] shadow'
                      : 'bg-slate-800 text-[#bbcabf]'
                  }`}>
                    <span className="material-symbols-outlined text-[18px]">{step.icon}</span>
                  </div>
                  <div className="truncate">
                    <div className="flex items-center gap-1.5">
                      <span className={`font-bold text-xs truncate ${step.status === 'active' ? 'text-[#4edea3]' : 'text-white'}`}>
                        {step.name}
                      </span>
                      <span className={`text-[9px] px-1.5 py-0.2 rounded font-bold ${
                        step.status === 'completed'
                          ? 'bg-[#10b981]/20 text-[#4edea3]'
                          : step.status === 'active'
                          ? 'bg-[#10b981] text-[#003824]'
                          : 'bg-slate-800 text-[#bbcabf]'
                      }`}>
                        {step.statusLabel}
                      </span>
                    </div>
                    <span className="text-[10px] text-[#bbcabf] truncate block">{step.title}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <div className="flex flex-col items-end">
                    <span className={`font-mono text-xs font-bold ${step.status === 'completed' || step.status === 'active' ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                      {step.rateLabel}
                    </span>
                    <span className={`text-[10px] font-bold ${step.status === 'active' ? 'text-[#ffb4ab]' : 'text-[#bbcabf]'}`}>
                      {step.status === 'completed' ? 'CLEAR' : step.status === 'active' ? `残り ¥${(step.shortageMonthly || 0).toLocaleString()}` : 'LOCKED'}
                    </span>
                  </div>
                  <span className={`material-symbols-outlined text-[18px] text-[#bbcabf] transition-transform ${openStep === step.step ? 'rotate-180' : ''}`}>
                    expand_more
                  </span>
                </div>
              </div>

              {openStep === step.step && (
                <div className="px-3 pb-3 pt-1 border-t border-slate-800 text-xs text-[#bbcabf] space-y-2">
                  <p className="leading-relaxed text-[11px]">{step.description}</p>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="p-2 rounded-lg bg-[#0b1326]">
                      <span className="text-[9px] block">目標月額 / 年額換算</span>
                      <span className="font-bold text-white text-xs">¥{step.targetMonthly.toLocaleString()} /月</span>
                      <span className="text-[9px] block text-[#bbcabf]">年額 ¥{step.targetAnnual.toLocaleString()}</span>
                    </div>
                    <div className="p-2 rounded-lg bg-[#0b1326]">
                      <span className="text-[9px] block">クエスト達成状況</span>
                      {step.status === 'completed' ? (
                        <>
                          <span className="font-bold text-[#4edea3] text-xs">達成済 ✨</span>
                          <span className="text-[9px] block text-[#4edea3]">現在月額 ¥{monthlyIncome.toLocaleString()} で超過中</span>
                        </>
                      ) : (
                        <>
                          <span className="font-bold text-[#ffb4ab] text-xs">あと ¥{step.shortageMonthly?.toLocaleString()} /月</span>
                          <span className="text-[9px] block text-[#ffb4ab]">年額 ¥{step.shortageAnnual?.toLocaleString()} 不足</span>
                        </>
                      )}
                    </div>
                  </div>

                  {step.neededCapitalSim && (
                    <div className="p-2 rounded-lg bg-[#0b1326] border border-slate-800 space-y-1">
                      <span className="text-[10px] font-semibold text-[#4edea3] flex items-center gap-1">
                        <span className="material-symbols-outlined text-[13px]">savings</span>
                        利回り別・必要追加元本目安
                      </span>
                      <div className="grid grid-cols-3 gap-1 text-center font-mono">
                        <div className="p-1 rounded bg-[#171f33]">
                          <span className="text-[9px] block text-[#bbcabf]">3.5%</span>
                          <span className="font-bold text-white text-[11px]">{step.neededCapitalSim.yield35}</span>
                        </div>
                        <div className="p-1 rounded bg-[#10b981]/15 border border-[#4edea3]/30">
                          <span className="text-[9px] block text-[#4edea3]">4.0%</span>
                          <span className="font-bold text-[#4edea3] text-[11px]">{step.neededCapitalSim.yield40}</span>
                        </div>
                        <div className="p-1 rounded bg-[#171f33]">
                          <span className="text-[9px] block text-[#bbcabf]">4.5%</span>
                          <span className="font-bold text-white text-[11px]">{step.neededCapitalSim.yield45}</span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Level 3 Group: STEP 8-10 */}
        <div id="level-group-3" className="flex flex-col gap-2 p-3 rounded-2xl bg-[#131b2e] border border-slate-800">
          <div className="flex items-center justify-between pb-1 px-1">
            <span className="text-xs font-bold text-[#bbcabf] flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px]">flight</span>
              LEVEL 3: 完全経済的自由・FIRE期
            </span>
            <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#bbcabf] text-[10px] font-semibold">
              最終ステージ帯
            </span>
          </div>

          {dynamicSteps.filter(s => s.level === 3).map(step => (
            <div
              key={step.step}
              className={`rounded-xl overflow-hidden bg-[#171f33]/60 border transition-all ${
                step.status === 'active' ? 'border-2 border-[#10b981]/50' : 'border-slate-800/80'
              }`}
            >
              <div
                onClick={() => toggleStep(step.step)}
                className="flex items-center justify-between p-3 cursor-pointer hover:bg-slate-700/60 active:scale-[0.99] transition-all"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
                    step.status === 'completed'
                      ? 'bg-[#10b981]/20 text-[#4edea3]'
                      : step.status === 'active'
                      ? 'bg-[#10b981] text-[#003824] shadow'
                      : 'bg-slate-800 text-[#bbcabf]'
                  }`}>
                    <span className="material-symbols-outlined text-[18px]">{step.icon}</span>
                  </div>
                  <div className="truncate">
                    <div className="flex items-center gap-1.5">
                      <span className="font-semibold text-xs text-white truncate">{step.name}</span>
                      {step.status === 'locked' && (
                        <span className="material-symbols-outlined text-[13px] text-[#bbcabf]">lock</span>
                      )}
                      {step.status !== 'locked' && (
                        <span className={`text-[9px] px-1.5 py-0.2 rounded font-bold ${
                          step.status === 'completed' ? 'bg-[#10b981]/20 text-[#4edea3]' : 'bg-[#10b981] text-[#003824]'
                        }`}>
                          {step.statusLabel}
                        </span>
                      )}
                    </div>
                    <span className="text-[10px] text-[#bbcabf] truncate block">{step.title}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <div className="flex flex-col items-end">
                    <span className={`font-mono text-xs font-bold ${step.status !== 'locked' ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                      {step.rateLabel}
                    </span>
                    <span className="text-[10px] text-[#bbcabf]">
                      {step.status === 'completed' ? 'CLEAR' : step.status === 'active' ? `残り ¥${(step.shortageMonthly || 0).toLocaleString()}` : 'LOCKED'}
                    </span>
                  </div>
                  <span className={`material-symbols-outlined text-[18px] text-[#bbcabf] transition-transform ${openStep === step.step ? 'rotate-180' : ''}`}>
                    expand_more
                  </span>
                </div>
              </div>

              {openStep === step.step && (
                <div className="px-3 pb-3 pt-1 border-t border-slate-800 text-xs text-[#bbcabf] space-y-2">
                  <p className="leading-relaxed text-[11px]">{step.description}</p>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="p-2 rounded-lg bg-[#0b1326]">
                      <span className="text-[9px] block">目標月額</span>
                      <span className="font-bold text-white text-xs">¥{step.targetMonthly.toLocaleString()} /月</span>
                    </div>
                    <div className="p-2 rounded-lg bg-[#0b1326]">
                      <span className="text-[9px] block">攻略不足額</span>
                      <span className="font-bold text-[#ffb4ab] text-xs">¥{step.shortageMonthly?.toLocaleString()} /月</span>
                    </div>
                  </div>
                  {step.neededCapitalSim && (
                    <div className="p-2 rounded-lg bg-[#0b1326] border border-slate-800 space-y-1">
                      <span className="text-[10px] font-semibold text-[#4edea3] flex items-center gap-1">
                        <span className="material-symbols-outlined text-[13px]">savings</span>
                        必要追加元本目安
                      </span>
                      <div className="grid grid-cols-3 gap-1 text-center font-mono">
                        <div className="p-1 rounded bg-[#171f33]">
                          <span className="text-[9px] block text-[#bbcabf]">3.5%</span>
                          <span className="font-bold text-white text-[11px]">{step.neededCapitalSim.yield35}</span>
                        </div>
                        <div className="p-1 rounded bg-[#10b981]/15 border border-[#4edea3]/30">
                          <span className="text-[9px] block text-[#4edea3]">4.0%</span>
                          <span className="font-bold text-[#4edea3] text-[11px]">{step.neededCapitalSim.yield40}</span>
                        </div>
                        <div className="p-1 rounded bg-[#171f33]">
                          <span className="text-[9px] block text-[#bbcabf]">4.5%</span>
                          <span className="font-bold text-white text-[11px]">{step.neededCapitalSim.yield45}</span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Share Modal Bottom Sheet */}
      <ShareModal
        isOpen={isShareModalOpen}
        onClose={() => setIsShareModalOpen(false)}
      />
    </div>
  );
};
