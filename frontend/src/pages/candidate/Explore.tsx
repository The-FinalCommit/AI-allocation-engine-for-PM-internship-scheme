import React, { useEffect, useRef, useState } from 'react';
import { Briefcase, MapPin, Clock, Search, Star, CheckCircle2, XCircle, ChevronRight, X } from 'lucide-react';
import { apiGet, errMsg } from '../../api/client';
import { Opp, OppDetail } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, Modal, fmtNum, EmptyState } from '../../components/ui';

const SECTORS: [string, string][] = [
  ['SOFTWARE_IT', 'Software & IT'], ['DATA_ANALYTICS', 'Data & Analytics'], ['FINANCE_BANKING', 'Finance & Banking'],
  ['DESIGN_MEDIA', 'Design & Media'], ['MARKETING_COMMUNICATION', 'Marketing & Communication'],
  ['PUBLIC_ADMIN_POLICY', 'Public Administration & Policy'], ['HEALTHCARE_BIOTECH', 'Healthcare & Biotech'],
  ['MANUFACTURING_OPERATIONS', 'Manufacturing & Operations'], ['RESEARCH_DEVELOPMENT', 'Research & Development'],
  ['ENVIRONMENTAL_ENERGY', 'Environmental & Energy'],
];
const STATES = ['Andhra Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Delhi', 'Gujarat', 'Haryana', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Odisha', 'Punjab', 'Rajasthan', 'Tamil Nadu', 'Telangana', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal'];

interface Filters { q: string; sector: string; state: string; }

export default function CandidateExplore() {
  const [rows, setRows] = useState<Opp[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Filters>({ q: '', sector: '', state: '' });
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [sel, setSel] = useState<OppDetail | null>(null);
  const reqSeq = useRef(0);

  // One composed filter state; every request carries the EXACT filters it was
  // created from (no stale-closure reads), and out-of-order responses are
  // discarded via a monotonic sequence number.
  const load = (p: number, f: Filters) => {
    const seq = ++reqSeq.current;
    setLoading(true); setErr('');
    apiGet<any>('/opportunities', { page: p, size: 9, q: f.q || undefined, state: f.state || undefined, sector: f.sector || undefined, availableOnly: true })
      .then(d => {
        if (seq !== reqSeq.current) return; // a newer request superseded this one
        setRows(d.content ?? []); setTotal(d.totalElements ?? 0); setPage(p);
      })
      .catch(e => { if (seq === reqSeq.current) setErr(errMsg(e)); })
      .finally(() => { if (seq === reqSeq.current) setLoading(false); });
  };

  const applyFilter = (patch: Partial<Filters>) => {
    const next = { ...filters, ...patch };
    setFilters(next);
    load(0, next); // filters always reset pagination
  };

  const hasFilters = filters.q !== '' || filters.sector !== '' || filters.state !== '';
  useEffect(() => { load(0, { q: '', sector: '', state: '' }); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  const open = async (id: number) => {
    try { setSel(await apiGet<OppDetail>(`/opportunities/${id}`)); } catch (e) { setErr(errMsg(e)); }
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">Explore Opportunities</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">Every open internship, with your personal eligibility shown for each.</p>
      </div>

      {err && <Callout tone="warn">{err}</Callout>}

      <div className="card flex flex-wrap items-center gap-3 p-4">
        <div className="relative min-w-[220px] flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 z-10 h-4 w-4 -translate-y-1/2 text-navy-400" />
          <input className="input" style={{ paddingLeft: '2.5rem' }} placeholder="Search by title or description…" value={filters.q}
            onChange={e => setFilters(f => ({ ...f, q: e.target.value }))}
            onKeyDown={e => e.key === 'Enter' && load(0, filters)} />
        </div>
        <select className="input w-auto" value={filters.sector} onChange={e => applyFilter({ sector: e.target.value })}>
          <option value="">All sectors</option>
          {SECTORS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <select className="input w-auto" value={filters.state} onChange={e => applyFilter({ state: e.target.value })}>
          <option value="">All states</option>
          {STATES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <button className="btn-dark" onClick={() => load(0, filters)} disabled={loading}><Search className="h-4 w-4" /> Search</button>
        {hasFilters && (
          <button className="btn-ghost btn-sm" onClick={() => applyFilter({ q: '', sector: '', state: '' })}>
            <X className="h-3.5 w-3.5" /> Clear filters
          </button>
        )}
      </div>

      {loading ? <div className="card"><Spinner label="Loading opportunities…" /></div> : rows.length === 0 ? (
        <div className="card">
          <EmptyState title="No opportunities match your filters"
            hint={hasFilters ? 'Try widening the sector or state, or clear the filters to see every open internship.' : 'No open internships right now — check back after the next allocation cycle.'}
            icon={<Briefcase className="h-8 w-8" />} />
          {hasFilters && <div className="flex justify-center pb-10"><button className="btn-ghost btn-sm" onClick={() => applyFilter({ q: '', sector: '', state: '' })}>Clear all filters</button></div>}
        </div>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {rows.map(o => (
              <button key={o.id} onClick={() => open(o.id)} className="card card-hover fade-up flex flex-col p-5 text-left">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-display text-[15px] font-bold leading-snug text-ink">{o.title}</h3>
                    <div className="mt-1 text-[12px] text-navy-400">{o.sector}</div>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    {o.eligible
                      ? <Badge tone="green" dot>Eligible for you</Badge>
                      : <Badge tone="red">Not eligible</Badge>}
                    {o.fitScore != null && (
                      <span className={`font-display text-[15px] font-bold ${o.fitScore >= 70 ? 'text-emerald-600' : o.fitScore >= 50 ? 'text-saffron-600' : 'text-red-500'}`}>
                        {o.fitScore.toFixed(0)}<span className="text-[10px] font-semibold text-navy-400"> /100 fit</span>
                      </span>
                    )}
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5 text-[12px] text-navy-500">
                  <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{o.city ? `${o.city}, ` : ''}{o.state}</span>
                  <span className="inline-flex items-center gap-1"><Briefcase className="h-3.5 w-3.5" />{fmtNum(o.capacity)} seats</span>
                  <span className="inline-flex items-center gap-1"><Clock className="h-3.5 w-3.5" />{o.durationMonths} months</span>
                </div>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {o.mandatorySkills.slice(0, 3).map(s => <span key={s} className="chip bg-navy-50 text-navy-600 ring-1 ring-navy-100">{s}</span>)}
                  {o.niceSkills?.slice(0, 2).map(s => <span key={s} className="chip bg-saffron-50 text-saffron-600 ring-1 ring-saffron-100">{s}</span>)}
                </div>
                <div className="mt-4 flex items-center justify-between border-t border-navy-50 pt-3">
                  <span className="text-[11.5px] font-semibold uppercase tracking-wide text-navy-300">Min: {o.minQualification.replace('_', ' ')}</span>
                  <span className="inline-flex items-center gap-1 text-[12.5px] font-bold text-saffron-600">View <ChevronRight className="h-4 w-4" /></span>
                </div>
              </button>
            ))}
          </div>
          <div className="flex items-center justify-between text-[13px] text-navy-500">
            <span>{fmtNum(total)} opportunities found</span>
            <div className="flex gap-2">
              <button className="btn-ghost btn-sm" disabled={page === 0 || loading} onClick={() => load(page - 1, filters)}>Previous</button>
              <button className="btn-ghost btn-sm" disabled={(page + 1) * 9 >= total || loading} onClick={() => load(page + 1, filters)}>Next</button>
            </div>
          </div>
        </>
      )}

      <Modal open={!!sel} onClose={() => setSel(null)} title={sel?.card.title ?? ''} wide>
        {sel && (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2">
              <Badge tone={sel.card.eligible ? 'green' : 'red'} dot>{sel.card.eligible ? 'Eligible for you' : 'Not eligible for you'}</Badge>
              <Badge tone="navy">{sel.card.sector}</Badge>
              {sel.card.myPreferenceRank && <Badge tone="saffron"><Star className="h-3 w-3" /> your #{sel.card.myPreferenceRank} preference</Badge>}
              {sel.card.fitScore != null && (
                <span className="chip bg-navy-900 text-white">
                  Your fit: <span className="font-bold">{sel.card.fitScore.toFixed(0)}/100</span>
                </span>
              )}
            </div>
            {!sel.card.eligible && (sel.card.eligibilityReasons?.length ?? 0) > 0 && (
              <Callout tone="warn" title="Why you are not eligible yet">
                <ul className="mt-1 list-disc space-y-0.5 pl-4">{(sel.card.eligibilityReasons ?? []).map((r: string) => <li key={r}>{r}</li>)}</ul>
              </Callout>
            )}
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {[['Location', `${sel.card.city ? sel.card.city + ', ' : ''}${sel.card.state}`], ['Seats', String(sel.card.capacity)], ['Duration', `${sel.card.durationMonths} months`], ['Min qualification', sel.card.minQualification.replace('_', ' ')]].map(([k, v]) => (
                <div key={k} className="rounded-xl bg-navy-50/70 p-3 ring-1 ring-navy-100">
                  <div className="text-[10.5px] font-semibold uppercase tracking-wide text-navy-400">{k}</div>
                  <div className="mt-1 text-[13px] font-bold text-ink">{v}</div>
                </div>
              ))}
            </div>
            <div>
              <div className="mb-1.5 text-[12px] font-bold uppercase tracking-wide text-navy-500">Skills required</div>
              <div className="flex flex-wrap gap-1.5">
                {sel.card.mandatorySkills.map(s => <span key={s} className="chip bg-navy-100/70 text-navy-700 ring-1 ring-navy-200">{s} · required</span>)}
                {sel.card.niceSkills?.map(s => <span key={s} className="chip bg-saffron-50 text-saffron-700 ring-1 ring-saffron-200">{s} · preferred</span>)}
              </div>
            </div>
            {sel.description && (
              <div>
                <div className="mb-1.5 text-[12px] font-bold uppercase tracking-wide text-navy-500">About this internship</div>
                <p className="whitespace-pre-line text-[13.5px] leading-relaxed text-navy-700">{sel.description}</p>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
