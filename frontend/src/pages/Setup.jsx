import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import AuthLayout from '../components/AuthLayout.jsx';

/** Fresh install: create the first administrator. Only offered while the database has no admin. */
export default function Setup() {
  const { user, loading, loginWithPassword } = useAuth();
  const navigate = useNavigate();
  const [required, setRequired] = useState(null);
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.setupStatus().then((s) => setRequired(s.setupRequired)).catch(() => setRequired(false));
  }, []);

  if (!loading && user) return <Navigate to="/" replace />;
  if (required === false) return <Navigate to="/login" replace />;
  if (required === null) return <div className="loading full-page">Loading…</div>;

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
      await api.setupAdmin(form.fullName, form.email, form.password);
      await loginWithPassword(form.email, form.password);
      navigate('/', { replace: true });
    } catch (err) {
      setErrors(err.fieldErrors || {});
      setError(err.status === 400 ? '' : err.message);
    } finally {
      setBusy(false);
    }
  }

  const field = (k, label, props) => (
    <label className={`field ${errors[k] ? 'has-error' : ''}`}>
      <span>{label}</span>
      <input value={form[k]} onChange={set(k)} required {...props} />
      {errors[k] && <small className="error-text">{errors[k]}</small>}
    </label>
  );

  return (
    <AuthLayout title="Welcome! Set up SolarSales" subtitle="Create the administrator account. This is only asked once.">
      <form onSubmit={submit} className="auth-form" noValidate>
        {error && <div className="alert alert-error">{error}</div>}
        {field('fullName', 'Full name', { autoComplete: 'name', autoFocus: true })}
        {field('email', 'Email', { type: 'email', autoComplete: 'email' })}
        {field('password', 'Password', { type: 'password', autoComplete: 'new-password' })}
        {!errors.password && <small className="muted hint">At least 8 characters, with a letter and a number.</small>}
        {field('confirm', 'Confirm password', { type: 'password', autoComplete: 'new-password' })}
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating…' : 'Create administrator'}</button>
      </form>
    </AuthLayout>
  );
}
