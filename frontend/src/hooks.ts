import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api';

export interface Resource<T> {
  data: T | undefined;
  error: string | null;
  loading: boolean;
  reload: () => Promise<void>;
}

/**
 * Loads a GET endpoint and keeps it fresh: reloads when the path changes and, with
 * pollMs, every few seconds while the tab is visible (new orders and messages show up
 * without a refresh). Pass null to skip loading.
 */
export function useResource<T>(path: string | null, pollMs?: number): Resource<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(path !== null);
  const latestPath = useRef(path);
  latestPath.current = path;

  const load = useCallback(async (showSpinner: boolean) => {
    if (path === null) return;
    if (showSpinner) setLoading(true);
    try {
      const result = await api.get<T>(path);
      if (latestPath.current === path) {
        setData(result);
        setError(null);
      }
    } catch (e) {
      if (latestPath.current === path) setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (latestPath.current === path) setLoading(false);
    }
  }, [path]);

  useEffect(() => {
    void load(true);
    if (!pollMs || path === null) return;
    const id = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load(false);
    }, pollMs);
    return () => window.clearInterval(id);
  }, [load, pollMs, path]);

  const reload = useCallback(() => load(false), [load]);
  return { data, error, loading, reload };
}
