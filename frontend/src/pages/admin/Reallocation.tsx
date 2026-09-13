import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCcw, Plus, Trash2, ArrowRight } from 'lucide-react';
import { apiGet, apiPost, errMsg } from '../../api/client';
import { SectionHeader, Spinner, Callout, Badge, KV, fmtNum, fmtTime, EmptyState } from '../../components/ui';

interface Change { type: string; opportunityId?: number; candidateId?: number; newCapacity?: number; }

export default function AdminReallocation() {
  const nav = useNavigate();
  const [runs, setRuns] = useState<any[]>([]);
  const [opps, setOpps] = useState<any[]>([]);
  const [baseRunId, setBaseRunId] = useState<number | null>(null);
  const [placed, setPlaced] = useState<Record<number, number>>({});
  const [changes, setChanges] = useState<Change[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [result, setResult] = useState<any>(null);

  useEffect(() => {
    (async () => {
      try {
        const [r, o] = await Promise.all([apiGet<any>('/admin/runs', { page: 0, size: 8 }), apiGet<any>('/opportunities', { page: 0, size: 100 })]);
        const completed = (r.content ?? []).filter((x: any) => x.status === 'Completed');
        setRuns(completed);
        if (completed.length > 0) setBaseRunId(completed[0].id);
        setOpps(o.content ?? []);
      } catch (e) { setErr(errMsg(e)); }
    })();
  }, []);

  // How many people actually hold a seat in each opportunity for the chosen base run —
  // shown in the picker so a capacity change is always aimed at a live opportunity.
  useEffect(() => {
    if (baseRunId == null) { setPlaced({}); return; }
    (async () => {
      try {
        const c = await apiGet<any[]>(`/admin/runs/${baseRunId}/conflicts`);
        const m: Record<number, number> = {};
        (c ?? []).forEach(x => { m[x.opportunityId] = x.allocated ?? 0; });
        setPlaced(m);
      } catch { setPlaced({}); }
    })();
  }, [baseRunId]);

  const mostPlacedOpp = () => {
    let best: any = null;
    for (const o of opps) if ((placed[o.id] ?? 0) > (best ? (placed[best.id] ?? 0) : 0)) best = o;
    return best ?? opps[0];
  };

  const addChange = (c: Change) => setChanges(cs => [...cs, c]);
  const removeChange = (i: number) => setChanges(cs => cs.filter((_, x) => x !== i));

  const run = async () => {
    if (baseRunId == null) { setErr('Select the completed run to reallocate from.'); return; }
    if (changes.length === 0) { setErr('Add at least one operational change.'); return; }
    setBusy(true); setErr(''); setResult(null);
    try {
      const r = await apiPost<any>('/admin/reallocations', { baseRunId, changes });
      const id = r.id;
      for (let i = 0; i < 20; i++) {
        await new Promise(res => setTimeout(res, 1500));
        const d = await apiGet<any>(`/admin/runs/${id}`);
        if (d.run.status !== 'Queued' && !d.run.status.toLowerCase().includes('progress')) {
          const diff = await apiGet<any>(`/admin/reallocations/${id}/diff`).catch(() => null);
          setResult({ run: d.run, diff });
          break;
        }
      }
      const d = await apiGet<any>(`/admin/runs/${id}`);
      const diff = await apiGet<any>(`/admin/reallocations/${id}/diff`).catch(() => null);
      setResult({ run: d.run, diff });
    } catch (e) { setErr(errMsg(e)); }
    setBusy(false);
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">Reallocation</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">
          Apply operational changes — a capacity change, a withdrawn candidate or a withdrawn opportunity —
          re-optimize, and see exactly who moves and why.
        </p>
      </div>

      {err && <Callout tone="warn">{err}</Callout>}

      <div className="grid gap-4 xl:grid-cols-2">
        <div className="card p-5">
          <SectionHeader title="1 · Choose the baseline" />
          <label className="label">Completed allocation run</label>
          <select className="input" value={baseRunId ?? ''} onChange={e => setBaseRunId(Number(e.target.value))}>
            {runs.map(r => <option key={r.id} value={r.id}>Run #{r.number} — {r.scenario}</option>)}
          </select>

          <div className="mt-5">
            <label className="label">2 · Operational changes</label>
            <div className="space-y-3">
              {changes.map((c, i) => (
                <div key={i} className="rounded-xl border border-navy-100 p-3.5">
                  {c.type === 'CAPACITY' ? (
                    <div className="flex items-center gap-3">
                      <select className="input flex-1" value={c.opportunityId ?? ''} onChange={e => setChanges(cs => cs.map((x, j) => j === i ? { ...x, opportunityId: Number(e.target.value) } : x))}>
                        <option value="">Select opportunity…</option>
                        {opps.map(o => <option key={o.id} value={o.id}>{o.title} — {o.capacity} seats, {placed[o.id] ?? 0} filled in this run</option>)}
                      </select>
                      <input type="number" min={0} max={200} className="input w-24" value={c.newCapacity ?? 0}
                        onChange={e => setChanges(cs => cs.map((x, j) => j === i ? { ...x, newCapacity: Number(e.target.value) } : x))} placeholder="new seats" />
                      <button className="btn-ghost btn-sm" onClick={() => removeChange(i)}><Trash2 className="h-4 w-4" /></button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-3">
                      <select className="input flex-1" value={c.opportunityId ?? ''} onChange={e => setChanges(cs => cs.map((x, j) => j === i ? { ...x, opportunityId: Number(e.target.value) } : x))}>
                        <option value="">Select opportunity to withdraw…</option>
                        {opps.map(o => <option key={o.id} value={o.id}>{o.title}</option>)}
                      </select>
                      <Badge tone="red">withdrawn</Badge>
                      <button className="btn-ghost btn-sm" onClick={() => removeChange(i)}><Trash2 className="h-4 w-4" /></button>
                    </div>
                  )}
                </div>
              ))}
              <div className="flex flex-wrap gap-2">
                <button className="btn-ghost btn-sm" onClick={() => { const o = mostPlacedOpp(); if (o) addChange({ type: 'CAPACITY', opportunityId: o.id, newCapacity: 1 }); }}><Plus className="h-4 w-4" /> Change capacity</button>
                <button className="btn-ghost btn-sm" onClick={() => { const o = mostPlacedOpp(); if (o) addChange({ type: 'WITHDRAW_OPPORTUNITY', opportunityId: o.id }); }}><Plus className="h-4 w-4" /> Withdraw opportunity</button>
              </div>
            </div>
          </div>

          <div className="mt-5">
            <button className="btn-primary w-full" onClick={run} disabled={busy}>
              <RefreshCcw className="h-4 w-4" /> {busy ? 'Re-optimizing…' : 'Apply changes & re-optimize'}
            </button>
          </div>
        </div>

        <div className="space-y-4">
          {!result ? (
            <div className="card"><EmptyState title="Result will appear here" hint="Apply operational changes on the left. The re-optimized run and a full before-and-after comparison will appear here." icon={<ArrowRight className="h-8 w-8" />} /></div>
          ) : (
            <>
              <div className="card p-5">
                <SectionHeader title={`Result — Allocation Run #${result.run.number}`} sub={`Child of run #${result.run.parentRunNumber ?? '—'}`}
                  right={<button className="btn-ghost btn-sm" onClick={() => nav(`/admin/runs/${result.run.id}`)}>Open run</button>} />
                <KV k="Status" v={<Badge tone={result.run.status === 'Completed' ? 'green' : 'amber'} dot>{result.run.status}</Badge>} />
                <KV k="Solver" v={result.run.solverStatus ?? '—'} />
                <KV k="Completed" v={fmtTime(result.run.completedAt)} />
              </div>
              {result.diff && (
                <div className="card p-5">
                  <SectionHeader title="What changed" sub={`Compared with the parent allocation (run #${result.diff.parentRunNumber})`} />
                  {(result.diff.moved + result.diff.added + result.diff.removed) === 0 && (
                    <div className="mb-3 rounded-xl bg-navy-50/70 p-3.5 text-[12.5px] leading-relaxed text-navy-600 ring-1 ring-navy-100">
                      This operational change did not alter the optimal allocation — every assignment stays exactly where the
                      optimizer placed it. The re-optimization ran and confirmed the result; changes only move people when the
                      optimum genuinely requires it.
                    </div>
                  )}
                  <div className="mb-3 grid grid-cols-4 gap-2 text-center">
                    {[['Moved', result.diff.moved, 'saffron'], ['Added', result.diff.added, 'green'], ['Removed', result.diff.removed, 'red'], ['Unchanged', result.diff.unchanged, 'navy']].map(([l, v, tone]: any) => (
                      <div key={l} className={`rounded-xl bg-navy-50/70 p-3 ring-1 ring-navy-100`}>
                        <div className="font-display text-[20px] font-bold text-ink">{fmtNum(v)}</div>
                        <div className="text-[11px] font-semibold uppercase tracking-wide text-navy-400">{l}</div>
                      </div>
                    ))}
                  </div>
                  <div className="max-h-[260px] overflow-y-auto">
                    {(result.diff.entries ?? []).map((e: any) => (
                      <div key={e.candidateId} className="flex items-center justify-between border-b border-navy-50 py-2 last:border-0">
                        <div className="min-w-0">
                          <div className="truncate text-[13px] font-semibold text-ink">{e.candidateName}</div>
                          <div className="truncate text-[12px] text-navy-400">{e.before ?? '—'} → {e.after ?? '—'}</div>
                        </div>
                        <Badge tone={e.status === 'MOVED' ? 'saffron' : e.status === 'ADDED' ? 'green' : e.status === 'REMOVED' ? 'red' : 'gray'}>{e.status}</Badge>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
