import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import { faqItems } from '../data/mockData';

export const ContactFeedbackScreen: React.FC = () => {
  const { showToast } = useApp();

  const [openFaqIndex, setOpenFaqIndex] = useState<number | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<'bug' | 'feature' | 'stock' | 'other'>('feature');
  const [senderName, setSenderName] = useState('');
  const [senderEmail, setSenderEmail] = useState('');
  const [message, setMessage] = useState('');
  const [attachDiagnostics, setAttachDiagnostics] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const toggleFaq = (index: number) => {
    setOpenFaqIndex(prev => (prev === index ? null : index));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim()) {
      alert('お問い合わせ内容をご入力ください');
      return;
    }

    setIsSubmitting(true);
    setTimeout(() => {
      setIsSubmitting(false);
      setMessage('');
      showToast('ご意見・お問い合わせを送信しました。ありがとうございます！');
    }, 700);
  };

  return (
    <div className="flex flex-col w-full max-w-lg mx-auto px-3.5 pt-18 pb-28 gap-4">
      {/* Concierge Welcome Card */}
      <div className="relative w-full rounded-2xl bg-[#131b2e] p-4 shadow-md border border-slate-800">
        <div className="flex items-start gap-3">
          <div className="w-11 h-11 rounded-xl bg-[#171f33] border border-slate-700/60 flex items-center justify-center shrink-0 text-[#4edea3]">
            <span className="material-symbols-outlined text-[24px]">headset_mic</span>
          </div>
          <div className="flex flex-col min-w-0">
            <div className="flex items-center gap-1.5">
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-[#10b981]/15 text-[#4edea3] font-bold">
                Official Support
              </span>
              <span className="w-1.5 h-1.5 rounded-full bg-[#4edea3] animate-pulse" />
            </div>
            <h3 className="font-bold text-sm text-white mt-1">Diview サポートデスク</h3>
            <p className="text-xs text-[#bbcabf] mt-0.5 leading-relaxed">
              配当管理や操作についてご不明な点、改善のご要望をお気軽にお寄せください。通常24時間以内に返信いたします。
            </p>
          </div>
        </div>
      </div>

      {/* Section 1: FAQ Accordion */}
      <section className="space-y-2">
        <div className="flex items-center justify-between text-xs px-1">
          <h3 className="font-bold text-white flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[#4edea3] text-[18px]">help</span>
            よくあるご質問 (FAQ)
          </h3>
          <span className="text-[10px] text-[#bbcabf]">タップして展開</span>
        </div>

        <div className="space-y-1.5">
          {faqItems.map((faq, idx) => (
            <div
              key={idx}
              className="rounded-xl bg-[#131b2e] border border-slate-800 overflow-hidden transition-all"
            >
              <button
                type="button"
                onClick={() => toggleFaq(idx)}
                className="w-full p-3 flex items-center justify-between text-left gap-2 cursor-pointer hover:bg-slate-700/50"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className="w-5 h-5 rounded-full bg-[#10b981]/15 text-[#4edea3] text-xs font-bold flex items-center justify-center shrink-0">
                    Q
                  </span>
                  <span className="text-xs font-bold text-white truncate">{faq.q}</span>
                </div>
                <span className={`material-symbols-outlined text-[#bbcabf] text-[18px] transition-transform ${openFaqIndex === idx ? 'rotate-180' : ''}`}>
                  expand_more
                </span>
              </button>

              {openFaqIndex === idx && (
                <div className="px-3 pb-3 pt-1 text-xs text-[#bbcabf] leading-relaxed border-t border-slate-800 bg-[#0b1326]/50">
                  {faq.a}
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Section 2: Contact Form */}
      <section className="space-y-2">
        <div className="flex items-center gap-1.5 px-1 text-xs font-bold text-white">
          <span className="material-symbols-outlined text-[#4edea3] text-[18px]">rate_review</span>
          <span>お問い合わせ・ご意見</span>
        </div>

        <form onSubmit={handleSubmit} className="p-4 rounded-2xl bg-[#131b2e] border border-slate-800 space-y-3 shadow-md">
          {/* Category Chips */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-white">カテゴリ *</label>
            <div className="grid grid-cols-2 gap-1.5">
              {[
                { id: 'feature', label: '機能の改善要望', icon: 'lightbulb' },
                { id: 'bug', label: '不具合の報告', icon: 'bug_report' },
                { id: 'stock', label: '銘柄データの誤り', icon: 'data_table' },
                { id: 'other', label: 'その他', icon: 'more_horiz' }
              ].map(cat => (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => setSelectedCategory(cat.id as any)}
                  className={`py-2 px-2 rounded-xl text-xs font-semibold flex items-center justify-center gap-1 transition-all ${
                    selectedCategory === cat.id
                      ? 'bg-[#10b981] text-[#003824] font-bold shadow'
                      : 'bg-[#0b1326] text-[#bbcabf] border border-slate-800 hover:text-white'
                  }`}
                >
                  <span className="material-symbols-outlined text-[16px]">{cat.icon}</span>
                  <span>{cat.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Name */}
          <div className="space-y-1">
            <div className="flex justify-between text-xs">
              <label htmlFor="sender-name-input" className="text-white font-medium">お名前 / ニックネーム</label>
              <span className="text-[10px] text-[#bbcabf]">任意</span>
            </div>
            <input
              id="sender-name-input"
              type="text"
              value={senderName}
              onChange={e => setSenderName(e.target.value)}
              placeholder="配当雪だるま"
              className="w-full h-10 px-3 rounded-xl bg-[#0b1326] border border-slate-700 text-white text-xs focus:border-[#4edea3] outline-none"
            />
          </div>

          {/* Email */}
          <div className="space-y-1">
            <div className="flex justify-between text-xs">
              <label htmlFor="sender-email-input" className="text-white font-medium">返信用メールアドレス</label>
              <span className="text-[10px] text-[#bbcabf]">返信が必要な場合</span>
            </div>
            <input
              id="sender-email-input"
              type="email"
              value={senderEmail}
              onChange={e => setSenderEmail(e.target.value)}
              placeholder="investor@example.com"
              className="w-full h-10 px-3 rounded-xl bg-[#0b1326] border border-slate-700 text-white text-xs focus:border-[#4edea3] outline-none"
            />
          </div>

          {/* Message Content */}
          <div className="space-y-1">
            <div className="flex justify-between text-xs">
              <label htmlFor="feedback-message-textarea" className="text-white font-medium">お問い合わせ内容・ご要望 *</label>
              <span className="font-mono text-[10px] text-[#4edea3]">{message.length} / 1,000</span>
            </div>
            <textarea
              id="feedback-message-textarea"
              rows={4}
              maxLength={1000}
              value={message}
              onChange={e => setMessage(e.target.value)}
              placeholder="具体的な銘柄コードや再現手順、こうなったら嬉しい機能などを教えてください。"
              className="w-full p-3 rounded-xl bg-[#0b1326] border border-slate-700 text-white text-xs focus:border-[#4edea3] outline-none resize-none leading-relaxed"
            />
          </div>

          {/* Diagnostics toggle */}
          <div className="p-2.5 rounded-xl bg-[#0b1326] border border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px] text-[#4edea3]">phone_android</span>
              <div>
                <p className="text-xs font-bold text-white">端末診断情報を添付</p>
                <p className="text-[10px] text-[#bbcabf]">v1.2.0 (Build 248) • 資産データ非包含</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setAttachDiagnostics(!attachDiagnostics)}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors ${attachDiagnostics ? 'bg-[#10b981]' : 'bg-slate-700'}`}
            >
              <span className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${attachDiagnostics ? 'translate-x-5' : 'translate-x-0'}`} />
            </button>
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full h-11 rounded-xl bg-[#10b981] hover:brightness-110 text-[#003824] font-bold text-xs flex items-center justify-center gap-2 shadow active:scale-98 transition-all"
          >
            <span className="material-symbols-outlined text-[18px]">send</span>
            <span>{isSubmitting ? '送信中...' : '送信する'}</span>
          </button>
        </form>
      </section>

      {/* Community Links */}
      <section className="space-y-2">
        <h4 className="text-xs font-bold text-white flex items-center gap-1.5 px-1">
          <span className="material-symbols-outlined text-[#4edea3] text-[18px]">groups</span>
          公式コミュニティ・応援
        </h4>

        <div className="space-y-1.5">
          <a
            href="https://twitter.com"
            target="_blank"
            rel="noopener noreferrer"
            className="p-3 rounded-2xl bg-[#131b2e] border border-slate-800 flex items-center justify-between hover:bg-[#171f33] transition-colors"
          >
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-full bg-slate-800 flex items-center justify-center text-[#4edea3]">
                <span className="material-symbols-outlined text-[18px]">tag</span>
              </div>
              <div>
                <p className="font-bold text-xs text-white">開発チーム公式 X (旧Twitter)</p>
                <p className="text-[10px] text-[#bbcabf]">最新の配当データ更新・アプデ速報を発信中</p>
              </div>
            </div>
            <span className="material-symbols-outlined text-[#bbcabf] text-[16px]">open_in_new</span>
          </a>
        </div>
      </section>
    </div>
  );
};
