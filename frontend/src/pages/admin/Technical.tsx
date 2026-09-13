import React, { useEffect, useState } from 'react';
import { Cpu, Bot, ShieldCheck, Database, Gauge, AlertTriangle, CheckCircle2, CircleDashed } from 'lucide-react';
import { apiGet, errMsg } from '../../api/client';
import { Technical, DqCheck } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, statusTone, KV, fmtNum, fmtMs } from '../../components/ui';

export default function AdminTechnical() {
  const [t, setT] = useState<Technical | null>(null);
  const [dq, setDq] = useState<DqCheck[]>([]);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const [tech, d] = await Promise.all([apiGet<Technical>('/admin/technical'), apiGet<DqCheck[]>('/admin/data-quality')]);
        setT(tech); setDq(d);
      } catch (e) { setErr(errMsg(e)); }
      setLoading(false);
    })();
  }, []);

  if (loading) return <Spinner label="Checking system health…" />;
  if (!t) return <Callout tone="danger">{err}</Callout>;

  const online = t.ai.status === 'online';
  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">System Health</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">
          Where the honest implementation details live: services, the optimizer, and dataset health.
        </p>
      </div>
      {err && <Callout tone="warn">{err}</Callout>}

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card p-5">
          <SectionHeader title="AI & optimization services"
            right={<Badge tone={online ? 'green' : 'red'} dot>{online ? 'online' : 'offline'}</Badge>} />
          <KV k="AI mode" v={(t.ai.aiMode ?? '—') === 'deterministic-taxonomy' ? 'Deterministic taxonomy matching' : (t.ai.aiMode ?? '—')} />
          <KV k="Engine" v={t.ai.engineDescription ?? 'Deterministic skill matching — no embedding model is used.'} />
          <KV k="Embedding model" v={t.ai.embeddingModel ?? 'None — matching is rule-based over the validated taxonomy'} />
          <KV k="What AI does" v="Understands resumes, normalizes skills, flags gaps" />
          <KV k="What AI does not do" v="It never allocates, never overrides eligibility, and never invents candidate facts" />
          <KV k="Optimizer" v={t.ai.optimizer ?? '—'} />
          <KV k="Optimizer version" v={t.ai.optimizerVersion ?? '—'} />
          <KV k="Service version" v={t.ai.serviceVersion ?? '—'} />
          <div className="mt-4 flex items-start gap-2 rounded-xl bg-navy-50/70 p-3.5 text-[12.5px] leading-relaxed text-navy-600 ring-1 ring-navy-100">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
            The allocation engine is deterministic and runs even when the AI service is offline —
            AI assistance is never a dependency of a decision.
          </div>
        </div>

        <div className="card p-5">
          <SectionHeader title="Last solver run" />
          {t.solver ? (
            <>
              <KV k="Run" v={t.solver.lastRun ?? '—'} />
              <KV k="Outcome" v={t.solver.solverStatus ?? '—'} />
              <KV k="Eligible candidate–opportunity pairs" v={fmtNum(t.solver.eligiblePairs)} />
              <KV k="Variables / constraints" v={`${fmtNum(t.solver.variables)} / ${fmtNum(t.solver.constraints)}`} />
              <KV k="Objective value" v={fmtNum(t.solver.objective)} />
              <KV k="Solver time" v={fmtMs(t.solver.solverRuntimeMs)} />
              <KV k="Total run time" v={fmtMs(t.solver.totalRuntimeMs)} />
            </>
          ) : (
            <div className="py-6 text-center text-[13px] text-navy-400">No completed run yet.</div>
          )}
          {t.dataset && (
            <div className="mt-3 rounded-xl bg-navy-50/70 p-3.5 text-[12.5px] text-navy-600 ring-1 ring-navy-100">
              Current dataset: {t.dataset.scenario ?? '—'} · {fmtNum(t.dataset.candidates)} candidates · {fmtNum(t.dataset.opportunities)} opportunities · {fmtNum(t.dataset.seats)} seats (v{t.dataset.version})
            </div>
          )}
        </div>
      </div>

      <div className="card p-5">
        <SectionHeader title="Technology (for evaluators)" sub="Implementation details belong on this page — never on operator screens" />
        <div className="grid gap-x-10 md:grid-cols-2">
          <div>
            <KV k="Frontend" v={t.stack?.frontend ?? '—'} />
            <KV k="Backend" v={t.stack?.backend ?? '—'} />
            <KV k="AI service" v={t.stack?.aiService ?? '—'} />
          </div>
          <div>
            <KV k="Optimizer" v={t.stack?.optimizer ?? '—'} />
            <KV k="Database" v={t.stack?.database ?? '—'} />
            <KV k="API documentation" v={<a className="font-semibold text-saffron-600 underline" href="/swagger-ui.html" target="_blank" rel="noreferrer">Open the interactive API docs</a>} />
          </div>
        </div>
      </div>

      <div className="card p-5">
        <SectionHeader title="Dataset health" sub="Each check says what it is, why it matters and the fix" />
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px]">
            <thead>
              <tr className="border-b border-navy-100">
                <th className="th">Area</th><th className="th">Status</th><th className="th">What it checks</th><th className="th">Why it matters</th><th className="th">Fix</th><th className="th">Affected</th>
              </tr>
            </thead>
            <tbody>
              {dq.map(c => (
                <tr key={c.area} className="tr-hover border-b border-navy-50 last:border-0">
                  <td className="td font-semibold text-ink">{c.area}</td>
                  <td className="td"><Badge tone={c.status === 'READY' ? 'green' : c.status === 'ATTENTION' ? 'amber' : 'red'} dot>{c.status}</Badge></td>
                  <td className="td max-w-[220px] text-navy-600">{c.what}</td>
                  <td className="td max-w-[220px] text-navy-500">{c.why}</td>
                  <td className="td max-w-[220px] text-navy-500">{c.fix}</td>
                  <td className="td">{fmtNum(c.affected)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
