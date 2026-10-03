import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import { roadmapMasterSteps } from '../data/mockData';
import { ShareModal } from '../components/modals/ShareModal';

export const RoadmapScreen: React.FC = () => {
  const { showToast } = useApp();
  const [openStep, setOpenStep] = useState<number | null>(6);
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);

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
            <span className="text-[11px] font-bold tracking-wide">LEVEL 2 進行中: STEP 6</span>
          </div>

          <div className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-indigo-950/60 text-indigo-300 text-[11px] font-semibold border border-indigo-800/40">
            <span className="material-symbols-outlined text-[15px] text-amber-400">emoji_events</span>
            <span>LV.1 制覇済み (5/10)</span>
          </div>
        </div>

        {/* Trophy Alert Card: LEVEL 1 Fully Cleared */}
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
              <span className="text-[10px] text-[#bbcabf]">生活インフラ自給期 (STEP 1〜5) を踏破完了</span>
            </div>
          </div>
          <span className="px-2 py-0.5 rounded bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold shrink-0">
            ALL CLEAR
          </span>
        </div>

        {/* Current Dividends Counter */}
        <div className="flex flex-col gap-0.5 mb-3 relative z-10">
          <span className="text-[10px] uppercase tracking-wider text-[#bbcabf] font-semibold">
            現在の配当獲得ステータス
          </span>
          <div className="flex items-baseline gap-2">
            <span className="font-mono text-2xl sm:text-3xl text-white font-bold tracking-tight">
              ¥374,500
            </span>
            <span className="text-xs text-[#bbcabf]">/ 年</span>
          </div>
          <div className="flex items-center gap-1.5 text-xs text-[#bbcabf]">
            <span>月間換算不労所得:</span>
            <span className="font-mono text-base text-[#4edea3] font-bold">約 ¥31,208</span>
            <span>/ 月</span>
          </div>
        </div>

        {/* Quest Progress to STEP 6 */}
        <div className="flex flex-col gap-2 p-3 rounded-xl bg-[#0b1326]/80 border border-slate-800 mb-3 relative z-10">
          <div className="flex justify-between items-center text-xs">
            <div className="flex items-center gap-1.5 font-bold text-white">
              <span className="material-symbols-outlined text-[17px] text-[#4edea3]">shopping_cart</span>
              <span>QUEST 6 目標: <span className="text-[#4edea3]">¥50,000 / 月</span></span>
            </div>
            <span className="font-mono text-xs font-bold text-[#4edea3]">62.4% 達成</span>
          </div>

          <div className="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden p-[1px]">
            <div
              className="h-full bg-gradient-to-r from-[#10b981] via-[#4edea3] to-teal-400 rounded-full shadow-[0_0_10px_rgba(78,222,163,0.5)]"
              style={{ width: '62.4%' }}
            />
          </div>

          <div className="flex justify-between items-center text-[10px] text-[#bbcabf] pt-0.5">
            <span>現在実績: <strong className="text-white font-mono">¥31,208</strong></span>
            <span className="text-[#ffb4ab] font-bold flex items-center gap-0.5">
              <span className="material-symbols-outlined text-[13px]">flag</span>
              あと ¥18,792 / 月 で攻略
            </span>
          </div>
        </div>

        {/* Additional Principal Note */}
        <div className="flex items-start gap-2 p-2.5 rounded-lg bg-[#171f33]/80 mb-3 border border-slate-700/60 text-xs text-[#bbcabf]">
          <span className="material-symbols-outlined text-[#4edea3] text-[18px] shrink-0 mt-0.5">query_stats</span>
          <div className="leading-relaxed">
            年額 <span className="text-[#4edea3] font-bold">+¥225,500</span> でSTEP 6クリア。配当利回り <span className="text-white font-semibold">4.0%</span> 想定時、必要な追加投資元本は約 <span className="text-[#4edea3] font-bold underline decoration-[#4edea3]/40">¥563万円</span> です。
          </div>
        </div>

        {/* Action CTAs */}
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => setIsShareModalOpen(true)}
            className="h-10 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] text-xs font-bold flex items-center justify-center gap-1.5 active:scale-95 shadow"
          >
            <span className="material-symbols-outlined text-[17px]">share</span>
            <span>達成度を共有する</span>
          </button>
          <button
            type="button"
            onClick={() => showToast('達成カード画像を保存しました', 'download_done')}
            className="h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold flex items-center justify-center gap-1.5 active:scale-95 border border-slate-700/60"
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
            className="flex flex-col items-center p-2 rounded-xl bg-[#131b2e] border border-[#4edea3]/40 text-center active:scale-95 transition-all"
          >
            <div className="flex items-center gap-1 text-[11px] font-bold text-[#4edea3]">
              <span>LV.1 制覇</span>
              <span className="material-symbols-outlined text-[13px]">check_circle</span>
            </div>
            <span className="text-[10px] text-[#bbcabf] truncate w-full mt-0.5">インフラ自給期</span>
            <span className="text-[10px] text-[#4edea3] font-bold">STEP 1-5</span>
          </button>

          <button
            type="button"
            onClick={() => jumpToLevel(2)}
            className="flex flex-col items-center p-2 rounded-xl bg-[#171f33] border-2 border-[#4edea3] text-center shadow-[0_0_12px_rgba(78,222,163,0.25)] active:scale-95 transition-all"
          >
            <div className="flex items-center gap-1 text-[11px] font-bold text-[#4edea3] animate-pulse">
              <span>LV.2 挑戦中</span>
              <span className="material-symbols-outlined text-[13px]">play_circle</span>
            </div>
            <span className="text-[10px] text-white font-semibold truncate w-full mt-0.5">生活費・ミニFIRE</span>
            <span className="text-[10px] text-[#4edea3] font-bold">STEP 6-7</span>
          </button>

          <button
            type="button"
            onClick={() => jumpToLevel(3)}
            className="flex flex-col items-center p-2 rounded-xl bg-[#131b2e] border border-slate-800 text-center active:scale-95 transition-all opacity-80"
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
        <div id="level-group-1" className="flex flex-col gap-2 p-3 rounded-2xl bg-[#131b2e] border border-[#4edea3]/30">
          <div className="flex items-center justify-between pb-1 px-1">
            <span className="text-xs font-bold text-[#4edea3] flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px]">bolt</span>
              LEVEL 1: 生活インフラ自給期
            </span>
            <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold flex items-center gap-1">
              <span className="material-symbols-outlined text-[12px]">done_all</span>
              5/5 CLEAR
            </span>
          </div>

          {roadmapMasterSteps.filter(s => s.level === 1).map(step => (
            <div
              key={step.step}
              className="rounded-xl overflow-hidden bg-[#171f33] border border-slate-800 transition-all"
            >
              <div
                onClick={() => toggleStep(step.step)}
                className="flex items-center justify-between p-3 cursor-pointer hover:bg-slate-700/60 active:scale-[0.99] transition-all"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="w-8 h-8 rounded-lg bg-[#10b981]/15 text-[#4edea3] flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-[18px]">{step.icon}</span>
                  </div>
                  <div className="truncate">
                    <div className="flex items-center gap-1.5">
                      <span className="font-bold text-xs text-white truncate">{step.name}</span>
                      <span className="text-[9px] px-1.5 py-0.2 rounded bg-[#10b981]/20 text-[#4edea3] font-bold">
                        {step.statusLabel}
                      </span>
                    </div>
                    <span className="text-[10px] text-[#bbcabf] truncate block">{step.title}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <span className="font-mono text-xs font-bold text-[#4edea3]">100% CLEAR</span>
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
                      <span className="font-bold text-[#4edea3] text-xs">達成済 ✨</span>
                      <span className="text-[9px] block text-[#4edea3]">現在月額 ¥31,208 で超過中</span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Level 2 Group: STEP 6-7 */}
        <div id="level-group-2" className="flex flex-col gap-2 p-3 rounded-2xl bg-[#131b2e] border-2 border-[#4edea3]/50 shadow-md">
          <div className="flex items-center justify-between pb-1 px-1">
            <span className="text-xs font-bold text-white flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-[#4edea3]">shopping_cart</span>
              LEVEL 2: 生活費自給・ミニFIRE期
            </span>
            <span className="px-2 py-0.5 rounded-full bg-[#10b981] text-[#003824] text-[10px] font-bold animate-pulse">
              挑戦中ステージ
            </span>
          </div>

          {roadmapMasterSteps.filter(s => s.level === 2).map(step => {
            const isStep6 = step.step === 6;
            return (
              <div
                key={step.step}
                className={`rounded-xl overflow-hidden transition-all ${
                  isStep6
                    ? 'bg-[#171f33] border-2 border-[#4edea3]/50 shadow-md'
                    : 'bg-[#171f33]/70 border border-slate-800'
                }`}
              >
                <div
                  onClick={() => toggleStep(step.step)}
                  className="flex items-center justify-between p-3 cursor-pointer hover:bg-slate-700/60 active:scale-[0.99] transition-all"
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
                      isStep6 ? 'bg-[#10b981] text-[#003824] shadow' : 'bg-slate-800 text-[#bbcabf]'
                    }`}>
                      <span className="material-symbols-outlined text-[18px]">{step.icon}</span>
                    </div>
                    <div className="truncate">
                      <div className="flex items-center gap-1.5">
                        <span className={`font-bold text-xs truncate ${isStep6 ? 'text-[#4edea3]' : 'text-white'}`}>
                          {step.name}
                        </span>
                        <span className={`text-[9px] px-1.5 py-0.2 rounded font-bold ${
                          isStep6 ? 'bg-[#10b981] text-[#003824]' : 'bg-slate-800 text-[#bbcabf]'
                        }`}>
                          {step.statusLabel}
                        </span>
                      </div>
                      <span className="text-[10px] text-[#bbcabf] truncate block">{step.title}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <div className="flex flex-col items-end">
                      <span className={`font-mono text-xs font-bold ${isStep6 ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                        {step.rateLabel}
                      </span>
                      <span className={`text-[10px] font-bold ${isStep6 ? 'text-[#ffb4ab]' : 'text-[#bbcabf]'}`}>
                        {step.subStatusLabel}
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
                        <span className="text-[9px] block">クエスト達成まで</span>
                        <span className="font-bold text-[#ffb4ab] text-xs">あと ¥{step.shortageMonthly?.toLocaleString()} /月</span>
                        <span className="text-[9px] block text-[#ffb4ab]">年額 ¥{step.shortageAnnual?.toLocaleString()} 不足</span>
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
            );
          })}
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

          {roadmapMasterSteps.filter(s => s.level === 3).map(step => (
            <div
              key={step.step}
              className="rounded-xl overflow-hidden bg-[#171f33]/60 border border-slate-800/80 transition-all"
            >
              <div
                onClick={() => toggleStep(step.step)}
                className="flex items-center justify-between p-3 cursor-pointer hover:bg-slate-700/60 active:scale-[0.99] transition-all"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="w-8 h-8 rounded-lg bg-slate-800 text-[#bbcabf] flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-[18px]">{step.icon}</span>
                  </div>
                  <div className="truncate">
                    <div className="flex items-center gap-1.5">
                      <span className="font-semibold text-xs text-white truncate">{step.name}</span>
                      <span className="material-symbols-outlined text-[13px] text-[#bbcabf]">lock</span>
                    </div>
                    <span className="text-[10px] text-[#bbcabf] truncate block">{step.title}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <div className="flex flex-col items-end">
                    <span className="font-mono text-xs text-[#bbcabf]">{step.rateLabel}</span>
                    <span className="text-[10px] text-[#bbcabf]">{step.subStatusLabel}</span>
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
                    <div className="p-2 rounded-lg bg-[#0b1326] border border-slate-800">
                      <span className="text-[10px] block text-[#4edea3]">必要元本 (利回り4.0% 想定):</span>
                      <span className="font-bold font-mono text-sm text-white">{step.neededCapitalSim.yield40}</span>
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
