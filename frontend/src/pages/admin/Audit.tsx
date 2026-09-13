import React, { useEffect, useState } from 'react';
import { ScrollText } from 'lucide-react';
import { apiGet, errMsg } from '../../api/client';
import { AuditRow } from '../../api/types';
import { SectionHeader, Spinner, Callout, Badge, fmtTime, EmptyState } from '../../components/ui';

export default function AdminAudit() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const d = await apiGet<any>('/admin/audit', { page: 0, size: 60 });
        setRows(d.content ?? []);
      } catch (e) { setErr(errMsg(e)); }
      setLoading(false);
    })();
  }, []);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-[24px] font-bold text-ink">Audit Trail</h1>
        <p className="mt-1 text-[13.5px] text-navy-500">
          A complete, tamper-evident record of every significant action — sign-ins, scenario loads, runs, policy changes and cancellations.
        </p>
      </div>
      {err && <Callout tone="warn">{err}</Callout>}
      <div className="card">
        {loading ? <Spinner label="Loading audit trail…" /> : rows.length === 0 ? (
          <EmptyState title="No audit entries yet" icon={<ScrollText className="h-8 w-8" />} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px]">
              <thead>
                <tr className="border-b border-navy-100">
                  <th className="th">When</th><th className="th">Action</th><th className="th">Actor</th><th className="th">What happened</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.id} className="tr-hover border-b border-navy-50 last:border-0">
                    <td className="td whitespace-nowrap text-navy-500">{fmtTime(r.at)}</td>
                    <td className="td"><Badge tone="navy">{r.action}</Badge></td>
                    <td className="td font-medium text-ink">{r.actor}</td>
                    <td className="td text-navy-600">{r.summary}</td>
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
