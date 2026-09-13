import React, { useEffect, useMemo, useState } from 'react';
import {
  Target, Sparkles, Lightbulb, GraduationCap, MapPin, CheckCircle2, XCircle, ArrowUp,
  Sparkle, Wand2, Info, ListChecks,
} from 'lucide-react';
import { apiGet, apiPost, errMsg } from '../../api/client';
import {
  FitAnalysis, Recommendation, SkillGap, GuidanceRes, Opp,
} from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, EmptyState, KV, fmtNum } from '../../components/ui';

const FACTOR_ICONS: Record<string, React.ReactNode> = {
  skills: <Wand2 className="h-4 w-4" />,
  qualification: <GraduationCap className="h-4 w-4" />,
  interest: <Sparkles className="h-4 w-4" />,
  location: <MapPin className="h-4 w-4" />,
  preference: <ListChecks className="h-4 w-4" />,
  learning: <ArrowUp className="h-4 w-4" />,
  experience: <Target className="h-4 w-4" />,
};

const QUESTIONS: { id: string; label: string }[] = [
  { id: 'weak_area', label: 'What part of my profile is weak for this internship?' },
  { id: 'learn_first', label: 'What should I learn first?' },
  { id: 'why_skill', label: 'Why does a listed skill matter here?' },
  { id: 'improve_impact', label: 'How would improving a skill affect my fit?' },
];

function fitTone(v: number): string {
  return v >= 80 ? 'text-emerald-600' : v >= 50 ? 'text-saffron-600' : 'text-red-500';
}

export default function CandidateAnalytics() {
  const [opps, setOpps] = useState<Opp[]>([]);
  const [selId, setSelId] = useState<number | null>(null);
  const [fit, setFit] = useState<FitAnalysis | null>(null);
  const [gap, setGap] = useState<SkillGap | null>(null);
  const [recs, setRecs] = useState<Recommendation[]>([]);
  const [recNote, setRecNote] = useState('');
  const [loadingFit, setLoadingFit] = useState(false);
  const [err, setErr] = useState('');

  const [activeQ, setActiveQ] = useState<string | null>(null);
  const [skillForQ, setSkillForQ] = useState('');
  const [answer, setAnswer] = useState<GuidanceRes | null>(null);
  const [asking, setAsking] = useState(false);

  const needsSkill = (qid: string) => qid === 'why_skill' || qid === 'improve_impact';

  useEffect(() => {
    (async () => {
      try {
        const d = await apiGet<any>('/opportunities', { page: 0, size: 100, availableOnly: true });
        const rows: Opp[] = d.content ?? [];
        setOpps(rows);
        if (rows.length > 0) {
          const firstEligible = rows.find(o => o.eligible) ?? rows[0];
          setSelId(firstEligible.id);
        }
        const r = await apiGet<any>('/candidates/me/recommendations');
        setRecs(r.items ?? []);
        setRecNote(r.note ?? '');
      } catch (e) { setErr(errMsg(e)); }
    })();
  }, []);

  useEffect(() => {
    if (selId == null) { setFit(null); setGap(null); return; }
    setLoadingFit(true); setErr(''); setAnswer(null); setActiveQ(null); setSkillForQ('');
    (async () => {
      try {
        const [f, g] = await Promise.all([
          apiGet<FitAnalysis>(`/candidates/me/analytics/opportunity/${selId}`),
          apiGet<SkillGap>(`/candidates/me/skill-gap/${selId}`),
        ]);
        setFit(f); setGap(g);
      } catch (e) { setErr(errMsg(e)); setFit(null); setGap(null); }
      setLoadingFit(false);
    })();
  }, [selId]);

  const pickQuestion = (qid: string) => {
    if (needsSkill(qid)) {
      setActiveQ(qid);
      if (skillForQ) void ask(qid, skillForQ);
    } else {
      setActiveQ(qid);
      void ask(qid);
    }
  };

  const onSkillChange = (skill: string) => {
    setSkillForQ(skill);
    setAnswer(null);
    if (skill && activeQ && needsSkill(activeQ)) void ask(activeQ, skill);
  };

  const ask = async (question: string, skill?: string) => {
    if (selId == null) return;
    setAsking(true); setErr('');
    try {
      const a = await apiPost<GuidanceRes>(`/candidates/me/skill-gap/${selId}/guidance`, { question, skill });
      setAnswer(a);
    } catch (e) { setErr(errMsg(e)); }
    setAsking(false);
  };

  const holdingBack = useMemo(
    () => (fit ? [...fit.factors].sort((a, b) => b.lostPoints - a.lostPoints).filter(f => f.lostPoints >= 0.5).slice(0, 3) : []),
    [fit]);

  const eligibleOpps = useMemo(() => opps.filter(o => o.eligible), [opps]);
  const notEligibleOpps = useMemo(() => opps.filter(o => !o.eligible), [opps]);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">My Fit Analytics</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">
          Where you stand for each internship — the exact score, what earns it, what you are leaving on the table, and what to improve.
        </p>
      </div>

      {err && <Callout tone="warn">{err}</Callout>}

      {/* Opportunity picker */}
      <div className="card p-4">
        <label className="label">Choose an internship to analyse</label>
        <select className="input" value={selId ?? ''} onChange={e => setSelId(Number(e.target.value))}>
          {eligibleOpps.length > 0 && <optgroup label="Eligible for you">
            {eligibleOpps.map(o => <option key={o.id} value={o.id}>{o.title} — {o.sector} · {o.state}</option>)}
          </optgroup>}
          {notEligibleOpps.length > 0 && <optgroup label="Not eligible yet (see blockers)">
            {notEligibleOpps.map(o => <option key={o.id} value={o.id}>{o.title} — {o.sector} · {o.state}</option>)}
          </optgroup>}
        </select>
        <div className="mt-2 text-[11.5px] text-navy-400">
          Fit scores use the same eligibility and scoring engine as the global allocation — weights: {fit?.weightsSource ?? 'PRAGATI default configurable weights'}.
        </div>
      </div>

      {loadingFit && <div className="card"><Spinner label="Computing your fit…" /></div>}

      {!loadingFit && fit && (
        <>
          {fit.eligible ? (
            <div className="grid gap-4 xl:grid-cols-3">
              {/* Score hero */}
              <div className="card p-5 xl:col-span-1">
                <SectionHeader title="Your fit for this internship" sub={fit.opportunity.title} />
                <div className="flex items-center gap-5">
                  <div className="relative flex h-28 w-28 shrink-0 items-center justify-center rounded-full ring-8 ring-saffron-50">
                    <svg className="absolute inset-0 -rotate-90" viewBox="0 0 100 100">
                      <circle cx="50" cy="50" r="45" fill="none" stroke="#e8edf5" strokeWidth="9" />
                      <circle cx="50" cy="50" r="45" fill="none"
                        stroke={fit.overallScore >= 80 ? '#1b8a5a' : fit.overallScore >= 50 ? '#ee8420' : '#dc2626'}
                        strokeWidth="9" strokeLinecap="round"
                        strokeDasharray={2 * Math.PI * 45}
                        strokeDashoffset={2 * Math.PI * 45 * (1 - fit.overallScore / 100)} />
                    </svg>
                    <div className="text-center">
                      <div className="font-display text-[26px] font-bold leading-none text-ink">{fit.overallScore.toFixed(1)}</div>
                      <div className="text-[10px] font-semibold uppercase tracking-wide text-navy-400">of {fit.possibleScore}</div>
                    </div>
                  </div>
                  <div>
                    <Badge tone={fit.overallScore >= 70 ? 'green' : fit.overallScore >= 50 ? 'amber' : 'red'} dot>{fit.recommendationLabel}</Badge>
                    <div className="mt-2 text-[13px] leading-relaxed text-navy-500">
                      You are leaving <span className="font-bold text-ink">{fit.lostPoints.toFixed(1)} points</span> on the table.
                    </div>
                    {fit.preferenceRank && <div className="mt-1 text-[12px] text-navy-400">Your #{fit.preferenceRank} listed preference</div>}
                  </div>
                </div>
              </div>

              {/* What is holding back */}
              <div className="card p-5 xl:col-span-2">
                <SectionHeader title="What is holding your score back?" sub="Top factors by points you could still earn" />
                {holdingBack.length === 0 ? (
                  <div className="flex items-center gap-3 rounded-xl bg-emerald-50/70 p-4 text-[13px] text-emerald-800 ring-1 ring-emerald-100">
                    <CheckCircle2 className="h-4 w-4 shrink-0" /> No meaningful gaps — this profile is well balanced for the internship.
                  </div>
                ) : (
                  <div className="space-y-2.5">
                    {holdingBack.map((f, i) => (
                      <div key={f.key} className="rounded-xl border border-navy-100 px-4 py-3">
                        <div className="flex items-center justify-between gap-3">
                          <div className="flex items-center gap-2.5 text-[13.5px] font-semibold text-ink">
                            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-saffron-50 text-[11px] font-bold text-saffron-700">{i + 1}</span>
                            {f.label}
                          </div>
                          <span className="text-[12.5px] font-bold text-saffron-600">{f.lostPoints.toFixed(1)} points</span>
                        </div>
                        <div className="mt-1 pl-8 text-[12.5px] leading-relaxed text-navy-500">{f.explanation}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="card p-5">
              <SectionHeader title="Not eligible for this internship yet" sub={fit.opportunity.title} />
              <Callout tone="warn" title="Why not yet">
                <ul className="mt-1 list-disc space-y-1 pl-4">
                  {fit.eligibilityReasons.map(r => <li key={r}>{r}</li>)}
                </ul>
              </Callout>
              <div className="mt-3 flex items-start gap-2 rounded-xl bg-navy-50/70 p-3.5 text-[12.5px] leading-relaxed text-navy-600 ring-1 ring-navy-100">
                <Info className="mt-0.5 h-4 w-4 shrink-0" />
                No fit score is shown for an ineligible internship — a score would not be actionable. Fix the blockers above (usually a required skill or qualification) and the score appears immediately.
              </div>
            </div>
          )}

          {fit.eligible && (
            <>
              {/* Factor breakdown */}
              <div className="card p-5">
                <SectionHeader title="Score breakdown" sub="How each factor earns its points — fit × weight, with the points still available" />
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[640px]">
                    <thead>
                      <tr className="border-b border-navy-100">
                        <th className="th">Factor</th><th className="th">Fit</th><th className="th">Weight</th>
                        <th className="th">Points earned</th><th className="th">Points available</th>
                      </tr>
                    </thead>
                    <tbody>
                      {fit.factors.map(f => (
                        <tr key={f.key} className="border-b border-navy-50 last:border-0">
                          <td className="td">
                            <div className="flex items-center gap-2 font-medium text-ink">
                              <span className="text-navy-400">{FACTOR_ICONS[f.key] ?? <Sparkle className="h-4 w-4" />}</span>{f.label}
                            </div>
                          </td>
                          <td className={`td font-bold ${fitTone(f.fit)}`}>{f.fit.toFixed(0)}%</td>
                          <td className="td text-navy-500">{f.weight.toFixed(0)}%</td>
                          <td className="td font-semibold text-ink">{f.contribution.toFixed(1)}</td>
                          <td className="td text-navy-500">{f.lostPoints.toFixed(1)}</td>
                        </tr>
                      ))}
                      <tr>
                        <td className="td font-bold text-ink">Overall fit</td>
                        <td className="td" colSpan={2} />
                        <td className="td font-display text-[15px] font-bold text-ink">{fit.overallScore.toFixed(1)} / 100</td>
                        <td className="td font-bold text-saffron-600">{fit.lostPoints.toFixed(1)}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Why this suits you + skill gap */}
              <div className="grid gap-4 xl:grid-cols-2">
                <div className="card p-5">
                  <SectionHeader title="Why this suits you" sub="Your strongest matching signals" />
                  {fit.strongestMatches.length > 0 ? (
                    <div className="mb-3 flex flex-wrap gap-1.5">
                      {fit.strongestMatches.map(s => <span key={s} className="chip bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200"><CheckCircle2 className="h-3.5 w-3.5" />{s}</span>)}
                    </div>
                  ) : (
                    <div className="mb-3 text-[12.5px] text-navy-400">No listed skills are covered yet — see the skill readiness card.</div>
                  )}
                  <div className="space-y-2">
                    {fit.factors.filter(f => ['interest', 'location', 'preference', 'qualification'].includes(f.key)).map(f => (
                      <div key={f.key} className="flex items-start gap-2.5 rounded-lg bg-navy-50/60 px-3.5 py-2.5 text-[12.5px] leading-relaxed text-navy-600 ring-1 ring-navy-100">
                        <span className="mt-0.5 shrink-0 text-navy-400">{FACTOR_ICONS[f.key]}</span>{f.explanation}
                      </div>
                    ))}
                  </div>
                </div>

                {gap && (
                  <div className="card p-5">
                    <SectionHeader title="Skill readiness" sub={gap.eligible ? 'Required and preferred skills vs your verified skills' : 'Required skills you are still missing'} />
                    <div className="mb-3 flex items-center justify-between rounded-xl bg-navy-50/70 px-4 py-3 ring-1 ring-navy-100">
                      <span className="text-[12.5px] font-semibold text-navy-600">Skill coverage</span>
                      <span className={`font-display text-[18px] font-bold ${fitTone(gap.readinessPercent)}`}>{gap.readinessPercent.toFixed(0)}%</span>
                    </div>
                    {gap.coveredSkills.length > 0 && (
                      <div className="mb-2 flex flex-wrap gap-1.5">
                        {gap.coveredSkills.map(s => <span key={s} className="chip bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">{s} · covered</span>)}
                      </div>
                    )}
                    {gap.mandatoryGaps.length > 0 && (
                      <div className="mb-2 flex flex-wrap gap-1.5">
                        {gap.mandatoryGaps.map(s => <span key={s} className="chip bg-red-50 text-red-600 ring-1 ring-red-200">{s} · required — missing</span>)}
                      </div>
                    )}
                    {gap.preferredGaps.length > 0 && (
                      <div className="mb-2 flex flex-wrap gap-1.5">
                        {gap.preferredGaps.map(s => <span key={s} className="chip bg-amber-50 text-amber-700 ring-1 ring-amber-200">{s} · nice to have</span>)}
                      </div>
                    )}
                    <div className="mt-3">
                      <div className="mb-1.5 text-[12px] font-bold uppercase tracking-wide text-navy-500">
                        What would strengthen your fit?
                      </div>
                      <div className="rounded-xl bg-saffron-50/60 p-3.5 text-[12.5px] leading-relaxed text-navy-700 ring-1 ring-saffron-100">
                        <Lightbulb className="mr-1.5 inline h-3.5 w-3.5 text-saffron-500" />{gap.assistantSummary}
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* AI skill-gap assistant */}
              <div className="card p-5">
                <SectionHeader title="Skill-gap assistant"
                  sub="Grounded guidance — built only from the opportunity's stated requirements and your verified profile. It never invents facts." />
                <div className="flex flex-wrap gap-2">
                  {QUESTIONS.map(qs => (
                    <button key={qs.id} disabled={asking}
                      onClick={() => pickQuestion(qs.id)}
                      className={`btn-ghost btn-sm ${asking ? 'opacity-50' : ''}`}>
                      <Sparkles className="h-3.5 w-3.5" /> {qs.label}
                    </button>
                  ))}
                </div>
                {activeQ && needsSkill(activeQ) && (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <span className="text-[12px] font-semibold text-navy-500">About which skill?</span>
                    <select className="input w-auto max-w-[260px]" value={skillForQ} onChange={e => onSkillChange(e.target.value)}>
                      <option value="">Choose a skill…</option>
                      {[...(gap?.mandatoryGaps ?? []), ...(gap?.preferredGaps ?? [])].map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                )}
                {asking && <div className="mt-4"><Spinner label="Composing grounded guidance…" /></div>}
                {answer && !asking && (
                  <div className="mt-4 rounded-xl border border-navy-100 bg-white p-4">
                    <div className="mb-2 flex items-center gap-2 text-[11.5px] font-bold uppercase tracking-wide text-navy-400">
                      <Sparkles className="h-3.5 w-3.5 text-saffron-500" />
                      {QUESTIONS.find(x => x.id === activeQ)?.label}
                      {skillForQ && <span className="normal-case text-saffron-600">· {skillForQ}</span>}
                    </div>
                    <p className="text-[13.5px] leading-relaxed text-navy-800">{answer.answer}</p>
                    <div className="mt-3 text-[11px] text-navy-400">
                      Grounding: {answer.grounding}. Engine: {answer.engine === 'deterministic-guidance' ? 'deterministic guidance templates' : 'deterministic fallback (AI service offline)'} — no fabricated content.
                    </div>
                  </div>
                )}
              </div>
            </>
          )}
        </>
      )}

      {/* Recommended for you */}
      <div className="card p-5">
        <SectionHeader title="Recommended for you"
          sub="Your best current-fit internships, ranked by the same engine that scores everything else"
          right={<Badge tone="saffron">Recommendation — not a final allocation</Badge>} />
        <div className="mb-3 flex items-start gap-2 rounded-xl bg-sky-50/70 p-3 text-[12px] leading-relaxed text-sky-900 ring-1 ring-sky-100">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          {recNote || 'Recommendations show your individual fit. The final allocation considers the entire candidate pool and available capacity.'}
        </div>
        {recs.length === 0 ? (
          <EmptyState title="No recommendations yet" hint="Complete your profile — skills, interests and a preference — and matching internships will appear here." icon={<Target className="h-8 w-8" />} />
        ) : (
          <div className="space-y-2.5">
            {recs.map(r => (
              <div key={r.opportunity.id} className="rounded-xl border border-navy-100 p-4 transition hover:border-saffron-200 hover:bg-saffron-50/20">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-start gap-3">
                    <span className="font-display flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-navy-900 text-[13px] font-bold text-white">#{r.rank}</span>
                    <div>
                      <div className="text-[14px] font-bold text-ink">{r.opportunity.title}</div>
                      <div className="mt-0.5 text-[12px] text-navy-400">
                        {r.opportunity.sector} · {r.opportunity.city ? r.opportunity.city + ', ' : ''}{r.opportunity.state}
                        {r.preferenceRank ? ` · your #${r.preferenceRank} preference` : ''}
                      </div>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className={`font-display text-[18px] font-bold ${fitTone(r.overallScore)}`}>{r.overallScore.toFixed(0)} <span className="text-[11px] font-semibold text-navy-400">/ 100</span></div>
                    <div className="text-[11px] font-semibold uppercase tracking-wide text-navy-400">{fit?.recommendationLabel && r.overallScore === fit.overallScore ? fit.recommendationLabel : r.overallScore >= 70 ? 'Strong fit' : r.overallScore >= 50 ? 'Moderate fit' : 'Partial fit'}</div>
                  </div>
                </div>
                <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
                  {r.strongestMatches.map(s => <span key={s} className="chip bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">{s}</span>)}
                  {r.topGap && <span className="chip bg-amber-50 text-amber-700 ring-1 ring-amber-200">main gap: {r.topGap}</span>}
                </div>
                <div className="mt-2 text-[12.5px] text-navy-500">{r.reason}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
