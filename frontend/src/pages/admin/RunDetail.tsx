import React, { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import L from 'leaflet';
import {
  ArrowLeft, Download, XCircle, Trophy, GitCompareArrows, Scale, Map as MapIcon,
  Route, Fingerprint, HelpCircle, AlertTriangle, CheckCircle2,
} from 'lucide-react';
import { apiGet, apiPost, apiDownload, ApiError, errMsg } from '../../api/client';
import { RunDetail as RD, Comparison, WhyDto, Fairness, GeoRow, Movement, ConflictRow } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, statusTone, KV, Modal, fmtNum, fmtPct, fmtMs, fmtTime } from '../../components/ui';
import { HBar, FactorBars } from '../../components/charts';

const TABS = [
  { id: 'compare', label: 'Strategy comparison', icon: GitCompareArrows },
  { id: 'why', label: 'Why this allocation', icon: HelpCircle },
  { id: 'fairness', label: 'Fairness', icon: Scale },
  { id: 'geo', label: 'Geography', icon: MapIcon },
  { id: 'moves', label: 'Movements & conflicts', icon: Route },
  { id: 'prov', label: 'Provenance', icon: Fingerprint },
];

function runReportUrl(id: number) { return `/api/admin/runs/${id}/report`; }

export default function AdminRunDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const [sp] = useSearchParams();
  const [d, setD] = useState<RD | null>(null);
  const [cmp, setCmp] = useState<Comparison | null>(null);
  const [whyList, setWhyList] = useState<any[]>([]);
  const [whySel, setWhySel] = useState<any>(null);
  const [fair, setFair] = useState<Fairness | null>(null);
  const [geo, setGeo] = useState<GeoRow[]>([]);
  const [moves, setMoves] = useState<Movement[]>([]);
  const [conflicts, setConflicts] = useState<ConflictRow[]>([]);
  const [tab, setTab] = useState(() => sp.get('tab') ?? 'compare');
  const [err, setErr] = useState('');
  const [notFound, setNotFound] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [pdfBusy, setPdfBusy] = useState(false);
  const [geoErr, setGeoErr] = useState('');
  const mapRef = useRef<any>(null);
  const mapDivRef = useRef<HTMLDivElement>(null);
  const geoJsonRef = useRef<any>(null);
  const loadedRef = useRef(false);

  const runId = Number(id);
  const active = d && (d.run.status === 'Queued' || d.run.status === 'Running' || d.run.status.toLowerCase().includes('progress'));
  const activeRef = useRef(false);
  activeRef.current = !!active;

  const load = async (silent = false) => {
    try {
      const rd = await apiGet<RD>(`/admin/runs/${runId}`);
      setD(rd);
      if (rd.run.status === 'Completed' && !loadedRef.current) {
        loadedRef.current = true;
        try { setCmp(await apiGet<Comparison>(`/admin/runs/${runId}/comparison`)); } catch { }
        try { setFair(await apiGet<Fairness>(`/admin/runs/${runId}/fairness`)); } catch { }
        try { const g = await apiGet<any>(`/admin/runs/${runId}/geography`); setGeo(g.states ?? []); } catch { }
        try {
          const m = await apiGet<any>(`/admin/runs/${runId}/movements`, { limit: 100 });
          setMoves(m.content ?? m);
        } catch { }
        try { setConflicts((await apiGet<any>(`/admin/runs/${runId}/conflicts`)) ?? []); } catch { }
        try {
          const w = await apiGet<any>(`/admin/runs/${runId}/why/assigned`, { limit: 100 });
          const arr = Array.isArray(w) ? w : (w.content ?? []);
          if (arr.length > 0) setWhyList(arr);
        } catch {
          // fall back to movements for the picker
          try {
            const m = await apiGet<any>(`/admin/runs/${runId}/movements`, { limit: 40 });
            setWhyList((m.content ?? m) as Movement[]);
          } catch { }
        }
      }
    } catch (e) {
      if (silent) return;
      if (e instanceof ApiError && e.status === 404) { setNotFound(true); setErr(''); return; }
      setErr(errMsg(e));
    }
  };

  useEffect(() => {
    loadedRef.current = false;
    setD(null); setCmp(null); setFair(null); setGeo([]); setWhyList([]); setMoves([]); setConflicts([]); setWhySel(null);
    setNotFound(false); setErr('');
    void load();
    const t = setInterval(() => { if (activeRef.current) void load(true); }, 3000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId]);

  const openWhy = async (candidateId: number) => {
    try {
      const w = await apiGet<WhyDto>(`/admin/runs/${runId}/why/${candidateId}`);
      setWhySel(w);
    } catch (e) { setErr(errMsg(e)); }
  };

  // Leaflet map
  useEffect(() => {
    if (tab !== 'geo' || !mapDivRef.current || geo.length === 0 || geoErr) return;
    let cancelled = false;
    (async () => {
      try {
        if (!geoJsonRef.current) {
          const r = await fetch('/india-states.json');
          geoJsonRef.current = await r.json();
        }
        if (cancelled || !mapDivRef.current) return;
        if (mapRef.current) { mapRef.current.remove(); mapRef.current = null; }
        const map = L.map(mapDivRef.current, { zoomControl: true, attributionControl: false, minZoom: 4, maxZoom: 7 }).setView([23.5, 79], 4);
        mapRef.current = map;
        const color = (v: number) => (v >= 3 ? '#b91c1c' : v >= 2 ? '#ea580c' : v >= 1.2 ? '#f59e0b' : '#1b8a5a');
        const norm: Record<string, string> = { Orissa: 'Odisha', Uttaranchal: 'Uttarakhand', 'Andaman and Nicobar': 'Andaman and Nicobar Islands', Pondicherry: 'Puducherry' };
        L.geoJSON(geoJsonRef.current, {
          style: (f: any) => {
            const name = norm[f.properties?.NAME_1] ?? f.properties?.NAME_1;
            const row = geo.find(g => g.state === name);
            return { fillColor: row ? color(row.pressure) : '#dbe4f0', fillOpacity: 0.75, color: '#ffffff', weight: 1 };
          },
          onEachFeature: (f: any, layer: any) => {
            const name = norm[f.properties?.NAME_1] ?? f.properties?.NAME_1;
            const row = geo.find(g => g.state === name);
            layer.bindTooltip(
              `<div style="font-size:12px"><b>${name}</b><br/>Demand ${row?.demand ?? '—'} · Capacity ${row?.capacity ?? '—'}<br/>Allocated ${row?.allocated ?? '—'} · Pressure ${row?.pressure ?? '—'}</div>`
            );
          },
        }).addTo(map);
      } catch { setGeoErr('The map could not be loaded in this browser.'); }
    })();
    return () => { cancelled = true; };
  }, [tab, geo, geoErr]);

  useEffect(() => () => { if (mapRef.current) { mapRef.current.remove(); mapRef.current = null; } }, []);

  if (notFound) return (
    <div className="space-y-4">
      <div className="card flex flex-col items-center gap-3 p-10 text-center">
        <AlertTriangle className="h-8 w-8 text-saffron-500" />
        <div>
          <div className="font-display text-[17px] font-bold text-ink">This run doesn&apos;t exist</div>
          <p className="mt-1 text-[13px] text-navy-500">
            Allocation Run #{id} was not found. It may belong to an earlier dataset that has been replaced.
          </p>
        </div>
        <button className="btn-primary" onClick={() => nav('/admin/runs')}><ArrowLeft className="h-4 w-4" /> Back to all runs</button>
      </div>
    </div>
  );

  if (!d) return <div className="space-y-4">{err ? <Callout tone="danger">{err}</Callout> : <Spinner label="Loading run…" />}</div>;

  const r = d.run;
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <button className="btn-ghost btn-sm" onClick={() => nav('/admin/runs')}><ArrowLeft className="h-4 w-4" /> All runs</button>
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="font-display text-[22px] font-bold text-ink">Allocation Run #{r.number}</h1>
              <Badge tone={statusTone(r.status)} dot>{r.status}</Badge>
              {r.solverStatus && <Badge tone={statusTone(r.solverStatus)}>{r.solverStatus}</Badge>}
              {r.stale && <Badge tone="gray">superseded dataset</Badge>}
              {r.reallocation && <Badge tone="saffron">reallocation from #{r.parentRunNumber}</Badge>}
            </div>
            <div className="mt-0.5 text-[13px] text-navy-500">
              {r.scenario} · {r.policyName} · started {fmtTime(r.createdAt)} · {fmtMs(r.totalRuntimeMs)}
            </div>
          </div>
        </div>
        <div className="flex gap-2.5">
          {active && (
            <button className="btn-danger" disabled={cancelling} onClick={async () => {
              setCancelling(true);
              try { await apiPost(`/admin/runs/${runId}/cancel`); await load(); } catch (e) { setErr(errMsg(e)); }
              setCancelling(false);
            }}>
              <XCircle className="h-4 w-4" /> {cancelling ? 'Cancelling…' : 'Cancel run'}
            </button>
          )}
          {r.status === 'Completed' && (
            <button className="btn-dark" disabled={pdfBusy}
              onClick={async () => {
                setPdfBusy(true);
                try { await apiDownload(runReportUrl(runId), `PRAGATI-Report-Run${r.number}.pdf`); }
                catch (e) { setErr(errMsg(e)); }
                setPdfBusy(false);
              }}>
              <Download className="h-4 w-4" /> {pdfBusy ? 'Preparing…' : 'Download report (PDF)'}
            </button>
          )}
        </div>
      </div>

      {err && <Callout tone="warn">{err}</Callout>}

      {d.infeasibleMessage && (
        <Callout tone="danger" title="This configuration could not be satisfied.">
          {d.infeasibleMessage}
        </Callout>
      )}

      {d.dataset && (
        <div className="card flex flex-wrap items-center gap-x-8 gap-y-2 p-4 text-[13px]">
          <span className="font-semibold text-ink">{d.dataset.name}</span>
          <span className="text-navy-500">{fmtNum(d.dataset.candidateCount)} candidates</span>
          <span className="text-navy-500">{fmtNum(d.dataset.opportunityCount)} opportunities</span>
          <span className="text-navy-500">{fmtNum(d.dataset.seatCount)} seats</span>
          <span className="text-navy-500">dataset version {d.dataset.version}</span>
          <Badge tone="saffron">synthetic</Badge>
        </div>
      )}

      {d.stages.length > 0 && (
        <div className="card p-5">
          <SectionHeader title="How this run progressed" sub="Real stage timings from the engine" />
          <div className="grid gap-2.5 md:grid-cols-3 xl:grid-cols-6">
            {d.stages.map((s, i) => (
              <div key={s.stage} className="rounded-xl bg-navy-50/70 p-3 ring-1 ring-navy-100">
                <div className="flex items-center gap-2">
                  <div className="flex h-[22px] w-[22px] items-center justify-center rounded-full bg-saffron-500 text-[10px] font-bold text-white">{i + 1}</div>
                  <span className="text-[12px] font-bold leading-tight text-navy-800">{s.stage}</span>
                </div>
                <div className="mt-2 text-[11.5px] font-medium text-navy-400">{fmtMs(s.ms)}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {TABS.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
            className={`btn btn-sm ${tab === t.id ? 'bg-ink text-white' : 'border border-navy-900/10 bg-white text-navy-700 hover:bg-navy-50'}`}>
            <t.icon className="h-4 w-4" /> {t.label}
          </button>
        ))}
      </div>

      {tab === 'compare' && <CompareTab cmp={cmp} whyList={whyList} onWhy={openWhy} />}
      {tab === 'why' && (
        <div className="grid gap-4 lg:grid-cols-3">
          <div className="card p-5 lg:col-span-2">
            <SectionHeader title="Allocated candidates" sub="Pick any assignment to see exactly why it happened" />
            <WhyPicker whyList={whyList} total={cmp?.globalAllocated ?? 0} onWhy={openWhy} />
          </div>
          <div className="card p-5">
            <SectionHeader title="What you will see" />
            <div className="space-y-3 text-[13px] leading-relaxed text-navy-600">
              <p>Every assignment is explained factor by factor — skills, qualification, interest, location, preference, learning potential and experience — each with its weight and contribution to the final suitability score.</p>
              <p>The engine never invents a reason: the narrative is built from the stored suitability breakdown of that exact assignment.</p>
            </div>
          </div>
        </div>
      )}
      {tab === 'fairness' && <FairnessTab fair={fair} />}
      {tab === 'geo' && (
        <div className="grid gap-4 lg:grid-cols-5">
          <div className="card p-4 lg:col-span-3">
            <SectionHeader title="Allocation pressure by state" sub="Darker = stronger competition for local capacity" />
            {geo.length === 0 ? <Spinner label="Loading geography…" /> : geoErr ? <Callout tone="warn">{geoErr}</Callout> : (
              <div ref={mapDivRef} className="h-[420px] w-full" />
            )}
          </div>
          <div className="card p-5 lg:col-span-2">
            <SectionHeader title="State pressure table" />
            <div className="max-h-[420px] overflow-y-auto">
              <table className="w-full">
                <thead><tr className="border-b border-navy-100"><th className="th">State</th><th className="th">Demand</th><th className="th">Capacity</th><th className="th">Allocated</th><th className="th">Pressure</th></tr></thead>
                <tbody>
                  {geo.map(g => (
                    <tr key={g.state} className="border-b border-navy-50 last:border-0">
                      <td className="td font-medium">{g.state}</td>
                      <td className="td text-navy-500">{g.demand}</td>
                      <td className="td text-navy-500">{g.capacity}</td>
                      <td className="td text-navy-500">{g.allocated}</td>
                      <td className="td"><Badge tone={g.pressure >= 2 ? 'red' : g.pressure >= 1.2 ? 'amber' : 'green'}>{g.pressure.toFixed(1)}×</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
      {tab === 'moves' && <MovesTab moves={moves} conflicts={conflicts} />}
      {tab === 'prov' && d.provenance && <ProvTab p={d.provenance} />}

      <Modal open={!!whySel} onClose={() => setWhySel(null)} title={whySel ? `Why ${whySel.candidateName} → ${whySel.opportunityTitle ?? ''}` : ''} wide>
        {whySel && (
          <div className="space-y-4">
            {whySel.narrative && <Callout tone="info" title="In short">{whySel.narrative}</Callout>}
            <div className="flex flex-wrap gap-2">
              {whySel.sector && <Badge tone="navy">{whySel.sector}</Badge>}
              {whySel.state && <Badge tone="gray">{whySel.state}</Badge>}
              {typeof whySel.suitability === 'number' && <Badge tone="saffron">suitability {whySel.suitability.toFixed(1)}</Badge>}
              {whySel.preferenceRank && <Badge tone="green">rank #{whySel.preferenceRank} preference</Badge>}
            </div>
            {Array.isArray(whySel.factors) && whySel.factors.length > 0 ? (
              <FactorBars factors={whySel.factors} />
            ) : (
              <div className="text-[13px] text-navy-500">{whySel.reason || 'This assignment was unaffected by the operational change.'}</div>
            )}
            {Array.isArray(whySel.factors) && whySel.factors.length > 0 && (
              <div className="rounded-xl bg-navy-50/70 p-3.5 text-[12.5px] text-navy-600 ring-1 ring-navy-100">
                The candidate is eligible for {fmtNum(whySel.eligibleCount)} opportunities; this one carried the strongest overall suitability within the global optimum.
                {whySel.seatCount > 0 && <> {fmtNum(whySel.seatCount)} seats were available for it.</>}
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}

function CompareTab({ cmp, whyList, onWhy }: { cmp: Comparison | null; whyList: any[]; onWhy: (c: number) => void }) {
  if (!cmp) return <div className="card"><Spinner label="Computing the comparison…" /></div>;
  const rows = whyList.filter(w => w.factors).slice(0, 8);
  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-2">
        <div className="card border-emerald-200 bg-gradient-to-b from-emerald-50/60 to-white p-5 ring-1 ring-emerald-100">
          <div className="mb-3 flex items-center gap-2 text-emerald-700"><Trophy className="h-5 w-5" /><span className="font-display text-[15px] font-bold">PRAGATI global allocation</span></div>
          <MetricRow k="Candidates allocated" v={fmtNum(cmp.globalAllocated)} />
          <MetricRow k="Average suitability" v={fmtPct(cmp.globalSuitability)} />
          <MetricRow k="Preference satisfaction" v={fmtPct(cmp.globalPreferenceSatisfaction)} />
          <MetricRow k="Objective value" v={fmtNum(cmp.globalObjective)} />
        </div>
        <div className="card p-5">
          <div className="mb-3 flex items-center gap-2 text-navy-600"><GitCompareArrows className="h-5 w-5" /><span className="font-display text-[15px] font-bold">Sequential baseline (candidate-by-candidate)</span></div>
          <MetricRow k="Candidates allocated" v={fmtNum(cmp.baselineAllocated)} />
          <MetricRow k="Average suitability" v={fmtPct(cmp.baselineSuitability)} />
          <MetricRow k="Preference satisfaction" v={fmtPct(cmp.baselinePreferenceSatisfaction)} />
          <MetricRow k="Objective value" v={fmtNum(cmp.baselineObjective)} />
        </div>
      </div>
      <Callout tone={cmp.globalSuitability > cmp.baselineSuitability ? 'success' : 'info'} title="Reading this comparison">
        {cmp.summary} Both strategies run on the identical dataset, eligibility set and suitability scores —
        the difference comes only from global optimization.
      </Callout>
      {rows.length > 0 && (
        <div className="card p-5">
          <SectionHeader title="Sample explanations" sub="Click a candidate for the full factor-level breakdown" />
          <div className="grid gap-2 md:grid-cols-2">
            {rows.map((w: any) => (
              <button key={w.candidateId} onClick={() => onWhy(w.candidateId)}
                className="flex items-center justify-between rounded-xl border border-navy-100 p-3 text-left transition hover:border-saffron-300 hover:bg-saffron-50/40">
                <div>
                  <div className="text-[13px] font-bold text-ink">{w.candidateName}</div>
                  <div className="text-[12px] text-navy-500">{w.opportunityTitle} · {w.sector}</div>
                </div>
                <Badge tone="saffron">{w.suitability.toFixed(1)}</Badge>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
function MetricRow({ k, v }: { k: string; v: string }) {
  return <div className="flex items-baseline justify-between border-b border-navy-100/70 py-2 last:border-0"><span className="text-[13px] text-navy-500">{k}</span><span className="font-display text-[16px] font-bold text-ink">{v}</span></div>;
}

function WhyPicker({ whyList, total, onWhy }: { whyList: any[]; total: number; onWhy: (c: number) => void }) {
  const rows = whyList.slice(0, 100);
  if (rows.length === 0) return <div className="py-8 text-center text-[13px] text-navy-400">Assignments will appear here once the run completes.</div>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[520px]">
        <thead><tr className="border-b border-navy-100"><th className="th">Candidate</th><th className="th">Opportunity</th><th className="th">Suitability</th><th className="th" /></tr></thead>
        <tbody>
          {rows.map((w: any) => (
            <tr key={w.candidateId} className="tr-hover border-b border-navy-50 last:border-0">
              <td className="td font-semibold text-ink">{w.candidateName}</td>
              <td className="td">{w.opportunityTitle || w.after || '—'}</td>
              <td className="td"><Badge tone="saffron">{w.suitability != null ? w.suitability.toFixed(1) : w.afterScore != null ? w.afterScore.toFixed(1) : '—'}</Badge></td>
              <td className="td text-right"><button className="btn-dark btn-sm" onClick={() => onWhy(w.candidateId)}>Explain</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      {total > rows.length && (
        <p className="mt-2 text-[11.5px] text-navy-400">Showing the first {rows.length} of {total} assignments — select any candidate from the Comparison tab for a full factor-level explanation.</p>
      )}
    </div>
  );
}

function FairnessTab({ fair }: { fair: Fairness | null }) {
  if (!fair) return <div className="card"><Spinner label="Computing fairness metrics…" /></div>;
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <div className="card p-5">
        <SectionHeader title="By location type" sub="Rural, urban and semi-urban outcomes" />
        <HBar data={fair.ruralUrban.map(g => ({ name: g.groupValue, value: g.rate }))} dataKey="value" suffix="%" height={200} color="#1b8a5a" />
        <table className="mt-2 w-full">
          <thead><tr className="border-b border-navy-100"><th className="th">Group</th><th className="th">Population</th><th className="th">Allocated</th><th className="th">Rate</th><th className="th">Avg suitability</th></tr></thead>
          <tbody>{fair.ruralUrban.map(g => (
            <tr key={g.groupValue} className="border-b border-navy-50 last:border-0">
              <td className="td font-medium">{g.groupValue}</td><td className="td text-navy-500">{g.population}</td>
              <td className="td text-navy-500">{g.allocated}</td><td className="td">{fmtPct(g.rate)}</td><td className="td">{fmtPct(g.avgSuitability)}</td>
            </tr>))}
          </tbody>
        </table>
      </div>
      <div className="card p-5">
        <SectionHeader title="By state" sub="Top states by candidate population" />
        <HBar data={fair.states.slice(0, 8).map(g => ({ name: g.groupValue, value: g.rate }))} dataKey="value" suffix="%" height={220} color="#ee8420" />
        <table className="mt-2 w-full">
          <thead><tr className="border-b border-navy-100"><th className="th">State</th><th className="th">Population</th><th className="th">Allocated</th><th className="th">Rate</th></tr></thead>
          <tbody>{fair.states.slice(0, 6).map(g => (
            <tr key={g.groupValue} className="border-b border-navy-50 last:border-0">
              <td className="td font-medium">{g.groupValue}</td><td className="td text-navy-500">{g.population}</td>
              <td className="td text-navy-500">{g.allocated}</td><td className="td">{fmtPct(g.rate)}</td>
            </tr>))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function MovesTab({ moves, conflicts }: { moves: Movement[]; conflicts: ConflictRow[] }) {
  const changed = moves.filter(m => m.status !== 'UNCHANGED');
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <div className="card p-5">
        <SectionHeader title="Movements" sub={`${changed.length} of ${moves.length} assignments differ versus the sequential baseline`} />
        {changed.length === 0 ? (
          <div className="py-8 text-center text-[13px] text-navy-400">No movements recorded.</div>
        ) : (
          <div className="max-h-[430px] overflow-y-auto">
            <table className="w-full">
              <thead><tr className="border-b border-navy-100"><th className="th">Candidate</th><th className="th">Change</th><th className="th">Score</th></tr></thead>
              <tbody>{changed.map(m => (
                <tr key={m.candidateId} className="border-b border-navy-50 last:border-0">
                  <td className="td font-medium">{m.candidateName}</td>
                  <td className="td text-[12.5px] text-navy-500">
                    <Badge tone={m.status === 'ADDED' ? 'green' : m.status === 'REMOVED' ? 'red' : 'amber'}>{m.status}</Badge>
                    <div className="mt-1">{m.before ?? '—'} → {m.after ?? '—'}</div>
                  </td>
                  <td className="td text-navy-500">{m.beforeScore != null ? m.beforeScore.toFixed(1) : '—'} → {m.afterScore != null ? m.afterScore.toFixed(1) : '—'}</td>
                </tr>))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <div className="card p-5">
        <SectionHeader title="Most contested opportunities" sub="Highest demand pressure first" />
        {conflicts.length === 0 ? <div className="py-8 text-center text-[13px] text-navy-400">No conflict data.</div> : (
          <div className="max-h-[430px] overflow-y-auto">
            <table className="w-full">
              <thead><tr className="border-b border-navy-100"><th className="th">Opportunity</th><th className="th">Seats</th><th className="th">Filled</th><th className="th">Eligible demand</th><th className="th">Pressure</th></tr></thead>
              <tbody>{conflicts.map(c => (
                <tr key={c.opportunityId} className="border-b border-navy-50 last:border-0">
                  <td className="td font-medium">{c.title}</td>
                  <td className="td text-navy-500">{c.seats}</td>
                  <td className="td text-navy-500">{c.allocated}</td>
                  <td className="td text-navy-500">{c.eligible}</td>
                  <td className="td"><Badge tone={c.pressure >= 2 ? 'red' : c.pressure >= 1 ? 'amber' : 'green'}>{c.pressure.toFixed(1)}×</Badge></td>
                </tr>))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function ProvTab({ p }: { p: NonNullable<RD['provenance']> }) {
  return (
    <div className="card p-5">
      <SectionHeader title="Provenance" sub="Everything needed to reproduce this decision" />
      <div className="grid gap-x-10 md:grid-cols-2">
        <div>
          <KV k="Run identifier" v={`Run #${p.runNumber} (${p.runId})`} />
          <KV k="Scenario" v={p.scenario} />
          <KV k="Dataset version" v={`v${p.datasetVersion}`} />
          <KV k="Dataset fingerprint" v={<span className="font-mono text-[12px]">{p.datasetFingerprint}</span>} />
          <KV k="Field size" v={`${fmtNum(p.candidates)} candidates · ${fmtNum(p.opportunities)} opportunities · ${fmtNum(p.seats)} seats`} />
          <KV k="Seeder seed" v={p.seed} />
        </div>
        <div>
          <KV k="Policy" v={`${p.policyName} (${p.policyKey}, ${p.policyVersion})`} />
          <KV k="Weights" v={Object.entries(p.weights).map(([k, v]) => `${k} ${v}`).join(' · ')} />
          <KV k="Optimizer" v={p.optimizer} />
          <KV k="Solver outcome" v={p.solverStatus ?? '—'} />
          <KV k="Objective / variables / constraints" v={`${fmtNum(p.objective)} / ${fmtNum(p.variables)} / ${fmtNum(p.constraints)}`} />
          <KV k="Solver time" v={fmtMs(p.solverRuntimeMs)} />
          <KV k="Total run time" v={fmtMs(p.totalRuntimeMs)} />
        </div>
      </div>
      <div className="mt-4 flex items-start gap-2 rounded-xl bg-navy-50/70 p-3.5 text-[12.5px] leading-relaxed text-navy-600 ring-1 ring-navy-100">
        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
        Re-running this run with the same dataset version, seed and policy always produces the identical allocation — the engine is fully deterministic.
      </div>
    </div>
  );
}
