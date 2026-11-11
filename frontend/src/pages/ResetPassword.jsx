import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import AuthLayout from '../components/AuthLayout.jsx';

/** Opened from the reset email: choose a new password. Afterwards every session is signed out. */
export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [check, setCheck] = useState(null); // { valid, email }
  const [form, setForm] = useState({ password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) {
      setCheck({ valid: false });
      return;
    }
    api.checkResetToken(token).then(setCheck).catch(() => setCheck({ valid: false }));
  }, [token]);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (form.password !== form.confirm) {
      setErrors({ confirm: 'Passwords do not match' });
      return;
    }
    setErrors({});
    setBusy(true);
    try {
      await api.resetPassword(token, form.password);
      if (user) await logout(); // this browser's session ended too
      navigate('/login', { replace: true, state: { message: 'Password changed. Sign in with your new password.' } });
    } catch (err) {
      const fe = err.fieldErrors || {};
      if (fe.token) {
        setCheck({ valid: false });
      } else {
        setErrors({ password: fe.newPassword });
        setError(fe.newPassword ? '' : err.message);
      }
    } finally {
      setBusy(false);
    }
  }

  if (check === null) return <div className="loading full-page">Checking your link…</div>;

  if (!check.valid) {
    return (
      <AuthLayout title="Link expired" subtitle="This password reset link is invalid, already used, or older than 30 minutes.">
        <div className="auth-form">
          <Link to="/forgot-password" className="btn btn-primary btn-block">Send a new link</Link>
          <Link to="/login" className="link center">Back to sign in</Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Choose a new password" subtitle={`For ${check.email}. You'll be signed out everywhere and can then sign in with it.`}>
      <form onSubmit={submit} className="auth-form" noValidate>
        {error && <div className="alert alert-error">{error}</div>}
        <label className={`field ${errors.password ? 'has-error' : ''}`}>
          <span>New password</span>
          <input type="password" autoComplete="new-password" value={form.password} onChange={set('password')} required autoFocus />
          {errors.password && <small className="error-text">{errors.password}</small>}
        </label>
        {!errors.password && <small className="muted hint">At least 8 characters, with a letter and a number.</small>}
        <label className={`field ${errors.confirm ? 'has-error' : ''}`}>
          <span>Confirm new password</span>
          <input type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} required />
          {errors.confirm && <small className="error-text">{errors.confirm}</small>}
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Saving…' : 'Set new password'}</button>
      </form>
    </AuthLayout>
  );
}
