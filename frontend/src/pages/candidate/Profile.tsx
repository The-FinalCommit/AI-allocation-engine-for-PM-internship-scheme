import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  User, GraduationCap, Wrench, Heart, ListOrdered, FileText, Plus, X, Save,
  Upload, SearchCheck, AlertTriangle, CheckCircle2, Sparkles, ShieldCheck,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { apiGet, apiPost, apiPut, errMsg } from '../../api/client';
import { ResumeSuggestionsView, Readiness } from '../../api/types';
import { SectionHeader, Callout, Badge, Spinner, fmtTime } from '../../components/ui';
import { ScoreRing } from '../../components/ScoreRing';

const SKILL_SUGGESTIONS = [
  'Python', 'Java', 'C++', 'SQL', 'Data Analysis', 'Machine Learning', 'Excel', 'Statistics',
  'Power BI', 'Tableau', 'Web Development', 'React', 'Node.js', 'JavaScript', 'HTML CSS', 'Git',
  'Cloud Computing', 'DevOps', 'Cybersecurity', 'NLP', 'Deep Learning', 'Communication',
  'Presentation', 'Research', 'Content Writing', 'Graphic Design', 'UI/UX Design', 'Digital Marketing',
  'Social Media', 'SEO', 'Finance', 'Accounting', 'Budgeting',
  'Public Administration', 'Policy Analysis', 'HR Management', 'Operations',
  'Supply Chain', 'Legal Research', 'Environmental Science', 'Healthcare Data',
  'Project Management',
];
const SECTOR_OPTIONS = [
  ['SOFTWARE_IT', 'Software & IT'], ['DATA_ANALYTICS', 'Data & Analytics'], ['FINANCE_BANKING', 'Finance & Banking'],
  ['DESIGN_MEDIA', 'Design & Media'], ['MARKETING_COMMUNICATION', 'Marketing & Communication'],
  ['PUBLIC_ADMIN_POLICY', 'Public Administration & Policy'], ['HEALTHCARE_BIOTECH', 'Healthcare & Biotech'],
  ['MANUFACTURING_OPERATIONS', 'Manufacturing & Operations'], ['RESEARCH_DEVELOPMENT', 'Research & Development'],
  ['ENVIRONMENTAL_ENERGY', 'Environmental & Energy'],
];
// Labels match backend Labels.java — the same wording candidates see in lists and reports.
const QUALS = [['HIGHER_SECONDARY', 'Higher Secondary'], ['BACHELORS', "Bachelor's Degree"], ['POST_GRADUATE', 'Postgraduate'], ['DOCTORAL', 'Doctoral']];
const STATUSES = [['STUDENT', 'Student'], ['GRADUATE', 'Recent graduate'], ['WORKING', 'Working professional']];
const LOC_TYPES = [['URBAN', 'Urban'], ['SEMI_URBAN', 'Semi-urban'], ['RURAL', 'Rural']];
const STATES = ['Andhra Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Delhi', 'Gujarat', 'Haryana', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Odisha', 'Punjab', 'Rajasthan', 'Tamil Nadu', 'Telangana', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal'];

const FIELD_LABELS: Record<string, string> = {
  fullName: 'Full name', phone: 'Phone', qualification: 'Highest qualification',
  candidateStatus: 'Current status', state: 'State', district: 'District',
  experienceYears: 'Experience (years)', bio: 'About you',
};

export default function CandidateProfile() {
  const nav = useNavigate();
  const [p, setP] = useState<any>(null);
  const [skills, setSkills] = useState<string[]>([]);
  const [interests, setInterests] = useState<string[]>([]);
  const [prefs, setPrefs] = useState<{ opportunityId: number; rank: number; title: string; sector: string }[]>([]);
  const [resume, setResume] = useState<any>(null);
  const [sugg, setSugg] = useState<ResumeSuggestionsView | null>(null);
  const [readiness, setReadiness] = useState<Readiness | null>(null);
  const [skillDraft, setSkillDraft] = useState('');
  const [oppSearch, setOppSearch] = useState('');
  const [oppResults, setOppResults] = useState<any[]>([]);
  const [oppNoMatch, setOppNoMatch] = useState<'' | 'none' | 'dups'>('');
  const [prefMsg, setPrefMsg] = useState('');
  const searchSeq = useRef(0);
  const prefMsgTimer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(prefMsgTimer.current), []);
  const [saving, setSaving] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');

  // Resume review state — keyed by resume id so a replacement resume can
  // never inherit (or submit) the previous file's accept/reject choices.
  const [reviewResumeId, setReviewResumeId] = useState<number | null>(null);
  const [acceptField, setAcceptField] = useState<Record<string, boolean>>({});
  const [acceptSkill, setAcceptSkill] = useState<Record<string, boolean>>({});
  const [acceptInterest, setAcceptInterest] = useState<Record<string, boolean>>({});
  const [applying, setApplying] = useState(false);

  const load = async () => {
    try {
      const [prof, pr, rs, o, rd] = await Promise.all([
        apiGet<any>('/candidates/me/profile'),
        apiGet<any>('/candidates/me/preferences'),
        apiGet<any>('/candidates/me/resume').catch(() => null),
        // All statuses (incl. paused) so an existing preference always resolves its title.
        apiGet<any>('/opportunities', { page: 0, size: 100, availableOnly: false }).catch(() => null),
        apiGet<Readiness>('/candidates/me/readiness').catch(() => null),
      ]);
      setP(prof);
      setSkills((prof.skills ?? []).map((s: any) => s.canonical));
      setInterests((prof.interests ?? []).map((s: any) => String(s)));
      const titles: Record<number, { title: string; sector: string }> = {};
      (o?.content ?? []).forEach((c: any) => { titles[c.id] = { title: c.title, sector: c.sector }; });
      setPrefs((pr ?? []).map((x: any) => ({
        opportunityId: x.opportunityId, rank: x.rank,
        title: titles[x.opportunityId]?.title ?? 'Opportunity',
        sector: titles[x.opportunityId]?.sector ?? '',
      })).sort((a: any, b: any) => a.rank - b.rank));
      setResume(rs ?? null);
      setReadiness(rd ?? null);
      if (rs && rs.status === 'PENDING_REVIEW') {
        apiGet<ResumeSuggestionsView>('/candidates/me/resume/suggestions')
          .then(s => setSugg(s))
          .catch(() => setSugg(null));
      } else {
        setSugg(null);
      }
    } catch (e) { setErr(errMsg(e)); }
  };
  useEffect(() => { void load(); }, []);

  // Reset review state whenever the reviewed resume changes (replacement safety).
  useEffect(() => {
    if (sugg && sugg.resumeId != null && sugg.resumeId !== reviewResumeId) {
      const f: Record<string, boolean> = {};
      for (const field of sugg.fields) {
        const cur = currentValue(p, field.field);
        f[field.field] = cur === ''; // blank → safe to auto-accept; existing data → never
      }
      const s: Record<string, boolean> = {};
      for (const sk of sugg.skills) s[sk] = !skills.includes(sk);
      const i: Record<string, boolean> = {};
      for (const it of sugg.interests) i[it] = true;
      setAcceptField(f); setAcceptSkill(s); setAcceptInterest(i);
      setReviewResumeId(sugg.resumeId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sugg?.resumeId]);

  const currentValue = (prof: any, field: string): string => {
    if (!prof) return '';
    switch (field) {
      case 'fullName': return prof.fullName ?? '';
      case 'phone': return prof.phone ?? '';
      case 'qualification': return prof.qualification ?? '';
      case 'candidateStatus': return prof.candidateStatus ?? '';
      case 'state': return prof.state ?? '';
      case 'district': return prof.district ?? '';
      case 'experienceYears': return (prof.experienceYears ?? 0) ? String(prof.experienceYears) : '';
      case 'bio': return prof.bio ?? '';
      default: return '';
    }
  };

  const saveBasic = async () => {
    setSaving('basic'); setMsg(''); setErr('');
    try {
      await apiPut('/candidates/me/profile', {
        fullName: p.fullName, phone: p.phone, dob: p.dob, state: p.state, district: p.district,
        locationType: p.locationType, qualification: p.qualification, candidateStatus: p.candidateStatus,
        bio: p.bio, experienceYears: p.experienceYears === '' ? null : Number(p.experienceYears),
      });
      setMsg('Profile details saved.'); await load();
    } catch (e) { setErr(errMsg(e)); }
    setSaving('');
  };
  const saveSkills = async () => {
    setSaving('skills'); setMsg(''); setErr('');
    try { await apiPut('/candidates/me/skills', skills); setMsg('Skills updated.'); await load(); }
    catch (e) { setErr(errMsg(e)); }
    setSaving('');
  };
  const saveInterests = async () => {
    setSaving('interests'); setMsg(''); setErr('');
    try { await apiPut('/candidates/me/interests', interests); setMsg('Interests saved.'); await load(); }
    catch (e) { setErr(errMsg(e)); }
    setSaving('');
  };
  const savePrefs = async () => {
    setSaving('prefs'); setMsg(''); setErr('');
    try { await apiPut('/candidates/me/preferences', prefs.map(x => ({ opportunityId: x.opportunityId, rank: x.rank }))); setMsg('Preferences saved.'); await load(); }
    catch (e) { setErr(errMsg(e)); }
    setSaving('');
  };
  const addSkill = () => {
    const v = skillDraft.trim();
    if (!v) return;
    if (!skills.includes(v)) setSkills(s => [...s, v]);
    setSkillDraft('');
  };
  const MAX_PREFS = 10; // backend rejects ranks above 10

  const flashPrefMsg = (m: string) => {
    setPrefMsg(m);
    window.clearTimeout(prefMsgTimer.current);
    prefMsgTimer.current = window.setTimeout(() => setPrefMsg(''), 3000);
  };

  const searchOpps = async (term: string) => {
    setOppSearch(term);
    if (!term.trim()) { setOppResults([]); setOppNoMatch(''); return; }
    const seq = ++searchSeq.current;
    try {
      const d = await apiGet<any>('/opportunities', { page: 0, size: 6, q: term, availableOnly: true });
      if (seq !== searchSeq.current) return; // a newer search superseded this one
      const all = d.content ?? [];
      const fresh = all.filter((o: any) => !prefs.some(x => x.opportunityId === o.id));
      setOppResults(fresh);
      setOppNoMatch(all.length === 0 ? 'none' : fresh.length === 0 ? 'dups' : '');
    } catch {
      if (seq === searchSeq.current) { setOppResults([]); setOppNoMatch('none'); }
    }
  };
  const addPref = (id: number) => {
    if (prefs.some(x => x.opportunityId === id)) { flashPrefMsg('That opportunity is already in your list.'); return; }
    if (prefs.length >= MAX_PREFS) { flashPrefMsg(`You can rank at most ${MAX_PREFS} opportunities.`); return; }
    const o = oppResults.find(x => x.id === id);
    const rank = prefs.length + 1;
    setPrefs(ps => [...ps, { opportunityId: id, rank: ps.length + 1, title: o?.title ?? 'Opportunity', sector: o?.sector ?? '' }]);
    setOppResults([]); setOppSearch(''); setOppNoMatch('');
    flashPrefMsg(`Added “${o?.title ?? 'Opportunity'}” as preference #${rank}.`);
  };
  const removePref = (id: number) => {
    const x = prefs.find(y => y.opportunityId === id);
    setPrefs(ps => ps.filter(y => y.opportunityId !== id).map((y, k) => ({ ...y, rank: k + 1 })));
    flashPrefMsg(`Removed “${x?.title ?? 'Opportunity'}”. Ranks updated.`);
  };
  const movePref = (idx: number, dir: -1 | 1) => {
    const j = idx + dir;
    if (j < 0 || j >= prefs.length) return;
    const next = [...prefs];
    [next[idx], next[j]] = [next[j], next[idx]];
    setPrefs(next.map((x, k) => ({ ...x, rank: k + 1 })));
  };

  const onResume = async (f: File | null) => {
    if (!f) return;
    setSaving('resume'); setErr(''); setMsg('');
    try {
      const fd = new FormData();
      fd.append('file', f);
      const r = await apiPost<any>('/candidates/me/resume', fd, { headers: { 'Content-Type': 'multipart/form-data' } } as any);
      setResume(r);
      if (r.status === 'PENDING_REVIEW') {
        setMsg(`Resume received. ${r.pendingSkills?.length ?? 0} skills and profile suggestions found — please review them below.`);
      } else if (r.status === 'REVIEWED') {
        setMsg('Resume received — nothing needed your review.');
      }
      await load();
    } catch (e) { setErr(errMsg(e)); }
    setSaving('');
  };

  const applySuggestions = async (ignoreAll: boolean) => {
    if (!sugg) return;
    setApplying(true); setErr(''); setMsg('');
    try {
      const profile: Record<string, string> = {};
      if (!ignoreAll) {
        for (const f of sugg.fields) {
          if (acceptField[f.field]) profile[f.field] = f.value;
        }
      }
      const skillsSel = ignoreAll ? [] : sugg.skills.filter(s => acceptSkill[s] !== false);
      const interestsSel = ignoreAll ? [] : sugg.interests.filter(s => acceptInterest[s] !== false);
      const r = await apiPost<any>('/candidates/me/resume/apply-suggestions',
        { resumeId: sugg.resumeId, profile, skills: skillsSel, interests: interestsSel });
      setResume(r);
      setSugg(null);
      setReviewResumeId(null);
      setMsg(ignoreAll
        ? 'Suggestions dismissed — your resume is marked as reviewed.'
        : 'Confirmed suggestions saved to your profile.');
      await load();
    } catch (e) { setErr(errMsg(e)); }
    setApplying(false);
  };

  const skillOptions = useMemo(() => SKILL_SUGGESTIONS.filter(s => !skills.includes(s)), [skills]);

  if (!p) return <div className="space-y-4">{err && <Callout tone="danger">{err}</Callout>}{!err && <Spinner label="Loading your profile…" />}</div>;

  const upd = (k: string, v: any) => setP((x: any) => ({ ...x, [k]: v }));
  const pendingReview = resume?.status === 'PENDING_REVIEW' && sugg;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-[24px] font-bold text-ink">My Profile</h1>
          <p className="mt-1 text-[13.5px] text-navy-500">A complete, accurate profile is what the engine uses to judge eligibility and suitability.</p>
        </div>
        {readiness && (
          <div className="flex items-center gap-3 rounded-xl bg-navy-50/70 px-4 py-2.5 ring-1 ring-navy-100">
            <ScoreRing value={readiness.overall} size={44} stroke={5} />
            <div>
              <div className="text-[12px] font-bold text-ink">Profile strength</div>
              <div className="text-[11.5px] text-navy-500">{readiness.overall >= 80 ? 'Strong — ready for allocation' : readiness.overall >= 50 ? 'Good — a couple of gaps' : 'Building — see the items below'}</div>
            </div>
          </div>
        )}
      </div>

      {err && <Callout tone="warn">{err}</Callout>}
      {msg && <Callout tone="success">{msg}</Callout>}

      {/* 1 · Personal information */}
      <div className="card p-5">
        <SectionHeader title="Personal information" right={<span className="chip bg-navy-50 text-navy-500 ring-1 ring-navy-100"><User className="h-3.5 w-3.5" /> identity</span>} />
        <div className="grid gap-4 md:grid-cols-3">
          <div><label className="label">Full name</label><input className="input" value={p.fullName ?? ''} onChange={e => upd('fullName', e.target.value)} /></div>
          <div><label className="label">Phone</label><input className="input" value={p.phone ?? ''} onChange={e => upd('phone', e.target.value)} placeholder="+91…" /></div>
          <div><label className="label">Date of birth</label><input className="input" type="date" value={p.dob ?? ''} onChange={e => upd('dob', e.target.value)} /></div>
          <div><label className="label">State</label>
            <select className="input" value={p.state ?? ''} onChange={e => upd('state', e.target.value)}>
              <option value="">Select…</option>{STATES.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div><label className="label">District</label><input className="input" value={p.district ?? ''} onChange={e => upd('district', e.target.value)} /></div>
          <div><label className="label">Location type</label>
            <select className="input" value={p.locationType ?? ''} onChange={e => upd('locationType', e.target.value)}>
              <option value="">Select…</option>{LOC_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </div>
        </div>
        <div className="mt-4 flex justify-end">
          <button className="btn-primary" onClick={saveBasic} disabled={saving === 'basic'}><Save className="h-4 w-4" />{saving === 'basic' ? 'Saving…' : 'Save details'}</button>
        </div>
      </div>

      {/* 2 · Education & experience */}
      <div className="card p-5">
        <SectionHeader title="Education & experience" right={<span className="chip bg-navy-50 text-navy-500 ring-1 ring-navy-100"><GraduationCap className="h-3.5 w-3.5" /> education</span>} />
        <div className="grid gap-4 md:grid-cols-3">
          <div><label className="label">Highest qualification</label>
            <select className="input" value={p.qualification ?? ''} onChange={e => upd('qualification', e.target.value)}>
              <option value="">Select…</option>{QUALS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </div>
          <div><label className="label">Current status</label>
            <select className="input" value={p.candidateStatus ?? ''} onChange={e => upd('candidateStatus', e.target.value)}>
              <option value="">Select…</option>{STATUSES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </div>
          <div><label className="label">Experience (years)</label><input className="input" type="number" min={0} max={40} step={0.5} value={p.experienceYears ?? 0} onChange={e => upd('experienceYears', e.target.value)} /></div>
          <div className="md:col-span-3"><label className="label">A line about yourself</label><textarea className="input min-h-[70px]" value={p.bio ?? ''} onChange={e => upd('bio', e.target.value)} placeholder="Your focus, projects, and what you are looking for." /></div>
        </div>
        <div className="mt-4 flex justify-end">
          <button className="btn-primary" onClick={saveBasic} disabled={saving === 'basic'}><Save className="h-4 w-4" />{saving === 'basic' ? 'Saving…' : 'Save details'}</button>
        </div>
      </div>

      {/* 3 · Skills */}
      <div className="card p-5">
        <SectionHeader title="Skills" sub="Only recognised skills count toward eligibility — pick from the validated list." />
        <div className="flex flex-wrap gap-2">
          {skills.map(s => (
            <span key={s} className="chip bg-navy-100/70 text-navy-700 ring-1 ring-navy-200">
              {s}<button onClick={() => setSkills(x => x.filter(y => y !== s))} className="ml-0.5 rounded-full p-0.5 hover:bg-navy-200"><X className="h-3 w-3" /></button>
            </span>
          ))}
          {skills.length === 0 && <span className="text-[13px] text-navy-400">No skills added yet.</span>}
        </div>
        <div className="mt-3 flex gap-2">
          <div className="relative flex-1">
            <input className="input" list="skill-sugg" placeholder="Add a skill (choose from the list)…" value={skillDraft}
              onChange={e => setSkillDraft(e.target.value)} onKeyDown={e => e.key === 'Enter' && addSkill()} />
            <datalist id="skill-sugg">{skillOptions.map(s => <option key={s} value={s} />)}</datalist>
          </div>
          <button className="btn-ghost" onClick={addSkill}><Plus className="h-4 w-4" /> Add</button>
        </div>
        <div className="mt-4 flex justify-end">
          <button className="btn-primary" onClick={saveSkills} disabled={saving === 'skills'}><Wrench className="h-4 w-4" />{saving === 'skills' ? 'Saving…' : 'Save skills'}</button>
        </div>
      </div>

      {/* 4 · Interests */}
      <div className="card p-5">
        <SectionHeader title="Interests" sub="Which sectors you are drawn to — helps the engine understand you." />
        <div className="flex flex-wrap gap-2">
          {SECTOR_OPTIONS.map(([v, l]) => {
            const on = interests.includes(v);
            return (
              <button key={v} onClick={() => setInterests(x => on ? x.filter(y => y !== v) : [...x, v])}
                className={`chip transition-all ${on ? 'bg-saffron-500 text-white ring-1 ring-saffron-500' : 'bg-white text-navy-600 ring-1 ring-navy-200 hover:bg-navy-50'}`}>
                {on && <CheckCircle2 className="h-3.5 w-3.5" />}{l}
              </button>
            );
          })}
        </div>
        <div className="mt-4 flex justify-end">
          <button className="btn-primary" onClick={saveInterests} disabled={saving === 'interests'}><Heart className="h-4 w-4" />{saving === 'interests' ? 'Saving…' : 'Save interests'}</button>
        </div>
      </div>

      {/* 5 · Preferences */}
      <div className="card p-5">
        <SectionHeader title="Opportunity preferences" sub="Your preferences help PRAGATI understand what you value. They do not override eligibility, capacity, or the global allocation." />
        {prefs.length === 0 ? (
          <div className="py-6 text-center text-[13px] text-navy-400">No preferences yet — search for an opportunity to start ranking.</div>
        ) : (
          <div className="space-y-2">
            {prefs.map((x, i) => (
              <div key={x.opportunityId} className="flex items-center gap-3 rounded-xl border border-navy-100 px-4 py-2.5">
                <span className="font-display flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-saffron-500 text-[13px] font-bold text-white">{x.rank}</span>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[13.5px] font-semibold text-ink">{x.title}</div>
                  <div className="text-[11.5px] text-navy-400">{x.sector}</div>
                </div>
                <div className="flex gap-1">
                  <button className="btn-ghost btn-sm" title="Move up" onClick={() => movePref(i, -1)} disabled={i === 0}>↑</button>
                  <button className="btn-ghost btn-sm" title="Move down" onClick={() => movePref(i, 1)} disabled={i === prefs.length - 1}>↓</button>
                  <button className="btn-ghost btn-sm text-red-500" title="Remove" onClick={() => removePref(x.opportunityId)}><X className="h-4 w-4" /></button>
                </div>
              </div>
            ))}
          </div>
        )}
        <div className="mt-3">
          <input className="input w-full" placeholder="Search an opportunity to add as a preference…" value={oppSearch} onChange={e => searchOpps(e.target.value)} />
        </div>
        {oppSearch.trim() && oppResults.length === 0 && oppNoMatch !== '' && (
          <div className="mt-2 rounded-xl border border-dashed border-navy-200 bg-navy-50/40 px-4 py-3 text-center text-[12.5px] text-navy-500">
            {oppNoMatch === 'dups'
              ? 'All matching opportunities are already in your preference list.'
              : 'No matching opportunities found. Try a different title or keyword.'}
          </div>
        )}
        {oppResults.length > 0 && (
          <div className="mt-2 overflow-hidden rounded-xl border border-navy-100">
            {oppResults.map(o => (
              <button key={o.id} onClick={() => addPref(o.id)} className="flex w-full items-center justify-between border-b border-navy-50 px-4 py-2.5 text-left last:border-0 hover:bg-saffron-50/50">
                <span className="text-[13px] font-medium text-navy-700">{o.title}</span>
                <span className="text-[11.5px] text-navy-400">{o.sector} · {o.state}</span>
              </button>
            ))}
          </div>
        )}
        {prefMsg && <div className="mt-2 text-[12.5px] font-medium text-emerald-700">{prefMsg}</div>}
        {prefs.length > 0 && (
          <div className="mt-4 flex items-center justify-between gap-3">
            <span className="text-[12px] text-navy-400">{prefs.length} of {MAX_PREFS} preferences · ranks 1–{prefs.length}</span>
            <button className="btn-primary" onClick={savePrefs} disabled={saving === 'prefs'}><ListOrdered className="h-4 w-4" />{saving === 'prefs' ? 'Saving…' : 'Save preferences'}</button>
          </div>
        )}
      </div>

      {/* 6 · Resume & profile autofill */}
      <div className="card p-5">
        <SectionHeader title="Resume & profile autofill"
          sub="Upload a PDF or Word resume. PRAGATI finds your details and skills — you review every suggestion before anything is saved." />
        <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-navy-200 bg-navy-50/40 py-7 transition hover:border-saffron-300 hover:bg-saffron-50/30">
          <Upload className="h-6 w-6 text-navy-400" />
          <span className="text-[13px] font-semibold text-navy-600">{saving === 'resume' ? 'Uploading…' : 'Choose a file (PDF or DOCX, up to 5 MB)'}</span>
          <span className="text-[11.5px] text-navy-400">Replaces the previous resume — its unreviewed suggestions are discarded.</span>
          <input type="file" accept=".pdf,.doc,.docx" className="hidden" onChange={e => onResume(e.target.files?.[0] ?? null)} />
        </label>

        {resume && (
          <div className="mt-4">
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-navy-50/70 px-4 py-3 ring-1 ring-navy-100">
              <div className="flex items-center gap-2.5">
                <FileText className="h-4 w-4 text-navy-500" />
                <span className="text-[13px] font-semibold text-ink">{resume.originalName}</span>
                <Badge tone={resume.status === 'REVIEWED' ? 'green' : resume.status === 'PENDING_REVIEW' ? 'amber' : resume.status === 'FAILED' ? 'red' : 'navy'} dot>{resume.status.replace('_', ' ')}</Badge>
              </div>
              <span className="text-[11.5px] text-navy-400">{fmtTime(resume.createdAt)}</span>
            </div>

            {resume.status === 'FAILED' && (
              <Callout tone="danger" title="Could not read this file">
                {resume.statusMessage || 'Please upload a text-based PDF or a Word (.docx) resume.'}
              </Callout>
            )}

            {pendingReview && sugg && (
              <div className="mt-4 space-y-4">
                <div className="flex items-start gap-2.5 rounded-xl bg-saffron-50/70 p-4 ring-1 ring-saffron-100">
                  <Sparkles className="mt-0.5 h-4 w-4 shrink-0 text-saffron-600" />
                  <div className="text-[13px] leading-relaxed text-navy-800">
                    <span className="font-bold">Profile information found in your resume.</span>{' '}
                    Blank fields are pre-selected for safe auto-fill. Fields you have already filled are shown as{' '}
                    <span className="font-semibold">proposed updates</span> — nothing is changed until you confirm it here.
                  </div>
                </div>

                {sugg.fields.length > 0 && (
                  <div>
                    <div className="mb-2 text-[12px] font-bold uppercase tracking-wide text-navy-500">Profile fields</div>
                    <div className="space-y-2">
                      {sugg.fields.map(f => {
                        const cur = currentValue(p, f.field);
                        const isUpdate = cur !== '';
                        const isQual = f.field === 'qualification';
                        const isStatus = f.field === 'candidateStatus';
                        const shownValue = isQual
                          ? QUALS.find(([v]) => v === f.value)?.[1] ?? f.value
                          : isStatus ? STATUSES.find(([v]) => v === f.value)?.[1] ?? f.value : f.value;
                        return (
                          <div key={f.field} className={`rounded-xl border p-3.5 ${acceptField[f.field] ? 'border-emerald-200 bg-emerald-50/30' : 'border-navy-100'}`}>
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <span className="text-[13px] font-bold text-ink">{FIELD_LABELS[f.field] ?? f.field}</span>
                              <div className="flex items-center gap-2">
                                <Badge tone={f.confidence === 'High' ? 'green' : 'amber'}>{f.confidence} confidence</Badge>
                                <button onClick={() => setAcceptField(m => ({ ...m, [f.field]: !m[f.field] }))}
                                  className={`btn-ghost btn-sm ${acceptField[f.field] ? 'text-emerald-600' : 'text-navy-500'}`}>
                                  {acceptField[f.field] ? <><CheckCircle2 className="h-3.5 w-3.5" /> {isUpdate ? 'Use suggested value' : 'Fill in'}</> : <X className="h-3.5 w-3.5" />}
                                </button>
                              </div>
                            </div>
                            <div className="mt-1.5 text-[13px] text-navy-700">
                              {isUpdate ? (<>Current: <span className="font-semibold">{cur}</span> → Suggested: <span className="font-semibold text-emerald-700">{shownValue}</span></>) : shownValue}
                            </div>
                            <div className="mt-1 text-[11.5px] italic text-navy-400">Source: {f.evidence}</div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {sugg.skills.length > 0 && (
                  <div>
                    <div className="mb-2 text-[12px] font-bold uppercase tracking-wide text-navy-500">Skills detected</div>
                    <div className="flex flex-wrap gap-2">
                      {sugg.skills.map(s => (
                        <button key={s} onClick={() => setAcceptSkill(m => ({ ...m, [s]: m[s] === false ? true : false }))}
                          className={`chip ring-1 transition ${skills.includes(s) ? 'bg-navy-100/60 text-navy-400 ring-navy-200' : acceptSkill[s] !== false ? 'bg-emerald-50 text-emerald-700 ring-emerald-200' : 'bg-white text-navy-500 ring-navy-200'}`}>
                          {skills.includes(s) ? <><CheckCircle2 className="h-3.5 w-3.5" />{s} (already added)</> : <>{acceptSkill[s] !== false && <CheckCircle2 className="h-3.5 w-3.5" />}{s}</>}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {sugg.interests.length > 0 && (
                  <div>
                    <div className="mb-2 text-[12px] font-bold uppercase tracking-wide text-navy-500">Interests detected</div>
                    <div className="flex flex-wrap gap-2">
                      {sugg.interests.map(s => (
                        <button key={s} onClick={() => setAcceptInterest(m => ({ ...m, [s]: !m[s] }))}
                          className={`chip ring-1 transition ${acceptInterest[s] !== false ? 'bg-saffron-50 text-saffron-700 ring-saffron-200' : 'bg-white text-navy-500 ring-navy-200'}`}>
                          {SECTOR_OPTIONS.find(([v]) => v === s)?.[1] ?? s}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {sugg.fields.length === 0 && sugg.skills.length === 0 && sugg.interests.length === 0 && (
                  <Callout tone="info">No suggestions were found in this resume. Mark it as reviewed to continue.</Callout>
                )}

                <div className="flex flex-wrap justify-end gap-2 border-t border-navy-100 pt-4">
                  <button className="btn-ghost" disabled={applying} onClick={() => applySuggestions(true)}>
                    <ShieldCheck className="h-4 w-4" /> Dismiss suggestions
                  </button>
                  <button className="btn-primary" disabled={applying} onClick={() => applySuggestions(false)}>
                    <Save className="h-4 w-4" />{applying ? 'Applying…' : 'Apply confirmed suggestions'}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
