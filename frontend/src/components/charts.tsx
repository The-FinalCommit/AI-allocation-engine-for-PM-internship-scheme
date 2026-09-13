import React from 'react';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, PieChart, Pie, Cell, LineChart, Line, Legend } from 'recharts';

const AXIS = { fontSize: 11, fill: '#64748b' } as const;

export function HBar({ data, dataKey = 'value', nameKey = 'name', color = '#ee8420', height = 220, suffix = '' }: {
  data: any[]; dataKey?: string; nameKey?: string; color?: string | ((v: any, i: number) => string); height?: number; suffix?: string;
}) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ left: 8, right: 24, top: 4, bottom: 4 }}>
        <CartesianGrid horizontal={false} stroke="#eef2f7" />
        <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} />
        <YAxis type="category" dataKey={nameKey} width={110} tick={AXIS} tickLine={false} axisLine={false} />
        <Tooltip formatter={(v: any) => `${v}${suffix}`} contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', fontSize: 12 }} />
        <Bar dataKey={dataKey} radius={[0, 6, 6, 0]} barSize={14}
          fill={typeof color === 'function' ? undefined : color}
          shape={typeof color === 'function' ? undefined : undefined}>
          {typeof color === 'function' && data.map((_, i) => <Cell key={i} fill={color(null, i)} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function Donut({ data, height = 210 }: { data: { name: string; value: number; color: string }[]; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <PieChart>
        <Pie data={data} dataKey="value" nameKey="name" innerRadius="58%" outerRadius="85%" paddingAngle={2} strokeWidth={2}>
          {data.map((d, i) => <Cell key={i} fill={d.color} />)}
        </Pie>
        <Tooltip contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', fontSize: 12 }} />
        <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
      </PieChart>
    </ResponsiveContainer>
  );
}

export function Trend({ data, series, height = 240, suffix = '' }: {
  data: any[]; series: { key: string; color: string }[]; height?: number; suffix?: string;
}) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ left: 0, right: 16, top: 8, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#eef2f7" />
        <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
        <YAxis tick={AXIS} tickLine={false} axisLine={false} width={44} />
        <Tooltip formatter={(v: any) => `${v}${suffix}`} contentStyle={{ borderRadius: 12, border: '1px solid #e2e8f0', fontSize: 12 }} />
        <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
        {series.map(s => (
          <Line key={s.key} type="monotone" dataKey={s.key} stroke={s.color} strokeWidth={2.5} dot={{ r: 3, strokeWidth: 0, fill: s.color }} activeDot={{ r: 5 }} />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}

export function FactorBars({ factors }: { factors: { label: string; fit: number; contribution: number; weight: number }[] }) {
  const maxC = Math.max(...factors.map(f => f.contribution), 1);
  return (
    <div className="space-y-2.5">
      {factors.map(f => (
        <div key={f.label}>
          <div className="mb-1 flex items-baseline justify-between text-[12px]">
            <span className="font-semibold text-navy-700">{f.label}</span>
            <span className="text-navy-400">fit {f.fit.toFixed(0)} · adds {f.contribution.toFixed(1)} pts</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-navy-100/70">
            <div className="h-full rounded-full bg-gradient-to-r from-saffron-400 to-saffron-600" style={{ width: `${(f.contribution / maxC) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}
