import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users, Briefcase, Armchair, Gauge, Play, Database, Sparkles, Activity, CheckCircle2, Clock, FlaskConical } from 'lucide-react';
import { apiGet, apiPost, errMsg } from '../../api/client';
import { Overview as Ov, RunSummary } from '../../api/types';
import { StatCard, SectionHeader, Badge, statusTone, Spinner, Callout, fmtNum, fmtPct, fmtMs } from '../../components/ui';
import { Donut } from '../../components/charts';

export default function AdminOverview() {
  const nav = useNavigate();
  const [ov, setOv] = useState<Ov | null>(null);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [starting, setStarting] = useState(false);
  const [err, setErr] = useState('');

  const load = async () => {
    try {
      const [o, r] = await Promise.all([apiGet<Ov>('/admin/overview'), apiGet<any>('/admin/runs', { page: 0, size: 4 })]);
      setOv(o); setRuns(r.content ?? []);
    } catch (e) { setErr(errMsg(e)); }
  };
  useEffect(() => { void load(); }, []);

  const startRun = async () => {
    setStarting(true); setErr('');
    try {
      const r = await apiPost<any>('/admin/runs', { policyKey: 'balanced' });
      nav(`/admin/runs/${r.id}`);
    } catch (e) { setErr(errMsg(e)); setStarting(false); }
  };

  if (!ov && !err) return <Spinner label="Preparing the command center…" />;
  if (err && !ov) return <Callout tone="danger" title="Could not load">{err}</Callout>;
  const a = ov?.allocation;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-1 text-[11px] font-semibold text-navy-500 ring-1 ring-navy-900/10">
              <Sparkles className="h-3.5 w-3.5 text-saffron-500" />
              {ov?.aiMode ?? '—'}
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-saffron-50 px-3 py-1 text-[11px] font-semibold text-saffron-700 ring-1 ring-saffron-200"
              title="Every record in this environment is deterministically generated for demonstration.">
              <FlaskConical className="h-3.5 w-3.5" /> Synthetic Demonstration Data
            </span>
          </div>
          <h1 className="font-display text-[24px] font-bold text-ink">Command Center</h1>
          <p className="mt-1 text-[13.5px] text-navy-500">
            The full picture of the current dataset, candidate readiness and the latest allocation outcome.
          </p>
        </div>
        <div className="flex gap-2.5">
          <button className="btn-ghost" onClick={() => nav('/admin/scenarios')}><Database className="h-4 w-4" /> Change scenario</button>
          <button className="btn-primary" onClick={startRun} disabled={starting}>
            <Play className="h-4 w-4" /> {starting ? 'Starting…' : 'Run global allocation'}
          </button>
        </div>
      </div>

      {err && <Callout tone="warn">{err}</Callout>}

      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatCard icon={<Users className="h-5 w-5" />} label="Candidates" value={fmtNum(ov?.scenario?.candidateCount)}
          sub={ov?.scenario ? <span className="inline-flex items-center gap-1.5"><Database className="h-3.5 w-3.5" /> {ov.scenario.name} · v{ov.scenario.version}</span> : '—'} />
        <StatCard icon={<Briefcase className="h-5 w-5" />} label="Opportunities" value={fmtNum(ov?.scenario?.opportunityCount)} tone="saffron"
          sub={`${fmtNum(ov?.scenario?.seatCount)} total seats`} />
        <StatCard icon={<Gauge className="h-5 w-5" />} label="Readiness" value={fmtPct(ov?.readiness?.average)} tone="leaf"
          sub={`${fmtNum(ov?.readiness?.ready)} ready · ${fmtNum(ov?.readiness?.partial)} partial · ${fmtNum(ov?.readiness?.incomplete)} incomplete`} />
        <StatCard icon={<Armchair className="h-5 w-5" />} label="Latest allocation" value={a ? fmtNum(a.allocated) : '—'} tone={a ? 'navy' : 'navy'}
          sub={a ? <>of {fmtNum(a.totalCandidates)} candidates · seat use {fmtPct(a.seatUtilization)}</> : 'No completed run yet'} />
      </div>

      <div className="grid gap-5 xl:grid-cols-5">
        <div className="card p-5 xl:col-span-2">
          <SectionHeader title="Latest run" sub="Status at a glance" right={ov?.latestRun && (
            <button className="btn-ghost btn-sm" onClick={() => nav(`/admin/runs/${ov.latestRun!.id}`)}>Open run</button>
          )} />
          {ov?.latestRun ? (
            <div>
              <div className="flex items-center justify-between gap-3 rounded-xl bg-navy-50/70 p-4 ring-1 ring-navy-100">
                <div>
                  <div className="font-display text-[15px] font-bold text-ink">Allocation Run #{ov.latestRun.number}</div>
                  <div className="mt-0.5 text-[12.5px] text-navy-500">{ov.latestRun.scenario} · {ov.latestRun.status}</div>
                </div>
                <Badge tone={statusTone(ov.latestRun.status)} dot>{ov.latestRun.status}</Badge>
              </div>
              <div className="mt-4 space-y-0">
                {(ov.latestRun.stages ?? []).map((s, i, arr) => (
                  <div key={s.stage} className="flex items-center gap-3">
                    <div className={`flex h-6 w-6 items-center justify-center rounded-full text-[10px] font-bold ${i < arr.length - 1 ? 'bg-saffron-500 text-white' : 'bg-navy-200 text-navy-600'}`}>
                      {i < arr.length - 1 ? <CheckCircle2 className="h-3.5 w-3.5" /> : i + 1}
                    </div>
                    <div className="flex-1">
                      <div className="text-[13px] font-semibold text-navy-800">{s.stage}</div>
                      <div className="stage-line mt-1.5"><div className="stage-fill" style={{ width: `${Math.min(100, Math.max(6, s.ms * 2))}%` }} /></div>
                    </div>
                    <span className="text-[11.5px] font-medium text-navy-400">{fmtMs(s.ms)}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="py-8 text-center text-[13px] text-navy-400">
              No allocation has run on this dataset yet.<br />Start one to see the full decision history.
            </div>
          )}
        </div>

        <div className="card p-5 xl:col-span-3">
          <SectionHeader title="Decision history" sub="Newest allocation runs first"
            right={<button className="btn-ghost btn-sm" onClick={() => nav('/admin/runs')}>View all</button>} />
          {runs.length === 0 ? (
            <div className="py-8 text-center text-[13px] text-navy-400">Nothing yet — the first run will appear here.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px]">
                <thead><tr className="border-b border-navy-100"><th className="th">Run</th><th className="th">Scenario</th><th className="th">Status</th><th className="th">Solver</th><th className="th">Time</th></tr></thead>
                <tbody>
                  {runs.map(r => (
                    <tr key={r.id} className="tr-hover cursor-pointer border-b border-navy-50 last:border-0" onClick={() => nav(`/admin/runs/${r.id}`)}>
                      <td className="td font-semibold text-ink">#{r.number}{r.reallocation ? ' (reallocation)' : ''}</td>
                      <td className="td">{r.scenario}</td>
                      <td className="td"><Badge tone={statusTone(r.status)} dot>{r.status}</Badge></td>
                      <td className="td text-navy-500">{r.solverStatus ?? '—'}</td>
                      <td className="td text-navy-500">{fmtMs(r.totalRuntimeMs)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {a && (
        <div className="grid gap-5 lg:grid-cols-3">
          <div className="card p-5">
            <SectionHeader title="Readiness mix" />
            <Donut data={[
              { name: 'Ready', value: ov?.readiness?.ready ?? 0, color: '#1b8a5a' },
              { name: 'Partial', value: ov?.readiness?.partial ?? 0, color: '#ee8420' },
              { name: 'Incomplete', value: ov?.readiness?.incomplete ?? 0, color: '#94a3b8' },
            ]} />
          </div>
          <div className="card p-5 lg:col-span-2">
            <SectionHeader title="Latest outcome" sub={a ? `Allocation Run #${a.runNumber}` : ''} />
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {[
                ['Allocated', `${fmtNum(a.allocated)} / ${fmtNum(a.totalCandidates)}`],
                ['Average suitability', fmtPct(a.avgSuitability)],
                ['Preference match', fmtPct(a.preferenceSatisfaction)],
                ['Seat utilization', fmtPct(a.seatUtilization)],
              ].map(([k, v]) => (
                <div key={k} className="rounded-xl bg-navy-50/70 p-3.5 ring-1 ring-navy-100">
                  <div className="text-[11px] font-semibold uppercase tracking-wide text-navy-400">{k}</div>
                  <div className="font-display mt-1.5 text-[20px] font-bold text-ink">{v}</div>
                </div>
              ))}
            </div>
            <div className="mt-4 flex items-center gap-2 text-[12px] text-navy-400">
              <Activity className="h-3.5 w-3.5" /> Full comparison with the sequential baseline and factor-level explanations live inside the run.
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
