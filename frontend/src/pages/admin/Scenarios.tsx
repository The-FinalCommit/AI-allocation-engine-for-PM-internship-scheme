import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Database, CheckCircle2, Flame, Globe2, Scale, ShieldAlert, Gauge, GitCompareArrows, Info } from 'lucide-react';
import { apiGet, apiPost, errMsg } from '../../api/client';
import { ScenarioSummary, DatasetSummary } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, fmtNum } from '../../components/ui';

const META: Record<string, { icon: any; tone: string; blurb: string }> = {
  STANDARD_SHOWCASE: { icon: Database, tone: 'bg-navy-50 text-navy-600', blurb: 'A realistic mixed field: diverse candidates, many sectors, genuine competition.' },
  MICRO_CONFLICT: { icon: GitCompareArrows, tone: 'bg-saffron-50 text-saffron-600', blurb: 'Four candidates, two seats. A small engineered proof that global allocation beats candidate-by-candidate.' },
  HIGH_CONFLICT: { icon: Flame, tone: 'bg-red-50 text-red-600', blurb: 'More candidates than seats across hot opportunities — pressure is real.' },
  GEOGRAPHIC: { icon: Globe2, tone: 'bg-sky-50 text-sky-600', blurb: 'Opportunities concentrated in a few states while demand spreads across regions.' },
  FAIRNESS: { icon: Scale, tone: 'bg-emerald-50 text-emerald-700', blurb: 'A mixed rural/urban population built to test allocation fairness by group.' },
  INFEASIBLE: { icon: ShieldAlert, tone: 'bg-amber-50 text-amber-700', blurb: 'Demand exceeds capacity by design. The system must say so — and explain why.' },
  BENCHMARK: { icon: Gauge, tone: 'bg-violet-50 text-violet-600', blurb: 'A large field to show the engine stays fast under real scale.' },
};


export default function AdminScenarios() {
  const nav = useNavigate();
  const [list, setList] = useState<ScenarioSummary[]>([]);
  const [current, setCurrent] = useState<DatasetSummary | null>(null);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const [s, o] = await Promise.all([apiGet<ScenarioSummary[]>('/admin/scenarios'), apiGet<any>('/admin/overview')]);
        setList(s); setCurrent(o.scenario);
      } catch (e) { setErr(errMsg(e)); }
    })();
  }, []);

  const load = async (key: string) => {
    setBusyKey(key); setErr('');
    try {
      const d = await apiPost<DatasetSummary>(`/admin/scenarios/${key}/load`);
      setCurrent(d);
      const s = await apiGet<ScenarioSummary[]>('/admin/scenarios');
      setList(s);
    } catch (e) { setErr(errMsg(e)); }
    setBusyKey(null);
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">Scenarios & Dataset</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">
          Each scenario is a self-contained, system-generated demonstration field. Loading one replaces the current dataset.
        </p>
      </div>

      {current && (
        <div className="card flex flex-wrap items-center justify-between gap-3 border-saffron-200 bg-gradient-to-r from-saffron-50/70 to-white p-4 ring-1 ring-saffron-200">
          <div className="flex items-center gap-3">
            <div className="rounded-xl bg-saffron-500 p-2.5 text-white"><Database className="h-5 w-5" /></div>
            <div>
              <div className="text-[13.5px] font-bold text-ink">Current dataset: {current.name}</div>
              <div className="text-[12.5px] text-navy-500">
                {fmtNum(current.candidateCount)} candidates · {fmtNum(current.opportunityCount)} opportunities · {fmtNum(current.seatCount)} seats · version {current.version}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone="saffron">synthetic</Badge>
            <button className="btn-ghost btn-sm" onClick={() => nav('/admin')}>Open command center</button>
          </div>
        </div>
      )}

      {err && <Callout tone="warn">{err}</Callout>}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {list.map(s => {
          const m = META[s.key] ?? { icon: Database, tone: 'bg-navy-50 text-navy-600', blurb: '' };
          const active = current?.key === s.key;
          return (
            <div key={s.key} className={`card card-hover fade-up flex flex-col p-5 ${active ? 'ring-2 ring-saffron-300' : ''}`}>
              <div className="flex items-start justify-between gap-3">
                <div className={`rounded-xl p-2.5 ${m.tone}`}><m.icon className="h-5 w-5" /></div>
                {active && <Badge tone="green" dot>Active</Badge>}
              </div>
              <h3 className="font-display mt-3 text-[15.5px] font-bold text-ink">{s.name}</h3>
              <p className="mt-1.5 flex-1 text-[12.5px] leading-relaxed text-navy-500">{m.blurb}</p>
              <div className="mt-4 flex items-center justify-between">
                <span className="text-[11px] font-semibold uppercase tracking-wide text-navy-300">
                  {s.active ? 'Loaded' : 'Not loaded'}
                </span>
                <button className={active ? 'btn-ghost btn-sm' : 'btn-dark btn-sm'} disabled={busyKey !== null} onClick={() => load(s.key)}>
                  {busyKey === s.key ? <Spinner label="" /> : active ? <span className="inline-flex items-center gap-1.5"><CheckCircle2 className="h-3.5 w-3.5" /> Reload</span> : 'Load scenario'}
                </button>
              </div>
            </div>
          );
        })}
      </div>

      <Callout tone="info" title="About this data">
        Every person, organisation and opportunity here is <b>synthetic and system-generated</b> for demonstration.
        Names, skills and results are created by the PRAGATI seeder — no real candidate data is used.
      </Callout>
      <div className="flex items-start gap-2 text-[12px] text-navy-400">
        <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
        Loading a new scenario keeps your run history, but runs on an older dataset version are marked as superseded.
      </div>
    </div>
  );
}
