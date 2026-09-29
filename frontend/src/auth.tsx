import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler } from './api';
import type { Me } from './types';

interface AuthState {
  /** undefined while loading, null when signed out */
  me: Me | null | undefined;
  setMe: (me: Me | null) => void;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null | undefined>(undefined);

  const refresh = useCallback(async () => {
    try {
      setMe(await api.get<Me>('/api/auth/me'));
    } catch {
      setMe(null);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout');
    } finally {
      setMe(null);
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null));
    void refresh();
  }, [refresh]);

  const value = useMemo(() => ({ me, setMe, refresh, logout }), [me, refresh, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}

/** The signed-in user; only for pages behind the login guard. */
export function useMe(): Me {
  const { me } = useAuth();
  if (!me) throw new Error('useMe without a signed-in user');
  return me;
}
