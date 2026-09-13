import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { apiPost, apiGet, setToken, getToken } from '../api/client';

export interface Me {
  id: number;
  email: string;
  name: string;
  role: 'ADMIN' | 'PROVIDER' | 'CANDIDATE';
}

interface AuthCtx {
  me: Me | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<Me>;
  logout: () => void;
  refresh: () => Promise<void>;
}

const Ctx = createContext<AuthCtx>(null!);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState<boolean>(() => !!getToken());

  const refresh = useCallback(async () => {
    if (!getToken()) return;
    try {
      const d = await apiGet<any>('/auth/me');
      setMe(d.user ?? d);
    } catch {
      setToken(null);
      setMe(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const login = useCallback(async (email: string, password: string) => {
    const d = await apiPost<any>('/auth/login', { email, password });
    setToken(d.token);
    setMe(d.user);
    return d.user as Me;
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setMe(null);
    window.location.href = '/login';
  }, []);

  return <Ctx.Provider value={{ me, loading, login, logout, refresh }}>{children}</Ctx.Provider>;
}

export const useAuth = () => useContext(Ctx);
