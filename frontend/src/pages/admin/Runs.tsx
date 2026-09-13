import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { History, RefreshCcw } from 'lucide-react';
import { apiGet, errMsg } from '../../api/client';
import { RunSummary } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, statusTone, fmtMs, fmtTime, EmptyState } from '../../components/ui';

export default function AdminRuns() {
  const nav = useNavigate();
  const [rows, setRows] = useState<RunSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const d = await apiGet<any>('/admin/runs', { page: 0, size: 30 });
      setRows(d.content ?? []);
    } catch (e) { setErr(errMsg(e)); }
    setLoading(false);
  };
  useEffect(() => { void load(); }, []);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">Allocation Runs</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">Every allocation the engine has produced — a complete, replayable decision history.</p>
      </div>
      {err && <Callout tone="warn">{err}</Callout>}
      <div className="card">
        {loading ? <Spinner label="Loading run history…" /> : rows.length === 0 ? (
          <EmptyState title="No allocation runs yet" hint="Start a run from the Command Center to build the decision history." icon={<History className="h-8 w-8" />} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px]">
              <thead>
                <tr className="border-b border-navy-100">
                  <th className="th">Run</th><th className="th">Scenario</th><th className="th">Policy</th><th className="th">Status</th>
                  <th className="th">Solver outcome</th><th className="th">Duration</th><th className="th">Started</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.id} className="tr-hover cursor-pointer border-b border-navy-50 last:border-0" onClick={() => nav(`/admin/runs/${r.id}`)}>
                    <td className="td">
                      <span className="font-display font-bold text-ink">#{r.number}</span>
                      {r.reallocation && <Badge tone="saffron">reallocation</Badge>}
                      {r.parentRunNumber && <span className="ml-2 text-[11.5px] text-navy-400">from #{r.parentRunNumber}</span>}
                      {r.stale && <Badge tone="gray">superseded</Badge>}
                    </td>
                    <td className="td">{r.scenario}</td>
                    <td className="td text-navy-500">{r.policyName}</td>
                    <td className="td"><Badge tone={statusTone(r.status)} dot>{r.status}</Badge></td>
                    <td className="td text-navy-500">{r.solverStatus ?? '—'}</td>
                    <td className="td text-navy-500">{fmtMs(r.totalRuntimeMs)}</td>
                    <td className="td text-navy-500">{fmtTime(r.createdAt)}</td>
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
