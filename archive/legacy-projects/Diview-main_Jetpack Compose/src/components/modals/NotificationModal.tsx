import React, { useState } from 'react';
import { useApp } from '../../context/AppContext';
import { notificationsList } from '../../data/mockData';

export const NotificationModal: React.FC = () => {
  const { isNotificationsOpen, closeNotifications, navigateTo } = useApp();
  const [items, setItems] = useState(notificationsList);
  const [filterType, setFilterType] = useState<'all' | 'dividend' | 'signal'>('all');

  if (!isNotificationsOpen) return null;

  const unreadCount = items.filter(i => i.unread).length;

  const markAllAsRead = () => {
    setItems(prev => prev.map(i => ({ ...i, unread: false })));
  };

  const filteredItems = items.filter(i => {
    if (filterType === 'all') return true;
    return i.category === filterType;
  });

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 backdrop-blur-md flex flex-col justify-end p-0"
      onClick={closeNotifications}
    >
      <div
        className="w-full max-w-lg mx-auto bg-[#131b2e] border-t border-slate-700/80 rounded-t-3xl p-4 flex flex-col gap-3.5 shadow-2xl max-h-[85vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="w-12 h-1 bg-slate-700 rounded-full mx-auto -mt-1 shrink-0" />

        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-[#171f33] border border-slate-700/60 flex items-center justify-center text-[#4edea3]">
              <span className="material-symbols-outlined text-[20px]">notifications_active</span>
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-sm text-white">通知履歴・アクティビティ</h3>
                {unreadCount > 0 ? (
                  <span className="px-2 py-0.5 rounded-full bg-[#10b981]/20 text-[#4edea3] text-[10px] font-bold">
                    未読 {unreadCount}件
                  </span>
                ) : (
                  <span className="px-2 py-0.5 rounded-full bg-slate-800 text-[#bbcabf] text-[10px]">
                    すべて既読
                  </span>
                )}
              </div>
              <p className="text-[10px] text-[#bbcabf]">買場シグナル・配当入金・権利確定日速報</p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeNotifications}
            className="w-8 h-8 rounded-full bg-[#171f33] text-[#bbcabf] hover:text-white flex items-center justify-center"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Tabs and Actions */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
            <button
              type="button"
              onClick={() => setFilterType('all')}
              className={`px-3 py-1 rounded-full text-[11px] font-bold shrink-0 transition-all ${
                filterType === 'all'
                  ? 'bg-[#10b981] text-[#003824]'
                  : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
              }`}
            >
              すべて ({items.length})
            </button>
            <button
              type="button"
              onClick={() => setFilterType('dividend')}
              className={`px-3 py-1 rounded-full text-[11px] font-semibold shrink-0 transition-all ${
                filterType === 'dividend'
                  ? 'bg-[#10b981] text-[#003824]'
                  : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
              }`}
            >
              権利落ち・入金 (3)
            </button>
            <button
              type="button"
              onClick={() => setFilterType('signal')}
              className={`px-3 py-1 rounded-full text-[11px] font-semibold shrink-0 transition-all ${
                filterType === 'signal'
                  ? 'bg-[#10b981] text-[#003824]'
                  : 'bg-[#171f33] text-[#bbcabf] hover:text-white'
              }`}
            >
              買場シグナル (2)
            </button>
          </div>

          <button
            type="button"
            onClick={markAllAsRead}
            className="text-[#4edea3] hover:underline text-[11px] font-semibold flex items-center gap-0.5 shrink-0"
          >
            <span className="material-symbols-outlined text-[14px]">done_all</span>
            <span>すべて既読</span>
          </button>
        </div>

        {/* Status Strip */}
        <div className="px-3 py-1.5 rounded-lg bg-[#0b1326] border border-slate-800 flex items-center justify-between text-[11px] text-[#bbcabf]">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-[#4edea3] animate-pulse" />
            <span>配当金通知: <strong className="text-[#4edea3]">ON</strong> / 買場アラート: <strong className="text-[#4edea3]">ON</strong></span>
          </div>
          <span className="text-[10px]">即時プッシュ</span>
        </div>

        {/* Notifications List */}
        <div className="flex flex-col gap-2 max-h-80 overflow-y-auto pr-0.5">
          {filteredItems.map(item => (
            <article
              key={item.id}
              onClick={() => {
                if (item.code && item.code !== '投信') {
                  closeNotifications();
                  navigateTo('stock-detail', item.code);
                }
              }}
              className={`p-3 rounded-xl border flex flex-col gap-1.5 transition-all cursor-pointer ${
                item.unread
                  ? 'bg-[#171f33] border-[#4edea3]/40 hover:bg-slate-700/60'
                  : 'bg-[#0b1326] border-slate-800/80 opacity-80 hover:opacity-100'
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 min-w-0">
                  {item.unread && <span className="w-2 h-2 rounded-full bg-[#4edea3] shrink-0" />}
                  <span
                    className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                      item.category === 'signal'
                        ? 'bg-[#10b981]/20 text-[#4edea3]'
                        : 'bg-sky-500/20 text-sky-400'
                    }`}
                  >
                    {item.title}
                  </span>
                  <span className="font-mono font-bold text-xs text-white bg-slate-800 px-1.5 py-0.5 rounded">
                    {item.code}
                  </span>
                  <span className="font-bold text-xs text-white truncate">{item.name}</span>
                </div>
                <span className="text-[10px] text-[#bbcabf] shrink-0">{item.time}</span>
              </div>
              <p className="text-xs text-slate-200 leading-relaxed pl-3.5">{item.body}</p>
            </article>
          ))}
        </div>

        {/* Close Button */}
        <div className="flex justify-between items-center pt-1 border-t border-slate-800">
          <button
            type="button"
            onClick={() => {
              closeNotifications();
              navigateTo('settings');
            }}
            className="text-xs text-[#bbcabf] hover:text-white flex items-center gap-1"
          >
            <span className="material-symbols-outlined text-[16px]">settings</span>
            <span>通知設定を変更</span>
          </button>
          <button
            type="button"
            onClick={closeNotifications}
            className="px-4 py-1.5 rounded-lg bg-[#171f33] hover:bg-slate-700 text-white text-xs font-semibold"
          >
            閉じる
          </button>
        </div>
      </div>
    </div>
  );
};
