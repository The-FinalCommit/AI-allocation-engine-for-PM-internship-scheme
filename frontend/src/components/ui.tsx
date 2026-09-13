import React from 'react';
import { Loader2, Inbox, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';

export function Spinner({ label = 'Working…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2.5 py-10 text-navy-500">
      <Loader2 className="h-4.5 w-4.5 h-[18px] w-[18px] animate-spin text-saffron-500" />
      <span className="text-sm font-medium">{label}</span>
    </div>
  );
}

export function EmptyState({ title, hint, icon }: { title: string; hint?: string; icon?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-14 text-center">
      <div className="text-navy-300">{icon ?? <Inbox className="h-8 w-8" />}</div>
      <div className="text-sm font-semibold text-navy-700">{title}</div>
      {hint && <div className="max-w-sm text-[13px] text-navy-400">{hint}</div>}
    </div>
  );
}

const badgeTones: Record<string, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
  amber: 'bg-amber-50 text-amber-700 ring-1 ring-amber-200',
  red: 'bg-red-50 text-red-700 ring-1 ring-red-200',
  blue: 'bg-sky-50 text-sky-700 ring-1 ring-sky-200',
  navy: 'bg-navy-50 text-navy-700 ring-1 ring-navy-200',
  gray: 'bg-navy-100/60 text-navy-500 ring-1 ring-navy-200/60',
  saffron: 'bg-saffron-50 text-saffron-700 ring-1 ring-saffron-200',
};
export function Badge({ tone = 'gray', children, dot }: { tone?: keyof typeof badgeTones; children: React.ReactNode; dot?: boolean }) {
  return (
    <span className={`chip ${badgeTones[tone]}`}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />}
      {children}
    </span>
  );
}

export function statusTone(status: string): keyof typeof badgeTones {
  const s = status.toLowerCase();
  if (s.includes('optimal') || s.includes('completed') || s.includes('ready') || s.includes('allocated') || s.includes('online') || s.includes('active')) return 'green';
  if (s.includes('feasible')) return 'blue';
  if (s.includes('infeasible') || s.includes('fail') || s.includes('blocked')) return 'red';
  if (s.includes('progress') || s.includes('running') || s.includes('queued') || s.includes('partial') || s.includes('attention')) return 'amber';
  if (s.includes('superseded') || s.includes('stale')) return 'gray';
  return 'navy';
}

export function StatCard({ icon, label, value, sub, tone = 'navy' }: {
  icon?: React.ReactNode; label: string; value: React.ReactNode; sub?: React.ReactNode; tone?: 'navy' | 'saffron' | 'leaf' | 'red';
}) {
  const tones = { navy: 'text-navy-700 bg-navy-50', saffron: 'text-saffron-600 bg-saffron-50', leaf: 'text-emerald-700 bg-emerald-50', red: 'text-red-600 bg-red-50' };
  return (
    <div className="card card-hover fade-up p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[12px] font-semibold uppercase tracking-[0.08em] text-navy-400">{label}</div>
          <div className="font-display mt-2 truncate text-[26px] font-bold leading-none text-ink">{value}</div>
          {sub && <div className="mt-2 text-[12.5px] text-navy-500">{sub}</div>}
        </div>
        {icon && <div className={`shrink-0 rounded-xl p-2.5 ${tones[tone]}`}>{icon}</div>}
      </div>
    </div>
  );
}

export function SectionHeader({ title, sub, right }: { title: string; sub?: string; right?: React.ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 className="font-display text-[17px] font-bold text-ink">{title}</h2>
        {sub && <p className="mt-0.5 text-[13px] text-navy-500">{sub}</p>}
      </div>
      {right}
    </div>
  );
}

export function Callout({ tone = 'info', title, children }: { tone?: 'info' | 'warn' | 'success' | 'danger'; title?: string; children: React.ReactNode }) {
  const map = {
    info: { box: 'border-sky-200 bg-sky-50/70', ic: <Info className="h-4 w-4 text-sky-600" /> },
    warn: { box: 'border-amber-200 bg-amber-50/70', ic: <AlertTriangle className="h-4 w-4 text-amber-600" /> },
    success: { box: 'border-emerald-200 bg-emerald-50/70', ic: <CheckCircle2 className="h-4 w-4 text-emerald-600" /> },
    danger: { box: 'border-red-200 bg-red-50/70', ic: <AlertTriangle className="h-4 w-4 text-red-600" /> },
  };
  const m = map[tone];
  return (
    <div className={`rounded-xl border px-4 py-3 ${m.box}`}>
      <div className="flex gap-2.5">
        <div className="mt-0.5 shrink-0">{m.ic}</div>
        <div className="text-[13px] leading-relaxed text-navy-800">
          {title && <span className="mr-1 font-bold">{title}</span>}
          {children}
        </div>
      </div>
    </div>
  );
}

export function Modal({ open, onClose, title, children, wide }: { open: boolean; onClose: () => void; title: string; children: React.ReactNode; wide?: boolean }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/45 backdrop-blur-[2px]" onClick={onClose} />
      <div className={`card fade-up relative flex max-h-[86vh] w-full flex-col ${wide ? 'max-w-3xl' : 'max-w-xl'}`}>
        <div className="flex items-center justify-between border-b border-navy-100 px-5 py-4">
          <h3 className="font-display text-[15px] font-bold text-ink">{title}</h3>
          <button onClick={onClose} className="rounded-lg p-1.5 text-navy-400 transition hover:bg-navy-50 hover:text-ink"><X className="h-4.5 w-4.5 h-[18px] w-[18px]" /></button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
      </div>
    </div>
  );
}

export function KV({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-dashed border-navy-100 py-2 last:border-0">
      <span className="shrink-0 text-[12.5px] font-medium text-navy-400">{k}</span>
      <span className="text-right text-[13px] font-semibold text-navy-800">{v}</span>
    </div>
  );
}

export function fmtPct(n: number | null | undefined, digits = 1): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return `${n.toFixed(digits)}%`;
}
export function fmtNum(n: number | null | undefined): string {
  if (n === null || n === undefined) return '—';
  return n.toLocaleString('en-IN');
}
export function fmtMs(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—';
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}
export function fmtTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  } catch { return iso; }
}
