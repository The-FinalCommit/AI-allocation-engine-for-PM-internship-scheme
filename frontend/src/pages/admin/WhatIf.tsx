import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FlaskConical, Play, RotateCcw, GitCompareArrows, SlidersHorizontal } from 'lucide-react';
import { apiGet, apiPost, errMsg } from '../../api/client';
import { PolicyPreset, SimulationRow } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, statusTone, KV, fmtNum, fmtPct, fmtMs, fmtTime, EmptyState } from '../../components/ui';

const WEIGHT_KEYS: [string, string][] = [
  ['skills', 'Skills'], ['qualification', 'Qualification'], ['interest', 'Interest'],
  ['location', 'Location'], ['preference', 'Preference'], ['learning', 'Learning potential'], ['experience', 'Experience'],
];

export default function AdminWhatIf() {
  const nav = useNavigate();
  const [presets, setPresets] = useState<PolicyPreset[]>([]);
  const [runs, setRuns] = useState<any[]>([]);
  const [baseRunId, setBaseRunId] = useState<number | null>(null);
  const [weights, setWeights] = useState<Record<string, number>>({ skills: 35, qualification: 15, interest: 15, location: 10, preference: 10, learning: 10, experience: 5 });
  const [fullCoverage, setFullCoverage] = useState(false);
  const [floor, setFloor] = useState<number>(0);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [sims, setSims] = useState<SimulationRow[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const [p, r, s] = await Promise.all([
          apiGet<PolicyPreset[]>('/admin/policies'),
          apiGet<any>('/admin/runs', { page: 0, size: 8 }),
          apiGet<any>('/admin/simulations', { page: 0, size: 8 }),
        ]);
        setPresets(p);
        const completed = (r.content ?? []).filter((x: any) => x.status === 'Completed' && !x.reallocation);
        setRuns(completed);
        if (completed.length > 0) setBaseRunId(completed[0].id);
        setSims(s.content ?? []);
      } catch (e) { setErr(errMsg(e)); }
    })();
  }, []);

  const total = useMemo(() => Object.values(weights).reduce((a, b) => a + b, 0), [weights]);
  const loadPreset = (key: string) => {
    const p = presets.find(x => x.key === key);
    if (!p) return;
    try { setWeights(JSON.parse(p.weightsJson)); } catch { }
    setFullCoverage(!!p.fullCoverage);
    setFloor(p.fairnessFloorPct ?? 0);
  };

  const refreshSims = async () => {
    const s = await apiGet<any>('/admin/simulations', { page: 0, size: 8 });
    setSims(s.content ?? []);
    return (s.content ?? []) as SimulationRow[];
  };

  const runSim = async () => {
    if (baseRunId == null) { setErr('Please select the allocation run to compare against.'); return; }
    setBusy(true); setErr('');
    try {
      await apiPost('/admin/simulations', { baseRunId, weights, fullCoverage, fairnessFloorPct: floor || null });
      // The simulation runs asynchronously — poll until it reaches a final state.
      for (let i = 0; i < 50; i++) {
        await new Promise(res => setTimeout(res, 1200));
        const list = await refreshSims();
        const top = list[0];
        if (top && !['Running', 'Queued', 'In progress'].includes(top.status)) break;
      }
      await refreshSims();
    } catch (e) { setErr(errMsg(e)); }
    setBusy(false);
  };

  const base = runs.find(r => r.id === baseRunId);
  const lastSim = sims[0];
  const lastSimRunning = !!lastSim && ['Running', 'Queued', 'In progress'].includes(lastSim.status);
  let lastMetrics: any = null;
  try { if (lastSim?.metricsJson) lastMetrics = JSON.parse(lastSim.metricsJson); } catch { }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">Policy Lab (What-if)</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">
          Change the policy and see what the allocation would have been — without touching the real decision.
          Simulations are sandboxed and never alter any published result.
        </p>
      </div>

      {err && <Callout tone="warn">{err}</Callout>}
      {runs.length === 0 && (
        <Callout tone="info" title="Nothing to simulate yet">
          A completed allocation run is needed as the baseline. <button className="font-bold underline" onClick={() => nav('/admin')}>Go to the Command Center</button> and run one.
        </Callout>
      )}

      <div className="grid gap-4 xl:grid-cols-5">
        <div className="card p-5 xl:col-span-3">
          <SectionHeader title="Configure the what-if policy" />
          <div className="space-y-4">
            <div>
              <label className="label">Baseline allocation run</label>
              <select className="input" value={baseRunId ?? ''} onChange={e => setBaseRunId(Number(e.target.value))}>
                {runs.map(r => <option key={r.id} value={r.id}>Run #{r.number} — {r.scenario} ({r.policyName})</option>)}
              </select>
            </div>
            <div>
              <label className="label">Start from a demonstration policy</label>
              <div className="flex flex-wrap gap-2">
                {presets.map(p => (
                  <button key={p.key} className={`btn btn-sm ${p.isDefault ? 'bg-ink text-white' : 'border border-navy-900/10 bg-white text-navy-700 hover:bg-navy-50'}`} onClick={() => loadPreset(p.key)}>
                    {p.name}
                  </button>
                ))}
              </div>
              <p className="mt-2 text-[11.5px] text-navy-400">These are PRAGATI default configurable weights for demonstration — not official government weights.</p>
            </div>
            <div>
              <div className="mb-2 flex items-center justify-between">
                <label className="label mb-0"><span className="inline-flex items-center gap-1.5"><SlidersHorizontal className="h-3.5 w-3.5" /> Weighting (must total 100%)</span></label>
                <Badge tone={total === 100 ? 'green' : 'red'}>{total}%</Badge>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                {WEIGHT_KEYS.map(([k, label]) => (
                  <div key={k}>
                    <div className="mb-1 flex items-center justify-between text-[12px] font-semibold text-navy-600">
                      <span>{label}</span><span className="text-navy-400">{weights[k]}%</span>
                    </div>
                    <input type="range" min={0} max={60} value={weights[k]}
                      onChange={e => setWeights(w => ({ ...w, [k]: Number(e.target.value) }))}
                      className="w-full accent-saffron-500" />
                  </div>
                ))}
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="flex cursor-pointer items-center justify-between rounded-xl border border-navy-900/10 p-3.5">
                <div>
                  <div className="text-[13px] font-bold text-ink">Allocate every candidate</div>
                  <div className="text-[11.5px] text-navy-400">A hard rule: nobody is left unallocated.</div>
                </div>
                <input type="checkbox" className="h-4.5 w-4.5 h-[18px] w-[18px] accent-saffron-500" checked={fullCoverage} onChange={e => setFullCoverage(e.target.checked)} />
              </label>
              <div>
                <div className="mb-1 flex items-center justify-between text-[12px] font-semibold text-navy-600">
                  <span>Minimum share for rural candidates</span><span className="text-navy-400">{floor}%</span>
                </div>
                <input type="range" min={0} max={95} value={floor} onChange={e => setFloor(Number(e.target.value))} className="w-full accent-saffron-500" />
                <p className="mt-1 text-[11px] text-navy-400">A hard fairness floor enforced by the optimizer.</p>
              </div>
            </div>
            <div className="flex gap-2.5">
              <button className="btn-primary" onClick={runSim} disabled={busy || baseRunId == null || total !== 100}>
                <Play className="h-4 w-4" /> {busy ? 'Simulating…' : 'Run what-if simulation'}
              </button>
              <button className="btn-ghost" onClick={() => loadPreset('balanced')}><RotateCcw className="h-4 w-4" /> Reset to defaults</button>
            </div>
            {total !== 100 && <div className="text-[12px] font-medium text-red-600">Weights must total exactly 100% — currently {total}%.</div>}
          </div>
        </div>

        <div className="space-y-4 xl:col-span-2">
          {lastMetrics ? (
            <div className="card p-5">
              <SectionHeader title="Latest simulation" sub={`${lastSim?.policyName} vs current policy · ${fmtMs(lastSim.runtimeMs)}`}
                right={<Badge tone={statusTone(lastSim?.status ?? '')} dot>{lastSim?.status}</Badge>} />
              <div className="space-y-1.5">
                <SimRow k="Candidates allocated" a={lastMetrics.current.allocated} b={lastMetrics.scenario.allocated} />
                <SimRow k="Average suitability" a={lastMetrics.current.suitability} b={lastMetrics.scenario.suitability} pct />
                <SimRow k="Preference match" a={lastMetrics.current.preferenceSatisfaction} b={lastMetrics.scenario.preferenceSatisfaction} pct />
                <SimRow k="Seat utilization" a={lastMetrics.current.seatUtilization} b={lastMetrics.scenario.seatUtilization} pct />
              </div>
              <div className="mt-4 rounded-xl bg-saffron-50/70 p-3.5 text-[12.5px] leading-relaxed text-navy-700 ring-1 ring-saffron-200">
                <b>{lastMetrics.affected}</b> candidate{lastMetrics.affected === 1 ? '' : 's'} would change seats; {fmtPct(lastMetrics.stability)} of assignments stay put.
                This is a sandbox — the published allocation is unchanged.
              </div>
            </div>
          ) : lastSimRunning ? (
            <div className="card"><Spinner label={`Simulating “${lastSim?.policyName}” against run #${lastSim?.baseRunId}…`} /></div>
          ) : (
            <div className="card"><EmptyState title="No simulation yet" hint="Configure a policy on the left and run the first what-if simulation." icon={<FlaskConical className="h-8 w-8" />} /></div>
          )}
          <div className="card p-5">
            <SectionHeader title="Simulation history" />
            {sims.length === 0 ? <div className="py-4 text-center text-[13px] text-navy-400">Simulations you run will be listed here.</div> : (
              <div className="space-y-2">
                {sims.map(s => (
                  <div key={s.id} className="flex items-center justify-between rounded-xl border border-navy-100 px-3.5 py-2.5">
                    <div>
                      <div className="text-[13px] font-semibold text-ink">{s.policyName}</div>
                      <div className="text-[11.5px] text-navy-400">base run #{s.baseRunId} · {fmtTime(s.createdAt)}</div>
                    </div>
                    <div className="flex items-center gap-2">
                      {s.affected != null && <Badge tone="saffron">{s.affected} affected</Badge>}
                      <Badge tone={statusTone(s.status)} dot>{s.status}</Badge>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function SimRow({ k, a, b, pct }: { k: string; a: number; b: number; pct?: boolean }) {
  const better = b > a;
  const worse = b < a;
  return (
    <div className="flex items-center justify-between rounded-lg px-2 py-1.5">
      <span className="text-[12.5px] text-navy-500">{k}</span>
      <span className="flex items-center gap-2 text-[13px] font-semibold">
        <span className="text-navy-400">{pct ? fmtPct(a) : fmtNum(a)}</span>
        <span className="text-navy-300">→</span>
        <span className={better ? 'text-emerald-600' : worse ? 'text-red-600' : 'text-ink'}>{pct ? fmtPct(b) : fmtNum(b)}</span>
      </span>
    </div>
  );
}
