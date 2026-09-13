import React, { useEffect, useState } from 'react';
import { Plus, Pencil, CheckCircle2, PauseCircle, XCircle, Star, Users } from 'lucide-react';
import { apiGet, apiPost, apiPut, apiPatch, errMsg } from '../../api/client';
import { SectionHeader, Spinner, Callout, Badge, Modal, fmtNum } from '../../components/ui';

const SECTOR_OPTIONS: [string, string][] = [
  ['SOFTWARE_IT', 'Software & IT'], ['DATA_ANALYTICS', 'Data & Analytics'], ['FINANCE_BANKING', 'Finance & Banking'],
  ['DESIGN_MEDIA', 'Design & Media'], ['MARKETING_COMMUNICATION', 'Marketing & Communication'],
  ['PUBLIC_ADMIN_POLICY', 'Public Administration & Policy'], ['HEALTHCARE_BIOTECH', 'Healthcare & Biotech'],
  ['MANUFACTURING_OPERATIONS', 'Manufacturing & Operations'], ['RESEARCH_DEVELOPMENT', 'Research & Development'],
  ['ENVIRONMENTAL_ENERGY', 'Environmental & Energy'],
];
// Labels must match backend Labels.java exactly — candidates see the same wording everywhere.
const QUALS: [string, string][] = [['HIGHER_SECONDARY', 'Higher Secondary'], ['BACHELORS', "Bachelor's Degree"], ['POST_GRADUATE', 'Postgraduate'], ['DOCTORAL', 'Doctoral']];
const STATES = ['Andhra Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Delhi', 'Gujarat', 'Haryana', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Odisha', 'Punjab', 'Rajasthan', 'Tamil Nadu', 'Telangana', 'Uttar Pradesh', 'Uttarakhand', 'West Bengal'];

const BLANK = { title: '', sector: '', state: '', city: '', capacity: 5, durationMonths: 3, minQualification: 'BACHELORS', description: '', mandatorySkills: '' as string | never[], niceSkills: '' as string | never[] };

// The API returns human labels ("Software & IT", "Bachelor's Degree"); the
// form selects speak enum values. Map both directions so editing never resets a field.
const SECTOR_BY_LABEL: Record<string, string> = Object.fromEntries(SECTOR_OPTIONS.map(([v, l]) => [l, v]));
const QUAL_BY_LABEL: Record<string, string> = Object.fromEntries(QUALS.map(([v, l]) => [l, v]));
const QUAL_LABEL: Record<string, string> = Object.fromEntries(QUALS.map(([v, l]) => [v, l]));
const SECTOR_LABEL: Record<string, string> = Object.fromEntries(SECTOR_OPTIONS.map(([v, l]) => [v, l]));

export default function ProviderOpportunities() {
  const [rows, setRows] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [editing, setEditing] = useState<any | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [form, setForm] = useState<any>(BLANK);
  const [busy, setBusy] = useState(false);

  const load = async () => {
    setLoading(true); setErr('');
    try {
      const d = await apiGet<any>('/providers/me/opportunities', { page: 0, size: 50 });
      setRows(d.content ?? []);
    } catch (e) { setErr(errMsg(e)); }
    setLoading(false);
  };
  useEffect(() => { void load(); }, []);

  const openNew = () => { setForm({ ...BLANK }); setIsNew(true); setEditing({}); };
  const openEdit = (o: any) => {
    setForm({
      title: o.title,
      sector: SECTOR_BY_LABEL[o.sector] ?? o.sector,
      state: o.state, city: o.city ?? '', capacity: o.capacity,
      durationMonths: o.durationMonths,
      minQualification: QUAL_BY_LABEL[o.minQualification] ?? o.minQualification,
      description: o.description ?? '',
      mandatorySkills: Array.isArray(o.mandatorySkills) ? o.mandatorySkills.join(', ') : (o.mandatorySkills ?? ''),
      niceSkills: Array.isArray(o.niceSkills) ? o.niceSkills.join(', ') : (o.niceSkills ?? ''),
    });
    setIsNew(false); setEditing(o);
  };

  const save = async () => {
    setBusy(true); setErr(''); setMsg('');
    const body = {
      title: form.title, sector: form.sector, state: form.state, city: form.city || null,
      capacity: Number(form.capacity), durationMonths: Number(form.durationMonths),
      minQualification: form.minQualification, description: form.description,
      mandatorySkills: String(form.mandatorySkills).split(',').map(s => s.trim()).filter(Boolean),
      niceSkills: String(form.niceSkills).split(',').map(s => s.trim()).filter(Boolean),
    };
    try {
      if (isNew) await apiPost('/providers/me/opportunities', body);
      else await apiPut(`/providers/me/opportunities/${editing.id}`, body);
      setMsg(isNew ? 'Opportunity published.' : 'Opportunity updated.');
      setEditing(null); await load();
    } catch (e) { setErr(errMsg(e)); }
    setBusy(false);
  };

  const setStatus = async (id: number, status: string) => {
    const verb: Record<string, string> = { ACTIVE: 'reopened', PAUSED: 'paused — hidden from candidates', CLOSED: 'closed' };
    try { await apiPatch(`/providers/me/opportunities/${id}/status`, { status }); setMsg(`Opportunity ${verb[status] ?? status.toLowerCase()}.`); await load(); }
    catch (e) { setErr(errMsg(e)); }
  };

  const upd = (k: string, v: any) => setForm((f: any) => ({ ...f, [k]: v }));

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-[24px] font-bold text-ink">My Opportunities</h1>
          <p className="mt-1 text-[13.5px] text-navy-500">Publish, update, open, pause or close the internships your organisation offers.</p>
        </div>
        <button className="btn-primary" onClick={openNew}><Plus className="h-4 w-4" /> New opportunity</button>
      </div>
      {err && <Callout tone="warn">{err}</Callout>}
      {msg && <Callout tone="success">{msg}</Callout>}

      <div className="card">
        {loading ? <Spinner label="Loading opportunities…" /> : rows.length === 0 ? (
          <div className="py-12 text-center text-[13px] text-navy-400">You have not published any opportunities yet.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px]">
              <thead>
                <tr className="border-b border-navy-100">
                  <th className="th">Opportunity</th><th className="th">Location</th><th className="th">Seats</th>
                  <th className="th">Eligible</th><th className="th">Preferences</th><th className="th">Allocated</th><th className="th">Status</th><th className="th" />
                </tr>
              </thead>
              <tbody>
                {rows.map(o => (
                  <tr key={o.id} className="tr-hover border-b border-navy-50 last:border-0">
                    <td className="td">
                      <div className="font-semibold text-ink">{o.title}</div>
                      <div className="text-[11.5px] text-navy-400">{o.sector} · {o.durationMonths} months</div>
                    </td>
                    <td className="td text-navy-500">{o.city ? `${o.city}, ` : ''}{o.state}</td>
                    <td className="td">{o.capacity}</td>
                    <td className="td"><span className="inline-flex items-center gap-1 text-navy-600"><Users className="h-3.5 w-3.5" />{fmtNum(o.eligibleCount)}</span></td>
                    <td className="td"><span className="inline-flex items-center gap-1 text-navy-600"><Star className="h-3.5 w-3.5" />{fmtNum(o.preferenceCount)}</span></td>
                    <td className="td">{o.allocatedCount > 0 ? <Badge tone="green" dot>{o.allocatedCount}</Badge> : <span className="text-navy-400">—</span>}</td>
                    <td className="td"><Badge tone={o.status === 'Active' ? 'green' : o.status === 'Paused' ? 'amber' : 'red'} dot>{o.status}</Badge></td>
                    <td className="td">
                      <div className="flex justify-end gap-1">
                        <button className="btn-ghost btn-sm" onClick={() => openEdit(o)}><Pencil className="h-3.5 w-3.5" /> Edit</button>
                        {o.status === 'Active' && <button className="btn-ghost btn-sm" onClick={() => setStatus(o.id, 'PAUSED')} title="Pause — hide from candidates without deleting"><PauseCircle className="h-4 w-4 text-amber-500" /></button>}
                        {o.status === 'Paused' && <button className="btn-ghost btn-sm" onClick={() => setStatus(o.id, 'ACTIVE')} title="Open again"><CheckCircle2 className="h-4 w-4 text-emerald-500" /></button>}
                        {o.status !== 'Closed' && <button className="btn-ghost btn-sm text-red-500" onClick={() => setStatus(o.id, 'CLOSED')} title="Close — permanently stop the internship"><XCircle className="h-4 w-4" /></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal open={!!editing} onClose={() => setEditing(null)} title={isNew ? 'New opportunity' : 'Edit opportunity'} wide>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="md:col-span-2">
            <label className="label">Title</label>
            <input className="input" value={form.title} onChange={e => upd('title', e.target.value)} placeholder="e.g. Data Analytics Intern" />
            <p className="mt-1 text-[11.5px] text-navy-400">This is what candidates see when searching internships.</p>
          </div>
          <div>
            <label className="label">Sector</label>
            <select className="input" value={form.sector} onChange={e => upd('sector', e.target.value)}>
              <option value="">Select…</option>{SECTOR_OPTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
            <p className="mt-1 text-[11.5px] text-navy-400">Used for candidate interest matching and sector reports.</p>
          </div>
          <div>
            <label className="label">Minimum qualification</label>
            <select className="input" value={form.minQualification} onChange={e => upd('minQualification', e.target.value)}>
              {QUALS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
            <p className="mt-1 text-[11.5px] text-navy-400">Candidates below this level are not eligible — the engine enforces it.</p>
          </div>
          <div>
            <label className="label">State</label>
            <select className="input" value={form.state} onChange={e => upd('state', e.target.value)}>
              <option value="">Select…</option>{STATES.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
            <p className="mt-1 text-[11.5px] text-navy-400">Where the internship is based — used for location fit and geography views.</p>
          </div>
          <div><label className="label">City / district</label><input className="input" value={form.city} onChange={e => upd('city', e.target.value)} /></div>
          <div>
            <label className="label">Seats (capacity)</label>
            <input className="input" type="number" min={1} max={200} value={form.capacity} onChange={e => upd('capacity', e.target.value)} />
            <p className="mt-1 text-[11.5px] text-navy-400">Maximum number of candidates this internship can take in one allocation.</p>
          </div>
          <div>
            <label className="label">Duration (months)</label>
            <input className="input" type="number" min={1} max={24} value={form.durationMonths} onChange={e => upd('durationMonths', e.target.value)} />
          </div>
          <div className="md:col-span-2">
            <label className="label">Required skills (comma-separated)</label>
            <input className="input" value={form.mandatorySkills} onChange={e => upd('mandatorySkills', e.target.value)} placeholder="e.g. Python, SQL" />
            <p className="mt-1 text-[11.5px] text-navy-400">Candidates must have <em>all</em> of these verified skills, or they are not eligible for this internship.</p>
          </div>
          <div className="md:col-span-2">
            <label className="label">Preferred skills (comma-separated, optional)</label>
            <input className="input" value={form.niceSkills} onChange={e => upd('niceSkills', e.target.value)} placeholder="e.g. Power BI, Statistics" />
            <p className="mt-1 text-[11.5px] text-navy-400">Not mandatory — candidates who have these score higher on fit.</p>
          </div>
          <div className="md:col-span-2">
            <label className="label">Description</label>
            <textarea className="input min-h-[90px]" value={form.description} onChange={e => upd('description', e.target.value)} placeholder="What will the intern work on? What will they learn?" />
          </div>
        </div>

        {/* What candidates will see */}
        {form.title && (
          <div className="mt-5 rounded-xl border border-navy-100 bg-navy-50/40 p-4">
            <div className="mb-2 text-[11px] font-bold uppercase tracking-wide text-navy-400">What candidates will see</div>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="text-[14.5px] font-bold text-ink">{form.title}</div>
                <div className="mt-0.5 text-[12px] text-navy-500">
                  {SECTOR_LABEL[form.sector] ?? '—'} · {form.city ? form.city + ', ' : ''}{form.state || '—'} · {form.capacity} seats · {form.durationMonths} months
                </div>
              </div>
              <div className="text-right text-[11.5px] text-navy-400">Min qualification: {QUAL_LABEL[form.minQualification] ?? '—'}</div>
            </div>
            <div className="mt-2.5 flex flex-wrap gap-1.5">
              {String(form.mandatorySkills).split(',').map(s => s.trim()).filter(Boolean).map(s => (
                <span key={'m' + s} className="chip bg-navy-100/70 text-navy-700 ring-1 ring-navy-200">{s} · required</span>
              ))}
              {String(form.niceSkills).split(',').map(s => s.trim()).filter(Boolean).map(s => (
                <span key={'n' + s} className="chip bg-saffron-50 text-saffron-700 ring-1 ring-saffron-100">{s} · preferred</span>
              ))}
            </div>
          </div>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <button className="btn-ghost" onClick={() => setEditing(null)}>Cancel</button>
          <button className="btn-primary" onClick={save} disabled={busy || !form.title || !form.sector || !form.state}>{busy ? 'Saving…' : isNew ? 'Publish' : 'Save changes'}</button>
        </div>
      </Modal>
    </div>
  );
}
