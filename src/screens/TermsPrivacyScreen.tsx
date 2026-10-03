import React, { useState } from 'react';

export const TermsPrivacyScreen: React.FC = () => {
  const [tab, setTab] = useState<'terms' | 'privacy'>('privacy');
  const [openAccordions, setOpenAccordions] = useState<Record<string, boolean>>({
    art1: true,
    art2: true
  });

  const toggleAccordion = (id: string) => {
    setOpenAccordions(prev => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-4">
      {/* Security Core Badge */}
      <section className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 space-y-2.5 shadow-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-[#4edea3] animate-pulse" />
            <span className="text-xs text-[#4edea3] font-bold">Kurogane Security Core</span>
          </div>
          <span className="text-[10px] text-[#4edea3] bg-[#10b981]/15 border border-[#4edea3]/30 px-2 py-0.5 rounded-full font-semibold flex items-center gap-1">
            <span className="material-symbols-outlined text-[13px]">verified</span>
            保護稼働中
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="p-2 rounded-xl bg-[#0b1326] border border-slate-800 flex items-center gap-2">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">encrypted</span>
            <div>
              <p className="font-bold text-white text-[11px]">端末内AES-256暗号化</p>
              <p className="text-[10px] text-[#bbcabf]">クラウド送信 0 Byte</p>
            </div>
          </div>
          <div className="p-2 rounded-xl bg-[#0b1326] border border-slate-800 flex items-center gap-2">
            <span className="material-symbols-outlined text-[#4edea3] text-[20px]">block</span>
            <div>
              <p className="font-bold text-white text-[11px]">第三者提供・売買ゼロ</p>
              <p className="text-[10px] text-[#bbcabf]">証券認証情報は非保持</p>
            </div>
          </div>
        </div>
      </section>

      {/* Segment Switcher */}
      <div className="p-1 bg-[#0b1326] rounded-xl flex items-center border border-slate-800">
        <button
          type="button"
          onClick={() => setTab('terms')}
          className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
            tab === 'terms' ? 'bg-[#10b981] text-[#003824] shadow' : 'text-[#bbcabf] hover:text-white'
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">gavel</span>
          <span>利用規約</span>
        </button>
        <button
          type="button"
          onClick={() => setTab('privacy')}
          className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
            tab === 'privacy' ? 'bg-[#10b981] text-[#003824] shadow' : 'text-[#bbcabf] hover:text-white'
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">verified_user</span>
          <span>プライバシー</span>
        </button>
      </div>

      {/* ================= TERMS TAB ================= */}
      {tab === 'terms' && (
        <div className="space-y-3 text-xs">
          <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 space-y-1">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-sm text-white">Kurogane利用規約</h3>
              <span className="text-[10px] text-[#4edea3] bg-[#10b981]/15 px-2 py-0.5 rounded-full font-bold">2025年改訂版</span>
            </div>
            <p className="text-[#bbcabf] text-[11px] leading-relaxed">
              本規約は、合同会社Kurogane Labsが提供するポートフォリオ分析アプリ「Kurogane Dividend」の利用条件を定めるものです。
            </p>
          </div>

          {/* Article 1 */}
          <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 space-y-2">
            <div
              onClick={() => toggleAccordion('art1')}
              className="flex items-center justify-between cursor-pointer"
            >
              <h4 className="font-bold text-xs text-white">第1条（総則・本サービスの目的）</h4>
              <span className={`material-symbols-outlined text-[#bbcabf] text-[18px] transition-transform ${openAccordions.art1 ? 'rotate-180' : ''}`}>
                expand_more
              </span>
            </div>
            {openAccordions.art1 && (
              <p className="text-[11px] text-[#bbcabf] leading-relaxed pt-1 border-t border-slate-800">
                本アプリは、ユーザー自身が手動入力またはCSVインポートした保有銘柄、取得価格、保有株数に基づき、年間想定配当額、税引後キャッシュフロー、受取月カレンダーを試算・可視化する個人向けシミュレーションツールです。証券会社IDやパスワードを預からず運用されます。
              </p>
            )}
          </div>

          {/* Article 2 */}
          <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-red-950/60 space-y-2">
            <div
              onClick={() => toggleAccordion('art2')}
              className="flex items-center justify-between cursor-pointer"
            >
              <h4 className="font-bold text-xs text-red-400">第2条（投資助言に関する免責事項）</h4>
              <span className={`material-symbols-outlined text-red-400 text-[18px] transition-transform ${openAccordions.art2 ? 'rotate-180' : ''}`}>
                expand_more
              </span>
            </div>
            {openAccordions.art2 && (
              <div className="text-[11px] text-[#bbcabf] leading-relaxed space-y-1.5 pt-1 border-t border-slate-800">
                <p>1. 当社は、金融商品取引法に基づく金融商品取引業者（投資助言・代理業等）ではありません。本アプリ内で表示される指標は参考値であり、特定の有価証券の売買推奨を行うものではありません。</p>
                <p>2. 配当金は企業の財務状況や市場動向により減配・無配となる場合があります。いかなる投資判断の損失についても当社は責任を負いません。</p>
              </div>
            )}
          </div>

          {/* Article 3 */}
          <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 space-y-1.5">
            <h4 className="font-bold text-xs text-white">第3条（知的財産権および禁止事項）</h4>
            <p className="text-[11px] text-[#bbcabf] leading-relaxed">
              本アプリ内のUIデザイン、グラフィック、アルゴリズムの著作権は当社に帰属します。リバースエンジニアリング、不正クローリング、複製行為は固く禁止いたします。
            </p>
          </div>
        </div>
      )}

      {/* ================= PRIVACY TAB ================= */}
      {tab === 'privacy' && (
        <div className="space-y-3 text-xs">
          {/* Zero Knowledge Hero Card */}
          <div className="p-4 rounded-2xl bg-gradient-to-br from-[#171f33] to-[#0d1629] border border-[#4edea3]/30 space-y-2">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-[#10b981]/20 flex items-center justify-center text-[#4edea3]">
                <span className="material-symbols-outlined text-[20px]">shield_with_heart</span>
              </div>
              <div>
                <span className="text-[9px] text-[#4edea3] uppercase font-bold tracking-wider">Zero Knowledge Architecture</span>
                <h3 className="font-bold text-sm text-white">金融データ端末内完結宣言</h3>
              </div>
            </div>
            <p className="text-[11px] text-[#bbcabf] leading-relaxed">
              Kurogane Dividendは「あなたの蓄財状況を誰にも見せない」ことを思想として設計されています。入力された全株式データ・投資金額・配当キャッシュフローは、クラウドサーバーへ一切送信されず、端末内でのみ演算処理されます。
            </p>

            <div className="grid grid-cols-2 gap-2 pt-1">
              <div className="p-2 rounded-xl bg-[#0b1326] border border-slate-800">
                <span className="text-[10px] text-[#4edea3] font-bold block">外部送信</span>
                <span className="font-mono text-lg font-bold text-white">0 <span className="text-xs text-[#4edea3]">Bytes</span></span>
                <span className="text-[9px] text-[#bbcabf] block">保有株・金額の送出なし</span>
              </div>
              <div className="p-2 rounded-xl bg-[#0b1326] border border-slate-800">
                <span className="text-[10px] text-[#4edea3] font-bold block">保護規格</span>
                <span className="font-mono text-lg font-bold text-white">AES-256</span>
                <span className="text-[9px] text-[#bbcabf] block">SQLite端末暗号化</span>
              </div>
            </div>
          </div>

          {/* Privacy Sections */}
          <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 space-y-1.5">
            <h4 className="font-bold text-xs text-white">1. 取得する情報とその範囲</h4>
            <p className="text-[11px] text-[#bbcabf] leading-relaxed">
              当社がアプリの安定運用および技術的改善のためにのみ取得する情報は、匿名化クラッシュログ（スタックトレース・OSモデル）および匿名の利用統計情報（Firebase Analytics等の標準基盤）に限られます。個人を特定できる情報は含まれません。
            </p>
          </div>

          <div className="p-3.5 rounded-2xl bg-[#131b2e] border border-slate-800 space-y-1.5">
            <h4 className="font-bold text-xs text-white">2. セキュリティ管理体制</h4>
            <p className="text-[11px] text-[#bbcabf] leading-relaxed">
              ポートフォリオデータは、端末固有のセキュアキーストア（iOS Keychain / Android Keystore）から導出されたマスターキーにより、ローカルデータベース（SQLite）レベルで暗号化されています。
            </p>
          </div>
        </div>
      )}
    </div>
  );
};
