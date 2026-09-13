import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, Briefcase, Armchair, Users, TrendingUp, ArrowRight } from 'lucide-react';
import { apiGet, errMsg } from '../../api/client';
import { SectionHeader, Spinner, Callout, StatCard, KV, Badge, fmtNum, fmtPct } from '../../components/ui';
import { HBar } from '../../components/charts';

export default function ProviderHome() {
  const nav = useNavigate();
  const [org, setOrg] = useState<any>(null);
  const [impact, setImpact] = useState<any[]>([]);
  const [cap, setCap] = useState<any[]>([]);
  const [err, setErr] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const [o, i, c] = await Promise.all([
          apiGet<any>('/providers/me'),
          apiGet<any>('/providers/me/impact'),
          apiGet<any>('/providers/me/capacity'),
        ]);
        setOrg(o); setImpact(i ?? []); setCap(c ?? []);
      } catch (e) { setErr(errMsg(e)); }
    })();
  }, []);

  if (!org && !err) return <Spinner label="Loading your organisation…" />;
  if (err && !org) return <Callout tone="danger">{err}</Callout>;

  const filled = impact.reduce((a, b) => a + b.allocated, 0);
  const unmet = impact.reduce((a, b) => a + b.unmetDemand, 0);
  const avgFit = impact.length > 0 ? impact.reduce((a, b) => a + b.avgSuitability * b.allocated, 0) / Math.max(1, filled) : 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-white px-3 py-1 text-[11px] font-semibold text-navy-500 ring-1 ring-navy-900/10">
            <Building2 className="h-3.5 w-3.5 text-saffron-500" /> {org.orgType} · {org.state}
          </div>
          <h1 className="font-display text-[24px] font-bold text-ink">{org.orgName}</h1>
          {org.about && <p className="mt-1 max-w-xl text-[13.5px] text-navy-500">{org.about}</p>}
        </div>
        <button className="btn-primary" onClick={() => nav('/provider/opportunities')}>
          <Briefcase className="h-4 w-4" /> Manage opportunities
        </button>
      </div>

      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatCard icon={<Briefcase className="h-5 w-5" />} label="Opportunities" value={fmtNum(org.opportunityCount)} sub={`${fmtNum(org.activeCount)} open now`} />
        <StatCard icon={<Armchair className="h-5 w-5" />} label="Total seats" value={fmtNum(org.totalSeats)} tone="saffron" sub={`${fmtNum(filled)} filled in the latest run`} />
        <StatCard icon={<TrendingUp className="h-5 w-5" />} label="Average fit" value={fmtPct(avgFit)} tone="leaf" sub="of allocated candidates" />
        <StatCard icon={<Users className="h-5 w-5" />} label="Unmet demand" value={fmtNum(unmet)} tone="red" sub="eligible candidates without a seat" />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card p-5">
          <SectionHeader title="Seats filled per opportunity" sub="Latest allocation run" />
          <HBar data={impact.map(x => ({ name: x.title.length > 22 ? x.title.slice(0, 22) + '…' : x.title, value: x.allocated }))} dataKey="value" height={Math.max(160, impact.length * 34)} color="#1b8a5a" />
        </div>
        <div className="card p-5">
          <SectionHeader title="Capacity vs demand" sub="Where pressure is building" />
          {cap.length === 0 ? <div className="py-8 text-center text-[13px] text-navy-400">No capacity data yet.</div> : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead><tr className="border-b border-navy-100"><th className="th">Opportunity</th><th className="th">Seats</th><th className="th">Eligible demand</th><th className="th">Pressure</th></tr></thead>
                <tbody>
                  {cap.map(c => (
                    <tr key={c.opportunityId} className="border-b border-navy-50 last:border-0">
                      <td className="td font-medium">{c.title}</td>
                      <td className="td text-navy-500">{c.capacity}</td>
                      <td className="td text-navy-500">{c.eligible}</td>
                      <td className="td"><Badge tone={c.pressure >= 2 ? 'red' : c.pressure >= 1 ? 'amber' : 'green'}>{c.pressure.toFixed(1)}×</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <div className="card p-5">
        <SectionHeader title="Your impact by opportunity" sub="What the latest global allocation did with your seats" />
        {impact.length === 0 ? <div className="py-8 text-center text-[13px] text-navy-400">Publish opportunities to see your impact here.</div> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px]">
              <thead><tr className="border-b border-navy-100"><th className="th">Opportunity</th><th className="th">Capacity</th><th className="th">Allocated</th><th className="th">Unmet demand</th><th className="th">Average suitability</th></tr></thead>
              <tbody>
                {impact.map(x => (
                  <tr key={x.opportunityId} className="tr-hover border-b border-navy-50 last:border-0">
                    <td className="td font-medium text-ink">{x.title}</td>
                    <td className="td text-navy-500">{x.capacity}</td>
                    <td className="td"><Badge tone={x.allocated > 0 ? 'green' : 'gray'} dot>{x.allocated}</Badge></td>
                    <td className="td">{x.unmetDemand > 0 ? <Badge tone="amber">{x.unmetDemand}</Badge> : <span className="text-navy-400">—</span>}</td>
                    <td className="td">{fmtPct(x.avgSuitability)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
