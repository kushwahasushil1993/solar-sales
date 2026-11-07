import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api, configureAuth } from './api.js';

const STORAGE_KEY = 'solar-sales-auth';
const AuthContext = createContext(null);

function loadStored() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (saved?.token && new Date(saved.expiresAt) > new Date()) return saved;
  } catch {
    /* storage unavailable or corrupt */
  }
  return null;
}

function store(session) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore: session just won't survive a reload */
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => {
    const saved = loadStored();
    configureAuth(saved?.token ?? null);
    return saved;
  });
  const [expired, setExpired] = useState(false);

  const logout = useCallback(() => {
    store(null);
    configureAuth(null);
    setSession(null);
  }, []);

  const start = useCallback((auth) => {
    const next = { token: auth.token, expiresAt: auth.expiresAt, user: auth.user };
    store(next);
    configureAuth(next.token);
    setExpired(false);
    setSession(next);
    return next.user;
  }, []);

  useEffect(() => {
    configureAuth(session?.token ?? null, () => {
      setExpired(true);
      logout();
    });
  }, [session, logout]);

  // refresh the profile on load: an admin may have changed this user's role since the last visit
  useEffect(() => {
    if (!session) return;
    api.me()
      .then((user) => setSession((s) => {
        if (!s) return s;
        const next = { ...s, user };
        store(next);
        return next;
      }))
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const value = useMemo(() => ({
    user: session?.user ?? null,
    isAdmin: session?.user?.role === 'ADMIN',
    expired,
    login: async (email, password) => start(await api.login(email, password)),
    register: async (fullName, email, password) => start(await api.register(fullName, email, password)),
    logout,
  }), [session, expired, start, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

export function RequireAuth({ children }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}

export function RequireAdmin({ children }) {
  const { isAdmin } = useAuth();
  if (!isAdmin) {
    return (
      <div className="card denied">
        <div className="denied-icon">🔒</div>
        <h3>Admins only</h3>
        <p className="muted">Ask an administrator for access to this page.</p>
      </div>
    );
  }
  return children;
}
