import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Building2, User, Sparkles, ArrowRight, Lock } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { errMsg } from '../api/client';
import { Logo, LogoMark } from '../components/Logo';

const ROLES = [
  { id: 'admin', label: 'Administrator', sub: 'Command center, runs, policy, fairness', icon: ShieldCheck, email: 'admin@pragati.gov.in', pass: 'Admin@123' },
  { id: 'provider', label: 'Organisation Partner', sub: 'Publish & manage opportunities', icon: Building2, email: 'provider@pragati.gov.in', pass: 'Provider@123' },
  { id: 'candidate', label: 'Candidate', sub: 'Profile, readiness, allocation result', icon: User, email: 'candidate@pragati.gov.in', pass: 'Candidate@123' },
];

export default function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [role, setRole] = useState('admin');
  const [email, setEmail] = useState(ROLES[0].email);
  const [pass, setPass] = useState(ROLES[0].pass);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const pick = (r: typeof ROLES[number]) => { setRole(r.id); setEmail(r.email); setPass(r.pass); setErr(''); };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setErr('');
    try {
      const me = await login(email, pass);
      nav(me.role === 'ADMIN' ? '/admin' : me.role === 'PROVIDER' ? '/provider' : '/candidate');
    } catch (ex) {
      setErr(errMsg(ex));
    } finally { setBusy(false); }
  };

  return (
    <div className="relative flex min-h-full items-stretch">
      <div className="sidebar-glass relative hidden w-[44%] flex-col justify-between overflow-hidden p-10 lg:flex">
        <div className="pointer-events-none absolute -right-32 -top-32 h-[420px] w-[420px] rounded-full bg-saffron-500/[0.07] blur-3xl" />
        <div className="pointer-events-none absolute -bottom-40 -left-24 h-[380px] w-[380px] rounded-full bg-emerald-400/[0.06] blur-3xl" />
        <div className="relative z-10">
          {/* Brand mark, top-left, on the dark surface directly — no white plate. */}
          <LogoMark height={44} textSize={14} />
          <div className="mt-10">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-white/[0.06] px-3.5 py-1.5 text-[11.5px] font-semibold text-saffron-300 ring-1 ring-white/10">
              <Sparkles className="h-3.5 w-3.5" /> Smart India Hackathon 2026 · SIH25033
            </div>
            <h1 className="font-display max-w-md text-[34px] font-bold leading-[1.15] text-white">
              One allocation engine for the <span className="text-saffron-400">entire candidate population</span>.
            </h1>
            <p className="mt-4 max-w-md text-[14px] leading-relaxed text-navy-200/75">
              PRAGATI allocates every eligible internship opportunity across all candidates — respecting
              eligibility, capacity, preferences, geography and policy — and explains every decision.
            </p>
            <div className="mt-8 grid max-w-md grid-cols-3 gap-3">
              {[['Global', 'not candidate-by-candidate'], ['Explainable', 'every assignment, factor by factor'], ['Fair', 'measured, not assumed']].map(([t, s]) => (
                <div key={t} className="rounded-xl bg-white/[0.05] p-3 ring-1 ring-white/10">
                  <div className="text-[13px] font-bold text-white">{t}</div>
                  <div className="mt-1 text-[10.5px] leading-snug text-navy-300/70">{s}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className="text-[11px] text-navy-300/60">Demonstration environment · all data is synthetic and system-generated</div>
      </div>

      <div className="flex flex-1 items-center justify-center px-6 py-10">
        <div className="w-full max-w-[430px]">
          <div className="mb-7 flex justify-center lg:hidden"><Logo width={196} className="max-w-full [&>img]:max-w-full [&>img]:h-auto" /></div>
          <h2 className="font-display text-[22px] font-bold text-ink">Sign in to PRAGATI</h2>
          <p className="mt-1 text-[13.5px] text-navy-500">Choose a demonstration role — credentials are pre-filled.</p>

          <div className="mt-5 space-y-2">
            {ROLES.map(r => (
              <button key={r.id} type="button" onClick={() => pick(r)}
                className={`flex w-full items-center gap-3.5 rounded-2xl border p-3.5 text-left transition-all ${role === r.id ? 'border-saffron-300 bg-saffron-50/60 shadow-[0_0_0_3px_rgba(238,132,32,0.12)]' : 'border-navy-900/10 bg-white hover:border-navy-200'}`}>
                <div className={`rounded-xl p-2.5 ${role === r.id ? 'bg-saffron-500 text-white' : 'bg-navy-50 text-navy-600'}`}><r.icon className="h-[18px] w-[18px]" /></div>
                <div className="min-w-0 flex-1">
                  <div className="text-[13.5px] font-bold text-ink">{r.label}</div>
                  <div className="truncate text-[12px] text-navy-400">{r.sub}</div>
                </div>
                <div className={`h-4 w-4 rounded-full border-2 ${role === r.id ? 'border-saffron-500 bg-saffron-500' : 'border-navy-200'}`} />
              </button>
            ))}
          </div>

          <form onSubmit={submit} className="mt-5 space-y-3.5">
            <div>
              <label className="label">Email</label>
              <input className="input" value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" />
            </div>
            <div>
              <label className="label">Password</label>
              <input className="input" type="password" value={pass} onChange={e => setPass(e.target.value)} autoComplete="current-password" />
            </div>
            {err && <div className="rounded-xl bg-red-50 px-3.5 py-2.5 text-[12.5px] font-medium text-red-700 ring-1 ring-red-200">{err}</div>}
            <button className="btn-primary w-full" disabled={busy}>
              <Lock className="h-4 w-4" /> {busy ? 'Signing in…' : 'Sign in'} <ArrowRight className="h-4 w-4" />
            </button>
          </form>
          <div className="mt-5 text-center text-[11.5px] text-navy-400">
            Demo credentials are pre-filled for each role. This is a demonstration build.
          </div>
        </div>
      </div>
    </div>
  );
}
