import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, setUnauthorizedHandler } from './api.js';
import PendingApproval from './pages/PendingApproval.jsx';

const AuthContext = createContext(null);
const RETURN_KEY = 'solar-sales-return-to';

/** Remember where to go after the OAuth round trip (the backend always lands on "/"). */
export function rememberReturnPath(path) {
  try {
    if (path && path !== '/') sessionStorage.setItem(RETURN_KEY, path);
  } catch {
    /* storage unavailable: user lands on the dashboard instead */
  }
}

function takeReturnPath() {
  try {
    const path = sessionStorage.getItem(RETURN_KEY);
    sessionStorage.removeItem(RETURN_KEY);
    return path;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setExpired(true);
      setUser(null);
    });
    // the session lives in an HttpOnly cookie; asking the backend is the only way to know who is signed in
    api.me()
      .then((me) => {
        setUser(me);
        const back = takeReturnPath();
        if (back) navigate(back, { replace: true });
      })
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // email + password: the backend sets the session cookie, then we load the profile
  const loginWithPassword = useCallback(async (email, password) => {
    await api.login(email, password);
    const me = await api.me();
    setExpired(false);
    setUser(me);
    return me;
  }, []);

  const loadMe = useCallback(async () => {
    const me = await api.me();
    setExpired(false);
    setUser(me);
    return me;
  }, []);

  /**
   * Mobile number + SMS code. Existing account → signed in, returns { user }.
   * New number → returns { needsRegistration: true, phone, maskedPhone }; finish with completePhoneRegistration.
   */
  const loginWithOtp = useCallback(async (phone, code) => {
    const result = await api.verifyOtp(phone, code);
    if (result?.status === 'REGISTRATION_REQUIRED') {
      return { needsRegistration: true, phone: result.phone, maskedPhone: result.maskedPhone };
    }
    return { user: await loadMe() };
  }, [loadMe]);

  const completePhoneRegistration = useCallback(async (fullName, email, requestedRole) => {
    await api.completePhoneRegistration(fullName, email, requestedRole);
    return loadMe();
  }, [loadMe]);

  const register = useCallback(async (fullName, email, password, requestedRole) => {
    await api.register(fullName, email, password, requestedRole);
    return loginWithPassword(email, password);
  }, [loginWithPassword]);

  // re-read the profile, e.g. to notice that an admin approved this account
  const refreshUser = useCallback(async () => {
    const me = await api.me();
    setUser(me);
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      /* session already gone */
    }
    setExpired(false);
    setUser(null);
  }, []);

  const value = useMemo(() => ({
    user,
    loading,
    isAdmin: user?.role === 'ADMIN',
    expired,
    loginWithPassword,
    loginWithOtp,
    completePhoneRegistration,
    register,
    refreshUser,
    logout,
  }), [user, loading, expired, loginWithPassword, loginWithOtp, completePhoneRegistration, register, refreshUser, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

export function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <div className="loading full-page">Loading…</div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user.role === 'PENDING') return <PendingApproval />;
  return children;
}

/** Renders children only for the given roles; agents are sent to their task page, others see a notice. */
export function RequireRole({ roles, children }) {
  const { user } = useAuth();
  if (!roles.includes(user?.role)) {
    if (user?.role === 'AGENT') return <Navigate to="/tasks" replace />;
    return (
      <div className="card denied">
        <div className="denied-icon">🔒</div>
        <h3>Not available</h3>
        <p className="muted">Your account doesn't have access to this page.</p>
      </div>
    );
  }
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
