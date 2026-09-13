import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  GraduationCap, MapPin, Briefcase, CheckCircle2, Sparkles, ArrowRight,
  Clock, PartyPopper, Hourglass, ListChecks, Target, FlaskConical, Lightbulb,
} from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { apiGet, errMsg } from '../../api/client';
import { CandidateAllocation, Readiness, Recommendation } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, fmtNum, fmtTime } from '../../components/ui';
import { ScoreRing } from '../../components/ScoreRing';

export default function CandidateHome() {
  const { me } = useAuth();
  const nav = useNavigate();
  const [alloc, setAlloc] = useState<CandidateAllocation | null>(null);
  const [ready, setReady] = useState<Readiness | null>(null);
  const [actions, setActions] = useState<string[]>([]);
  const [history, setHistory] = useState<any[]>([]);
  const [recs, setRecs] = useState<Recommendation[]>([]);
  const [err, setErr] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const [a, d, h, r] = await Promise.all([
          apiGet<CandidateAllocation>('/candidates/me/allocation'),
          apiGet<any>('/candidates/me/dashboard'),
          apiGet<any>('/candidates/me/allocation/history'),
          apiGet<any>('/candidates/me/recommendations').catch(() => null),
        ]);
        setAlloc(a);
        setReady(d.readiness ?? null);
        setActions(d.nextActions ?? d.actions ?? []);
        setHistory(h ?? []);
        setRecs(r?.items ?? []);
      } catch (e) { setErr(errMsg(e)); }
    })();
  }, []);

  if (!alloc && !err) return <Spinner label="Preparing your dashboard…" />;

  const allocated = alloc?.state === 'ALLOCATED';
  const firstName = me?.name?.split(' ')[0] ?? 'there';
  const topRec = recs[0] ?? null;
  const primaryAction = actions[0] ?? 'You are ready — watch for the next allocation run';
  const actionRoute = (a: string) =>
    a.toLowerCase().includes('resume') ? '/candidate/profile'
      : a.toLowerCase().includes('preference') ? '/candidate/profile'
      : a.toLowerCase().includes('skill') ? '/candidate/analytics'
      : '/candidate/explore';

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-[24px] font-bold text-ink">Namaste, {firstName}</h1>
          <p className="mt-1 text-[13.5px] text-navy-500">Your internship readiness, best fits and allocation status — in one place.</p>
        </div>
        <span className="inline-flex items-center gap-1.5 rounded-full bg-saffron-50 px-3 py-1 text-[11px] font-semibold text-saffron-700 ring-1 ring-saffron-200">
          <FlaskConical className="h-3.5 w-3.5" /> Synthetic Demonstration Data
        </span>
      </div>

      {err && <Callout tone="warn">{err}</Callout>}

      {/* Allocation hero */}
      <div className={`card relative overflow-hidden p-6 ${allocated ? 'border-emerald-200 bg-gradient-to-br from-emerald-50/80 via-white to-white ring-1 ring-emerald-200' : 'bg-gradient-to-br from-navy-900 to-ink text-white'}`}>
        <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-saffron-400/10 blur-2xl" />
        {allocated && alloc ? (
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-start gap-4">
              <div className="rounded-2xl bg-emerald-500 p-3.5 text-white shadow-lg"><PartyPopper className="h-7 w-7" /></div>
              <div>
                <div className="text-[12px] font-bold uppercase tracking-[0.1em] text-emerald-600">Your allocation</div>
                <div className="font-display mt-1 text-[22px] font-bold leading-snug text-ink">
                  You have been allocated <span className="whitespace-nowrap">{alloc.opportunityTitle}</span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-[13px] text-navy-500">
                  <span className="chip bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200"><CheckCircle2 className="h-3.5 w-3.5" /> Allocated in the latest global allocation</span>
                  {alloc.sector && <span className="chip bg-navy-50 text-navy-700 ring-1 ring-navy-100">{alloc.sector}</span>}
                  {alloc.opportunityState && <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{alloc.opportunityState}</span>}
                </div>
                <div className="mt-2 text-[12.5px] text-navy-400">
                  {alloc.suitability != null && <>Overall fit: <span className="font-bold text-navy-700">{alloc.suitability.toFixed(1)} / 100</span> · </>}
                  Decided in Allocation Run #{alloc.runNumber} · {fmtTime(alloc.runAt)}
                  {alloc.preferenceRank ? ` · your rank #${alloc.preferenceRank} choice` : ''}
                </div>
                {recs[0] && recs[0].opportunity.title !== alloc.opportunityTitle && (
                  <div className="mt-1.5 text-[11.5px] text-navy-400">
                    Your top individual fit is actually{' '}
                    <button className="font-semibold text-saffron-600 underline-offset-2 hover:underline" onClick={() => nav('/candidate/analytics')}>
                      {recs[0].opportunity.title} ({recs[0].overallScore.toFixed(0)}/100)
                    </button>{' '}
                    — you were still routed here because the global allocation balances every candidate and every seat at once, not just your personal best fit.
                  </div>
                )}
              </div>
            </div>
            <button className="btn-dark" onClick={() => nav('/candidate/explore')}>
              <Briefcase className="h-4 w-4" /> View opportunity
            </button>
          </div>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              <div className="rounded-2xl bg-white/10 p-3.5 ring-1 ring-white/15"><Hourglass className="h-7 w-7 text-saffron-400" /></div>
              <div>
                <div className="text-[12px] font-bold uppercase tracking-[0.1em] text-saffron-400">Your internship readiness</div>
                <div className="font-display mt-1 text-[20px] font-bold">{alloc?.message ?? 'Awaiting the next allocation run.'}</div>
                <div className="mt-1 text-[13px] text-navy-200/80">Your profile is being considered in every global allocation run.</div>
              </div>
            </div>
            <button className="btn-primary" onClick={() => nav('/candidate/analytics')}>
              <Target className="h-4 w-4" /> See my fit analytics
            </button>
          </div>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Readiness + single next action */}
        <div className="card p-5">
          <SectionHeader title="Profile readiness" sub="How complete your profile is" />
          <div className="flex items-center gap-5">
            <ScoreRing value={ready?.overall ?? 0} size={84} label="ready" />
            <div className="flex-1 space-y-1.5">
              {(ready?.items ?? []).map(it => (
                <div key={it.key} className="flex items-center gap-2" title={it.detail}>
                  {it.status === 'COMPLETED'
                    ? <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" />
                    : <Clock className="h-4 w-4 shrink-0 text-amber-500" />}
                  <span className={`flex-1 truncate text-[12.5px] ${it.status === 'COMPLETED' ? 'text-navy-700' : 'text-navy-500'}`}>{it.label}</span>
                  <span className="text-[11.5px] font-semibold text-navy-400">{it.earned}/{it.maxScore}</span>
                </div>
              ))}
            </div>
          </div>
          <button className="btn-primary btn-sm mt-4 w-full" onClick={() => nav(actionRoute(primaryAction))}>
            <span className="max-w-full truncate">{primaryAction}</span> <ArrowRight className="h-3.5 w-3.5 shrink-0" />
          </button>
        </div>

        {/* Recommended for you (top 3) */}
        <div className="card p-5 lg:col-span-2">
          <SectionHeader title="Recommended for you"
            sub="Best current-fit internships — recommendation, not a final allocation"
            right={<button className="btn-ghost btn-sm" onClick={() => nav('/candidate/analytics')}>Full fit analytics <ArrowRight className="h-3.5 w-3.5" /></button>} />
          {recs.length === 0 ? (
            <div className="rounded-xl border border-dashed border-navy-200 p-6 text-center">
              <Target className="mx-auto h-8 w-8 text-navy-300" />
              <div className="mt-2 text-[13.5px] font-semibold text-navy-600">No recommendations yet</div>
              <div className="mx-auto mt-1 max-w-sm text-[12.5px] text-navy-400">
                Add skills, interests and a preference, and your best-fit internships will appear here — scored by the same engine that runs the global allocation.
              </div>
              <button className="btn-ghost btn-sm mt-3" onClick={() => nav('/candidate/profile')}>Complete your profile</button>
            </div>
          ) : (
            <div className="space-y-2">
              {recs.slice(0, 3).map(r => (
                <button key={r.opportunity.id} onClick={() => nav('/candidate/analytics')}
                  className="flex w-full items-center justify-between gap-3 rounded-xl border border-navy-100 px-4 py-3 text-left transition hover:border-saffron-200 hover:bg-saffron-50/30">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="font-display flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-navy-900 text-[12px] font-bold text-white">#{r.rank}</span>
                    <div className="min-w-0">
                      <div className="truncate text-[13.5px] font-bold text-ink">{r.opportunity.title}</div>
                      <div className="truncate text-[11.5px] text-navy-400">
                        {r.opportunity.sector} · {r.opportunity.state}
                        {r.topGap ? <> · main gap: <span className="text-amber-600">{r.topGap}</span></> : <> · all listed skills covered</>}
                      </div>
                    </div>
                  </div>
                  <span className={`shrink-0 font-display text-[16px] font-bold ${r.overallScore >= 70 ? 'text-emerald-600' : r.overallScore >= 50 ? 'text-saffron-600' : 'text-red-500'}`}>
                    {r.overallScore.toFixed(0)}<span className="text-[10px] font-semibold text-navy-400"> /100</span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Top improvement (skill gap highlight) */}
      {topRec && (
        <div className="card flex flex-wrap items-center justify-between gap-3 p-5">
          <div className="flex items-start gap-3">
            <div className="rounded-xl bg-saffron-50 p-2.5 text-saffron-600 ring-1 ring-saffron-100"><Lightbulb className="h-5 w-5" /></div>
            <div>
              <div className="text-[13.5px] font-bold text-ink">
                {topRec.topGap
                  ? <>Top improvement: add <span className="text-saffron-700">{topRec.topGap}</span> to strengthen your fit for {topRec.opportunity.title}</>
                  : <>You cover every listed skill for {topRec.opportunity.title} — your strongest current fit</>}
              </div>
              <div className="mt-0.5 text-[12.5px] text-navy-400">
                {topRec.topGap
                  ? 'Skills you verify on your profile are the input the engine scores — see the skill-gap assistant for grounded guidance.'
                  : 'Work the other levers (preferences, location) in Fit Analytics to raise the score further.'}
              </div>
            </div>
          </div>
          <button className="btn-ghost btn-sm" onClick={() => nav('/candidate/analytics')}>Open skill gap <ArrowRight className="h-3.5 w-3.5" /></button>
        </div>
      )}

      {/* History */}
      {history.length > 0 && (
        <div className="card p-5">
          <SectionHeader title="Your allocation history" />
          <div className="space-y-2">
            {history.map(h => (
              <div key={h.runId} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-navy-100 px-4 py-3">
                <div className="flex items-center gap-3">
                  <Badge tone="green" dot>Allocated</Badge>
                  <span className="text-[13px] font-medium text-navy-700">
                    Allocation Run #{h.runNumber} · {h.opportunityTitle ?? '—'}
                    {h.suitability != null && <span className="ml-2 text-[12px] text-navy-400">fit {h.suitability.toFixed(1)}</span>}
                  </span>
                </div>
                <span className="text-[12px] text-navy-400">{fmtTime(h.completedAt)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
