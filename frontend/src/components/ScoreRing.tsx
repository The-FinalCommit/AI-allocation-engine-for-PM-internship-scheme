import React from 'react';

export function ScoreRing({ value, size = 72, stroke = 7, label }: { value: number; size?: number; stroke?: number; label?: string }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const off = c * (1 - Math.max(0, Math.min(100, value)) / 100);
  const color = value >= 80 ? '#1b8a5a' : value >= 50 ? '#ee8420' : '#dc2626';
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#e8edf5" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={off} style={{ transition: 'stroke-dashoffset .6s cubic-bezier(.2,.7,.3,1)' }} />
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className="font-display text-[15px] font-bold text-ink" style={{ fontSize: size / 4.6 }}>{Math.round(value)}</span>
        {label && <span className="text-[9px] font-semibold uppercase tracking-wide text-navy-400">{label}</span>}
      </div>
    </div>
  );
}
