import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import AuthLayout from '../components/AuthLayout.jsx';

export default function Register() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

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
      await register(form.fullName, form.email, form.password);
      navigate('/', { replace: true });
    } catch (err) {
      setErrors(err.fieldErrors);
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
    <AuthLayout
      title="Create account"
      subtitle="Sales team accounts can add sales and track installations."
      footer={<>Already have an account? <Link to="/login" className="link">Sign in</Link></>}
    >
      <form onSubmit={submit} className="auth-form" noValidate>
        {error && <div className="alert alert-error">{error}</div>}
        {field('fullName', 'Full name', { autoComplete: 'name', autoFocus: true })}
        {field('email', 'Email', { type: 'email', autoComplete: 'email' })}
        {field('password', 'Password', { type: 'password', autoComplete: 'new-password' })}
        {!errors.password && <small className="muted hint">At least 8 characters, with a letter and a number.</small>}
        {field('confirm', 'Confirm password', { type: 'password', autoComplete: 'new-password' })}
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
      </form>
    </AuthLayout>
  );
}
