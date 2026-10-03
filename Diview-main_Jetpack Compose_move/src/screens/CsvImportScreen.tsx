import React, { useState } from 'react';
import { useApp } from '../context/AppContext';

type CsvScenario =
  | 'step1_file'
  | 'step1_parsing'
  | 'step1_error'
  | 'step2_preview'
  | 'step2_diff'
  | 'step3_processing'
  | 'step3_complete'
  | 'step3_skip'
  | 'step3_partial'
  | 'step3_offline';

export const CsvImportScreen: React.FC = () => {
  const { navigateTo, showToast } = useApp();
  const [scenario, setScenario] = useState<CsvScenario>('step1_file');
  const [selectedBroker, setSelectedBroker] = useState<'sbi' | 'rakuten' | 'monex' | 'matsui' | 'other'>('monex');
  const [guideTab, setGuideTab] = useState<'sbi' | 'rakuten' | 'monex' | 'matsui' | 'nomura' | 'other'>('monex');
  const [isGuideOpen, setIsGuideOpen] = useState(true);

  // Holding actions in preview
  const [actions, setActions] = useState<Record<string, 'new' | 'merge' | 'skip'>>({
    '7203': 'new',
    'AAPL': 'new',
    'eMAXIS': 'new'
  });

  const getStepInfo = () => {
    switch (scenario) {
      case 'step1_file':
        return { step: 'STEP 1 / 3', title: 'CSVファイルの選択', status: '待機中', barWidths: ['100%', '0%', '0%'] };
      case 'step1_parsing':
        return { step: 'STEP 1 / 3', title: 'CSV構文解析中', status: '解析処理中', barWidths: ['100%', '0%', '0%'] };
      case 'step1_error':
        return { step: 'STEP 1 / 3', title: '解析エラー', status: 'エラー検知', barWidths: ['100%', '0%', '0%'] };
      case 'step2_preview':
        return { step: 'STEP 2 / 3', title: '銘柄照合プレビュー', status: '照合完了', barWidths: ['100%', '100%', '0%'] };
      case 'step2_diff':
        return { step: 'STEP 2 / 3', title: '差分・重複検出', status: '重複あり', barWidths: ['100%', '100%', '0%'] };
      case 'step3_processing':
        return { step: 'STEP 3 / 3', title: 'ポートフォリオ反映中', status: '計算中', barWidths: ['100%', '100%', '75%'] };
      case 'step3_complete':
        return { step: 'STEP 3 / 3', title: 'インポート完了', status: '反映完了', barWidths: ['100%', '100%', '100%'] };
      case 'step3_skip':
        return { step: 'STEP 3 / 3', title: 'インポート完了 (スキップ)', status: '変動なし', barWidths: ['100%', '100%', '100%'] };
      case 'step3_partial':
        return { step: 'STEP 3 / 3', title: '一部保留・エラー', status: '一部警告', barWidths: ['100%', '100%', '100%'] };
      case 'step3_offline':
        return { step: 'STEP 3 / 3', title: 'オフライン保存完了', status: 'ローカル保存', barWidths: ['100%', '100%', '100%'] };
    }
  };

  const { step, title, status, barWidths } = getStepInfo();

  const runParse = () => {
    setScenario('step1_parsing');
    setTimeout(() => {
      setScenario('step2_preview');
    }, 1500);
  };

  const runExecute = () => {
    setScenario('step3_processing');
    setTimeout(() => {
      setScenario('step3_complete');
    }, 1500);
  };

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-3.5">
      {/* 0. Prototype Controller Sandbox Bar */}
      <aside className="w-full bg-[#060e20]/95 p-2 rounded-xl shadow-md border border-slate-800">
        <div className="flex items-center justify-between text-[11px] mb-1 px-1">
          <span className="text-[#4edea3] font-bold flex items-center gap-1.5 uppercase tracking-wider">
            <span className="w-2 h-2 rounded-full bg-[#4edea3] animate-pulse" />
            Interactive Wizard Sandbox
          </span>
          <span className="font-mono text-[#bbcabf]">10 Scenarios Live</span>
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
          {[
            { id: 'step1_file', label: 'S1: ファイル選択' },
            { id: 'step1_parsing', label: 'S1: 解析中アニメ' },
            { id: 'step1_error', label: 'S1: エラー画面' },
            { id: 'step2_preview', label: 'S2: プレビュー' },
            { id: 'step2_diff', label: 'S2: 差分・重複検出' },
            { id: 'step3_processing', label: 'S3: 処理中' },
            { id: 'step3_complete', label: 'S3: 完了・反映' },
            { id: 'step3_skip', label: 'S3: 重複スキップ' },
            { id: 'step3_partial', label: 'S3: 一部エラー' },
            { id: 'step3_offline', label: 'S3: オフライン保存' }
          ].map(s => (
            <button
              key={s.id}
              type="button"
              onClick={() => setScenario(s.id as CsvScenario)}
              className={`shrink-0 px-2.5 py-1 rounded-full text-[11px] font-semibold transition-all active:scale-95 ${
                scenario === s.id
                  ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                  : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
      </aside>

      {/* 1. Unified Progress Tracker & Step Header */}
      <section className="p-3 rounded-2xl bg-[#131b2e] border border-slate-800 flex flex-col gap-2 shadow-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded font-mono font-bold text-[10px] bg-[#10b981]/15 border border-[#4edea3]/30 text-[#4edea3]">
              {step}
            </span>
            <h2 className="font-bold text-sm text-white">{title}</h2>
          </div>
          <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#4edea3] text-[10px] font-semibold flex items-center gap-1 border border-slate-700">
            <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3]" />
            {status}
          </span>
        </div>

        {/* 3-Segment Progress Bar */}
        <div className="grid grid-cols-3 gap-1.5 w-full pt-0.5">
          <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
            <div className="h-full bg-[#10b981] rounded-full transition-all duration-300" style={{ width: barWidths[0] }} />
          </div>
          <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
            <div className="h-full bg-[#10b981] rounded-full transition-all duration-300" style={{ width: barWidths[1] }} />
          </div>
          <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
            <div className="h-full bg-[#10b981] rounded-full transition-all duration-300" style={{ width: barWidths[2] }} />
          </div>
        </div>
      </section>

      {/* ================= SCENARIO 1: ファイル選択 ================= */}
      {scenario === 'step1_file' && (
        <div className="flex flex-col gap-3.5">
          {/* Brokerage 2x2 Grid */}
          <section className="flex flex-col gap-1.5">
            <span className="text-xs font-semibold text-[#bbcabf] px-1">対象証券会社</span>
            <div className="grid grid-cols-2 gap-2">
              {[
                { id: 'sbi', name: 'SBI証券', sub: '自動判定 推奨', icon: 'account_balance' },
                { id: 'rakuten', name: '楽天証券', sub: '全資産CSV対応', icon: 'trending_up' },
                { id: 'monex', name: 'マネックス', sub: '残高明細CSV', icon: 'sync' },
                { id: 'matsui', name: '松井証券', sub: '現物残高CSV', icon: 'store' }
              ].map(b => (
                <button
                  key={b.id}
                  type="button"
                  onClick={() => {
                    setSelectedBroker(b.id as any);
                    setGuideTab(b.id as any);
                  }}
                  className={`flex items-center justify-between p-3 rounded-xl border text-left transition-all ${
                    selectedBroker === b.id
                      ? 'bg-[#171f33] border-[#4edea3] shadow-[0_0_12px_rgba(78,222,163,0.25)]'
                      : 'bg-[#131b2e] border-slate-800 hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <span className={`material-symbols-outlined text-[18px] ${selectedBroker === b.id ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                      {b.icon}
                    </span>
                    <div className="truncate">
                      <p className="font-bold text-xs text-white truncate">{b.name}</p>
                      <p className={`text-[10px] truncate ${selectedBroker === b.id ? 'text-[#4edea3]' : 'text-[#bbcabf]'}`}>
                        {b.sub}
                      </p>
                    </div>
                  </div>
                  <span className={`material-symbols-outlined text-[16px] ${selectedBroker === b.id ? 'text-[#4edea3]' : 'text-slate-600'}`}>
                    {selectedBroker === b.id ? 'radio_button_checked' : 'radio_button_unchecked'}
                  </span>
                </button>
              ))}
            </div>

            <button
              type="button"
              onClick={() => {
                setSelectedBroker('other');
                setGuideTab('other');
              }}
              className={`p-2.5 rounded-xl border text-left flex items-center justify-between transition-all ${
                selectedBroker === 'other'
                  ? 'bg-[#171f33] border-[#4edea3]'
                  : 'bg-[#131b2e] border-slate-800 text-[#bbcabf]'
              }`}
            >
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[16px]">tune</span>
                <span className="text-xs font-bold text-white">その他 (汎用CSV / 各種フォーマット)</span>
              </div>
              <span className={`material-symbols-outlined text-[16px] ${selectedBroker === 'other' ? 'text-[#4edea3]' : 'text-slate-600'}`}>
                {selectedBroker === 'other' ? 'radio_button_checked' : 'radio_button_unchecked'}
              </span>
            </button>
          </section>

          {/* Selected File Card */}
          <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-[#4edea3]/30 flex flex-col gap-2 shadow-sm">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-10 h-10 rounded-xl bg-[#10b981]/20 text-[#4edea3] flex items-center justify-center">
                  <span className="material-symbols-outlined text-[24px]">description</span>
                </div>
                <div>
                  <h4 className="font-bold text-xs text-white">monex_portfolio_202412.csv</h4>
                  <p className="text-[10px] text-[#bbcabf]">24.8 KB • 38行 • <strong className="text-[#4edea3]">Shift-JIS 自動検知</strong></p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => showToast('ファイルを解除しました')}
                className="w-7 h-7 rounded-full bg-slate-800 hover:bg-slate-700 text-[#bbcabf] hover:text-white flex items-center justify-center"
              >
                <span className="material-symbols-outlined text-[16px]">close</span>
              </button>
            </div>

            <div className="flex items-center justify-between pt-1 text-[11px] text-[#4edea3]">
              <span className="flex items-center gap-1 font-semibold">
                <span className="material-symbols-outlined text-[14px]">verified</span>
                フォーマット構文照合完了 (国内株式・新NISA成長枠を含む)
              </span>
              <button
                type="button"
                onClick={() => showToast('ファイル選択ダイアログを開きます')}
                className="text-[10px] text-[#bbcabf] hover:underline"
              >
                別のファイル
              </button>
            </div>
          </div>

          {/* Guide Accordion */}
          <section className="rounded-2xl bg-[#131b2e] p-3 border border-slate-800 flex flex-col gap-2">
            <button
              type="button"
              onClick={() => setIsGuideOpen(!isGuideOpen)}
              className="w-full flex items-center justify-between text-left cursor-pointer"
            >
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[#4edea3] text-[18px]">help_center</span>
                <span className="font-bold text-xs text-white">各証券会社のCSV出力方法ガイド</span>
              </div>
              <span className={`material-symbols-outlined text-[#bbcabf] text-[18px] transition-transform ${isGuideOpen ? 'rotate-180' : ''}`}>
                expand_more
              </span>
            </button>

            {isGuideOpen && (
              <div className="pt-2 border-t border-slate-800 flex flex-col gap-2 text-xs">
                {/* Guide tabs */}
                <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
                  {(['sbi', 'rakuten', 'monex', 'matsui', 'nomura'] as const).map(k => (
                    <button
                      key={k}
                      type="button"
                      onClick={() => setGuideTab(k)}
                      className={`px-2.5 py-1 rounded-full text-[10px] font-semibold shrink-0 ${
                        guideTab === k ? 'bg-[#10b981] text-[#003824] font-bold' : 'bg-slate-800 text-[#bbcabf]'
                      }`}
                    >
                      {k === 'sbi' ? 'SBI証券' : k === 'rakuten' ? '楽天証券' : k === 'monex' ? 'マネックス' : k === 'matsui' ? '松井証券' : '野村證券'}
                    </button>
                  ))}
                </div>

                <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 space-y-1.5 text-[11px] text-[#bbcabf]">
                  <p className="text-white font-semibold flex items-center gap-1">
                    <span className="material-symbols-outlined text-[#4edea3] text-[14px]">lightbulb</span>
                    {guideTab === 'monex'
                      ? 'マネックス証券の「保有残高・口座管理」画面から出力された保有株CSVに対応しています。'
                      : '証券会社のマイページ＞保有資産＞CSVダウンロードより出力したファイルを指定してください。'}
                  </p>
                  <ol className="list-decimal pl-4 space-y-0.5 text-[10px]">
                    <li>ログイン後、「保有残高・口座管理」をクリックします</li>
                    <li>画面下部にある「CSVダウンロード」をクリックします</li>
                    <li>ダウンロードしたCSVを本アプリで読み込んでください。</li>
                  </ol>
                </div>
              </div>
            )}
          </section>

          {/* Local Security Guarantee */}
          <div className="p-3 rounded-2xl bg-[#0b1326] border border-slate-800 flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-[#10b981]/15 text-[#4edea3] flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[18px]">lock</span>
            </div>
            <div>
              <p className="font-bold text-xs text-white">端末内ローカル処理保証</p>
              <p className="text-[10px] text-[#bbcabf]">個人情報や保有資産データは外部サーバーへ送信されず、端末内のみでセキュアに解析されます。</p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex gap-2 pt-1">
            <button
              type="button"
              onClick={() => showToast('クリアしました')}
              className="w-1/3 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
            >
              クリア
            </button>
            <button
              type="button"
              onClick={runParse}
              className="flex-1 h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs flex items-center justify-center gap-1.5 shadow"
            >
              <span>ファイルを解析する</span>
              <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
            </button>
          </div>
        </div>
      )}

      {/* ================= SCENARIO 2: 解析中アニメ ================= */}
      {scenario === 'step1_parsing' && (
        <div className="flex flex-col items-center justify-center py-8 gap-4 text-center">
          <div className="relative w-28 h-28 flex items-center justify-center">
            <div className="w-24 h-24 rounded-full border-4 border-slate-800 border-t-[#4edea3] animate-spin" />
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="font-mono text-2xl font-bold text-[#4edea3]">85%</span>
              <span className="text-[9px] text-[#bbcabf]">解析中</span>
            </div>
          </div>
          <div>
            <h3 className="font-bold text-sm text-white">ポートフォリオ構造を解析中</h3>
            <p className="text-xs text-[#bbcabf] mt-0.5">配当履歴マスターおよび権利付最終日データベースと照合しています...</p>
          </div>
          <button
            type="button"
            onClick={() => setScenario('step2_preview')}
            className="px-4 py-1.5 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
          >
            スキップしてプレビューへ ➔
          </button>
        </div>
      )}

      {/* ================= SCENARIO 3: エラー画面 ================= */}
      {scenario === 'step1_error' && (
        <div className="flex flex-col gap-3.5">
          <div className="p-4 rounded-2xl bg-red-950/40 border border-red-800 text-white flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-red-400 text-[22px]">warning</span>
              <h3 className="font-bold text-sm text-red-400">CSVファイルの読み込みに失敗しました</h3>
            </div>
            <p className="text-xs text-[#bbcabf]">フォーマットの不一致または文字コード異常が検出されました。</p>
            <div className="p-2.5 rounded-lg bg-[#0b1326] text-[11px] text-[#bbcabf] space-y-1">
              <div>• 必須列欠損: 列 [保有数量] または [取得単価] が見つかりません。</div>
              <div>• 文字化け検知: ヘッダー1行目のデコードに失敗しました。</div>
            </div>
          </div>
          <button
            type="button"
            onClick={() => setScenario('step1_file')}
            className="w-full h-11 rounded-xl bg-[#10b981] text-[#003824] font-bold text-xs flex items-center justify-center gap-1.5 shadow"
          >
            <span className="material-symbols-outlined text-[16px]">replay</span>
            別のCSVファイルを選択し直す
          </button>
        </div>
      )}

      {/* ================= SCENARIO 4: プレビュー ================= */}
      {scenario === 'step2_preview' && (
        <div className="flex flex-col gap-3">
          <div className="p-3 rounded-xl bg-[#131b2e] border border-[#4edea3]/30 flex items-center justify-between text-xs">
            <span className="flex items-center gap-1.5 text-white font-semibold">
              <span className="material-symbols-outlined text-[#4edea3] text-[18px]">task_alt</span>
              解析完了: <strong className="text-[#4edea3]">3件</strong>の銘柄を検出
            </span>
            <span className="px-2 py-0.5 rounded-full bg-[#10b981]/15 text-[#4edea3] font-bold text-[10px]">
              整合性 100%
            </span>
          </div>

          {/* Detected Holdings */}
          {[
            { code: '7203', name: 'トヨタ自動車', sub: '新NISA(成長) • 100株 @ ¥2,650', div: '+¥7,280/年', rate: '利回り 2.75%' },
            { code: 'AAPL', name: 'Apple Inc.', sub: '特定口座 • 10株 @ $180.50 (USD)', div: '+¥1,460/年', rate: '$9.60/年 (為替@154)' },
            { code: 'eMAXIS', name: 'eMAXIS Slim 全世界株式', sub: 'つみたて投資枠 • 50,000口', div: '¥0 (再投資)', rate: '無分配複利型' }
          ].map(row => (
            <div key={row.code} className="p-3 rounded-xl bg-[#131b2e] border border-slate-800 flex flex-col gap-2">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <span className="px-2 py-0.5 rounded bg-slate-800 font-mono text-[10px] text-white font-bold">{row.code}</span>
                  <div>
                    <p className="font-bold text-xs text-white">{row.name}</p>
                    <p className="text-[10px] text-[#bbcabf]">{row.sub}</p>
                  </div>
                </div>
                <span className="font-mono text-xs font-bold text-[#4edea3]">{row.div}</span>
              </div>
              <div className="flex items-center justify-between pt-1 border-t border-slate-800 text-[10px]">
                <span className="text-[#bbcabf]">{row.rate}</span>
                <div className="flex gap-1">
                  {(['new', 'merge', 'skip'] as const).map(act => (
                    <button
                      key={act}
                      type="button"
                      onClick={() => setActions({ ...actions, [row.code]: act })}
                      className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                        actions[row.code] === act ? 'bg-[#10b981] text-[#003824] font-bold' : 'bg-slate-800 text-[#bbcabf]'
                      }`}
                    >
                      {act === 'new' ? '新規' : act === 'merge' ? '合算' : 'スキップ'}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ))}

          {/* Impact Banner */}
          <div className="p-3 rounded-2xl bg-[#0b1326] border border-[#4edea3]/30 grid grid-cols-2 gap-2 text-xs">
            <div>
              <span className="text-[10px] text-[#bbcabf]">年間配当金加算額</span>
              <span className="font-mono text-base font-bold text-[#4edea3] block mt-0.5">+¥8,740</span>
            </div>
            <div>
              <span className="text-[10px] text-[#bbcabf]">対象資産評価額</span>
              <span className="font-mono text-base font-bold text-white block mt-0.5">¥720,000</span>
            </div>
          </div>

          <div className="flex gap-2 pt-1">
            <button
              type="button"
              onClick={() => setScenario('step1_file')}
              className="w-1/3 h-11 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
            >
              やり直す
            </button>
            <button
              type="button"
              onClick={runExecute}
              className="flex-1 h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs flex items-center justify-center gap-1.5 shadow"
            >
              <span>資産に反映する</span>
              <span className="material-symbols-outlined text-[16px]">cloud_done</span>
            </button>
          </div>
        </div>
      )}

      {/* ================= SCENARIO 5: 差分・重複検出 ================= */}
      {scenario === 'step2_diff' && (
        <div className="flex flex-col gap-3">
          <div className="p-3 rounded-xl bg-[#131b2e] border border-amber-500/30 flex items-start gap-2.5">
            <span className="material-symbols-outlined text-amber-400 text-[20px] shrink-0 mt-0.5">content_copy</span>
            <div className="text-xs">
              <h4 className="font-bold text-white">2件の既存銘柄との重複を検出</h4>
              <p className="text-[#bbcabf] text-[11px] mt-0.5">ポートフォリオに同一コードが存在します。取り込み方法を指定してください。</p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setScenario('step3_complete')}
            className="w-full h-11 rounded-xl bg-[#10b981] text-[#003824] font-bold text-xs flex items-center justify-center gap-1.5 shadow"
          >
            <span>差分を合算して反映する</span>
            <span className="material-symbols-outlined text-[16px]">check</span>
          </button>
        </div>
      )}

      {/* ================= SCENARIO 6: 処理中 ================= */}
      {scenario === 'step3_processing' && (
        <div className="flex flex-col items-center justify-center py-10 gap-3 text-center">
          <div className="w-14 h-14 rounded-full border-4 border-slate-800 border-t-[#4edea3] animate-spin" />
          <h3 className="font-bold text-sm text-white">ポートフォリオを更新中</h3>
          <p className="text-xs text-[#bbcabf]">配当カレンダーの再計算およびセクター比率の整合性を検証しています...</p>
        </div>
      )}

      {/* ================= SCENARIO 7: 完了・反映 ================= */}
      {scenario === 'step3_complete' && (
        <div className="flex flex-col items-center text-center gap-3.5 pt-2">
          <div className="w-16 h-16 rounded-full bg-[#10b981] text-[#003824] flex items-center justify-center shadow-lg">
            <span className="material-symbols-outlined text-[36px]">check_circle</span>
          </div>
          <div>
            <h3 className="font-bold text-base text-white">3件の銘柄を取り込み完了</h3>
            <p className="text-xs text-[#bbcabf] mt-0.5">新規登録 2件 / 既存合算 1件 が正常に反映されました</p>
          </div>

          <div className="w-full p-4 rounded-2xl bg-[#131b2e] border border-[#4edea3]/30 text-left space-y-2">
            <span className="text-xs font-bold text-[#4edea3]">年間配当の見通しが向上しました</span>
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="p-2 rounded bg-[#0b1326]">
                <span className="text-[10px] text-[#bbcabf] block">年間配当の増加</span>
                <span className="font-mono text-base font-bold text-[#4edea3] mt-0.5 block">+¥8,740</span>
              </div>
              <div className="p-2 rounded bg-[#0b1326]">
                <span className="text-[10px] text-[#bbcabf] block">反映後の総資産増</span>
                <span className="font-mono text-base font-bold text-white mt-0.5 block">+¥720,000</span>
              </div>
            </div>
          </div>

          <div className="w-full flex flex-col gap-2 pt-1">
            <button
              type="button"
              onClick={() => navigateTo('asset-management')}
              className="w-full h-11 rounded-xl bg-[#10b981] text-[#003824] font-bold text-xs flex items-center justify-center gap-1.5 shadow"
            >
              <span className="material-symbols-outlined text-[18px]">account_balance_wallet</span>
              <span>資産管理画面を確認する</span>
            </button>
            <button
              type="button"
              onClick={() => setScenario('step1_file')}
              className="w-full h-10 rounded-xl bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
            >
              続けて別のCSVをインポートする
            </button>
          </div>
        </div>
      )}

      {/* ================= SCENARIOS 8, 9, 10: Skip, Partial, Offline ================= */}
      {scenario === 'step3_skip' && (
        <div className="flex flex-col items-center text-center gap-3 p-4">
          <span className="material-symbols-outlined text-[40px] text-[#bbcabf]">filter_list_off</span>
          <h3 className="font-bold text-sm text-white">新規に取り込む銘柄はありませんでした</h3>
          <p className="text-xs text-[#bbcabf]">CSV内の銘柄は既に最新情報として登録されているためスキップされました。</p>
          <button
            type="button"
            onClick={() => setScenario('step1_file')}
            className="mt-2 px-4 py-2 rounded-xl bg-[#10b981] text-[#003824] text-xs font-bold"
          >
            別のファイルを選択
          </button>
        </div>
      )}

      {scenario === 'step3_partial' && (
        <div className="flex flex-col gap-3 p-3 rounded-2xl bg-[#131b2e] border border-amber-500/40">
          <h3 className="font-bold text-xs text-amber-400">2件成功 / 1件の取り込みに失敗</h3>
          <p className="text-[11px] text-[#bbcabf]">コード 9999 はマスター照合できませんでした。有効な2件のみ反映を継続できます。</p>
          <button
            type="button"
            onClick={() => setScenario('step3_complete')}
            className="w-full h-10 rounded-xl bg-[#10b981] text-[#003824] font-bold text-xs"
          >
            エラー銘柄を除外して完了する
          </button>
        </div>
      )}

      {scenario === 'step3_offline' && (
        <div className="flex flex-col items-center text-center gap-3 p-4">
          <span className="material-symbols-outlined text-[36px] text-indigo-300">cloud_off</span>
          <h3 className="font-bold text-sm text-white">端末内にオフライン保存しました</h3>
          <p className="text-xs text-[#bbcabf]">通信復旧時に自動同期キューが処理されます。</p>
          <button
            type="button"
            onClick={() => navigateTo('asset-management')}
            className="mt-2 px-4 py-2 rounded-xl bg-[#10b981] text-[#003824] text-xs font-bold"
          >
            資産管理へ戻る
          </button>
        </div>
      )}
    </div>
  );
};
