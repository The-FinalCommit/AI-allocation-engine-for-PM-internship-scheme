import React from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, Database, History, FlaskConical, RefreshCcw, ScrollText, Compass, Cpu,
  User, Briefcase, Building2, LogOut, Gauge,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { LogoMark } from './Logo';

const ADMIN_NAV = [
  { to: '/admin', end: true, label: 'Command Center', icon: LayoutDashboard },
  { to: '/admin/scenarios', label: 'Scenarios & Dataset', icon: Database },
  { to: '/admin/runs', label: 'Allocation Runs', icon: History },
  { to: '/admin/whatif', label: 'Policy Lab (What-if)', icon: FlaskConical },
  { to: '/admin/reallocation', label: 'Reallocation', icon: RefreshCcw },
  { to: '/admin/audit', label: 'Audit Trail', icon: ScrollText },
  { to: '/admin/judge', label: 'Judge Walkthrough', icon: Compass },
  { to: '/admin/technical', label: 'System Health', icon: Cpu },
];
const CAND_NAV = [
  { to: '/candidate', end: true, label: 'My Dashboard', icon: LayoutDashboard },
  { to: '/candidate/explore', label: 'Explore Opportunities', icon: Briefcase },
  { to: '/candidate/analytics', label: 'My Fit Analytics', icon: Gauge },
  { to: '/candidate/profile', label: 'My Profile', icon: User },
];
const PROV_NAV = [
  { to: '/provider', end: true, label: 'Provider Overview', icon: LayoutDashboard },
  { to: '/provider/opportunities', label: 'My Opportunities', icon: Building2 },
];

const TITLES: [string, string][] = [
  ['/admin/scenarios', 'Scenarios & Dataset'], ['/admin/runs', 'Allocation Runs'], ['/admin/whatif', 'Policy Lab'],
  ['/admin/reallocation', 'Reallocation'], ['/admin/audit', 'Audit Trail'], ['/admin/judge', 'Judge Walkthrough'],
  ['/admin/technical', 'System Health'], ['/candidate/explore', 'Explore Opportunities'],
  ['/candidate/analytics', 'My Fit Analytics'], ['/candidate/profile', 'My Profile'], ['/provider/opportunities', 'My Opportunities'],
];

export default function Shell() {
  const { me, logout } = useAuth();
  const loc = useLocation();
  const nav = me?.role === 'ADMIN' ? ADMIN_NAV : me?.role === 'PROVIDER' ? PROV_NAV : CAND_NAV;
  const title = TITLES.find(([p]) => loc.pathname.startsWith(p) && p !== '/admin')?.[1];

  return (
    <div className="flex h-full">
      <aside className="sidebar-glass fixed inset-y-0 left-0 z-40 flex w-[248px] flex-col border-r border-white/5">
        <div className="flex justify-center px-5 pb-5 pt-6">
          <LogoMark height={32} />
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-3">
          {nav.map(n => (
            <NavLink key={n.to} to={n.to} end={n.end}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
              <n.icon className="nav-ico" />
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-white/5 p-4">
          <div className="mb-3 flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-b from-saffron-400 to-saffron-600 text-[13px] font-bold text-white">
              {me?.name?.slice(0, 1) ?? '?'}
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-semibold text-white">{me?.name}</div>
              <div className="text-[11px] text-navy-300/80">{me?.role === 'ADMIN' ? 'Administrator' : me?.role === 'PROVIDER' ? 'Organisation Partner' : 'Candidate'}</div>
            </div>
            <button onClick={logout} title="Sign out" className="rounded-lg p-1.5 text-navy-300 transition hover:bg-white/10 hover:text-white">
              <LogOut className="h-4 w-4" />
            </button>
          </div>
          <div className="rounded-lg bg-white/[0.04] px-2.5 py-1.5 text-[10px] leading-relaxed text-navy-300/60 ring-1 ring-white/5">
            Demonstration environment · synthetic data
          </div>
        </div>
      </aside>

      <div className="ml-[248px] flex min-h-full flex-1 flex-col">
        <main className="flex-1 px-8 py-7">
          <div className="mx-auto max-w-[1240px]">
            <Outlet />
          </div>
        </main>
        <footer className="border-t border-navy-900/[0.06] px-8 py-3">
          <div className="mx-auto flex max-w-[1240px] items-center justify-between text-[11.5px] text-navy-400">
            <span>Smart India Hackathon 2026 · SIH25033</span>
            <span>Demonstration build · all data is synthetic and system-generated</span>
          </div>
        </footer>
      </div>
    </div>
  );
}
