import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Compass, ArrowRight, CheckCircle2, ChevronDown, ChevronUp } from 'lucide-react';
import { apiGet, errMsg } from '../../api/client';
import { SectionHeader, Spinner, Callout, Badge, KV } from '../../components/ui';

interface Step { n: number; title: string; what: string; why: string; data: Record<string, any> | null; path: string | null; }

export default function AdminJudge() {
  const nav = useNavigate();
  const [steps, setSteps] = useState<Step[]>([]);
  const [open, setOpen] = useState<number>(1);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try { setSteps(await apiGet<Step[]>('/admin/judge/steps')); }
      catch (e) { setErr(errMsg(e)); }
      setLoading(false);
    })();
  }, []);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">Judge Walkthrough</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">
          The story of the system in the order we would tell it — problem, data, engine, evidence, and what is deliberately out of scope.
        </p>
      </div>
      {err && <Callout tone="warn">{err}</Callout>}
      {loading ? <Spinner label="Loading the walkthrough…" /> : (
        <div className="space-y-3">
          {steps.map(s => {
            const isOpen = open === s.n;
            return (
              <div key={s.n} className={`card overflow-hidden transition-all ${isOpen ? 'ring-1 ring-saffron-200' : ''}`}>
                <button className="flex w-full items-center gap-4 p-4 text-left" onClick={() => setOpen(isOpen ? 0 : s.n)}>
                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl font-display text-[15px] font-bold ${isOpen ? 'bg-saffron-500 text-white' : 'bg-navy-50 text-navy-600'}`}>
                    {s.n}
                  </div>
                  <div className="flex-1">
                    <div className="font-display text-[15px] font-bold text-ink">{s.title}</div>
                    <div className="mt-0.5 line-clamp-1 text-[12.5px] text-navy-500">{s.what}</div>
                  </div>
                  {isOpen ? <ChevronUp className="h-4 w-4 text-navy-400" /> : <ChevronDown className="h-4 w-4 text-navy-400" />}
                </button>
                {isOpen && (
                  <div className="border-t border-navy-100 px-5 py-4 pl-[76px]">
                    <div className="space-y-4">
                      <div>
                        <div className="mb-1 text-[11px] font-bold uppercase tracking-[0.08em] text-navy-400">What it is</div>
                        <p className="text-[13.5px] leading-relaxed text-navy-700">{s.what}</p>
                      </div>
                      <div>
                        <div className="mb-1 text-[11px] font-bold uppercase tracking-[0.08em] text-saffron-600">Why it matters</div>
                        <p className="text-[13.5px] leading-relaxed text-navy-700">{s.why}</p>
                      </div>
                      {s.data && Object.keys(s.data).length > 0 && (
                        <div className="rounded-xl bg-navy-50/70 p-4 ring-1 ring-navy-100">
                          {Object.entries(s.data).map(([k, v]) => (
                            <KV key={k} k={k.replace(/([A-Z])/g, ' $1').replace(/^./, c => c.toUpperCase())} v={String(v)} />
                          ))}
                        </div>
                      )}
                      {s.path && (
                        <button className="btn-dark btn-sm" onClick={() => nav(s.path!)}>
                          <Compass className="h-4 w-4" /> Go to this part <ArrowRight className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
