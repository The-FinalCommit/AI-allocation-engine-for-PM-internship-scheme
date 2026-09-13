import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Spinner } from './components/ui';
import Shell from './components/Shell';
import Login from './pages/Login';
import AdminOverview from './pages/admin/Overview';
import AdminScenarios from './pages/admin/Scenarios';
import AdminRuns from './pages/admin/Runs';
import AdminRunDetail from './pages/admin/RunDetail';
import AdminWhatIf from './pages/admin/WhatIf';
import AdminReallocation from './pages/admin/Reallocation';
import AdminAudit from './pages/admin/Audit';
import AdminJudge from './pages/admin/Judge';
import AdminTechnical from './pages/admin/Technical';
import CandidateHome from './pages/candidate/Home';
import CandidateExplore from './pages/candidate/Explore';
import CandidateAnalytics from './pages/candidate/Analytics';
import CandidateProfile from './pages/candidate/Profile';
import ProviderHome from './pages/provider/Home';
import ProviderOpps from './pages/provider/Opportunities';

function Guard({ role, children }: { role: 'ADMIN' | 'PROVIDER' | 'CANDIDATE'; children: React.ReactNode }) {
  const { me, loading } = useAuth();
  if (loading) return <Spinner label="Loading your workspace…" />;
  if (!me) return <Navigate to="/login" replace />;
  if (me.role !== role) return <Navigate to={me.role === 'ADMIN' ? '/admin' : me.role === 'PROVIDER' ? '/provider' : '/candidate'} replace />;
  return <>{children}</>;
}

export default function App() {
  const { me, loading } = useAuth();
  if (loading) return <div className="flex h-full items-center justify-center"><Spinner label="Starting PRAGATI…" /></div>;
  return (
    <Routes>
      <Route path="/login" element={me ? <Navigate to={me.role === 'ADMIN' ? '/admin' : me.role === 'PROVIDER' ? '/provider' : '/candidate'} replace /> : <Login />} />
      <Route element={<Shell />}>
        <Route path="/admin" element={<Guard role="ADMIN"><AdminOverview /></Guard>} />
        <Route path="/admin/scenarios" element={<Guard role="ADMIN"><AdminScenarios /></Guard>} />
        <Route path="/admin/runs" element={<Guard role="ADMIN"><AdminRuns /></Guard>} />
        <Route path="/admin/runs/:id" element={<Guard role="ADMIN"><AdminRunDetail /></Guard>} />
        <Route path="/admin/whatif" element={<Guard role="ADMIN"><AdminWhatIf /></Guard>} />
        <Route path="/admin/reallocation" element={<Guard role="ADMIN"><AdminReallocation /></Guard>} />
        <Route path="/admin/audit" element={<Guard role="ADMIN"><AdminAudit /></Guard>} />
        <Route path="/admin/judge" element={<Guard role="ADMIN"><AdminJudge /></Guard>} />
        <Route path="/admin/technical" element={<Guard role="ADMIN"><AdminTechnical /></Guard>} />
        <Route path="/candidate" element={<Guard role="CANDIDATE"><CandidateHome /></Guard>} />
        <Route path="/candidate/explore" element={<Guard role="CANDIDATE"><CandidateExplore /></Guard>} />
        <Route path="/candidate/analytics" element={<Guard role="CANDIDATE"><CandidateAnalytics /></Guard>} />
        <Route path="/candidate/profile" element={<Guard role="CANDIDATE"><CandidateProfile /></Guard>} />
        <Route path="/provider" element={<Guard role="PROVIDER"><ProviderHome /></Guard>} />
        <Route path="/provider/opportunities" element={<Guard role="PROVIDER"><ProviderOpps /></Guard>} />
      </Route>
      <Route path="*" element={<Navigate to={me ? (me.role === 'ADMIN' ? '/admin' : me.role === 'PROVIDER' ? '/provider' : '/candidate') : '/login'} replace />} />
    </Routes>
  );
}
