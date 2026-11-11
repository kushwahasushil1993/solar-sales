import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api } from '../api.js';
import AuthLayout from '../components/AuthLayout.jsx';

/** Asks for the email and sends a reset link. Shows the same answer whether or not the account exists. */
export default function ForgotPassword() {
  const { state } = useLocation();
  const [email, setEmail] = useState(state?.email && state.email.includes('@') ? state.email : '');
  const [sent, setSent] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await api.forgotPassword(email);
      setSent(res.message);
    } catch (err) {
      setError(err.fieldErrors?.email || err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      title="Forgot your password?"
      subtitle="Enter your account email and we'll send you a link to choose a new password."
      footer={<>Remembered it? <Link to="/login" className="link">Back to sign in</Link></>}
    >
      {sent ? (
        <div className="auth-form">
          <div className="alert alert-success">{sent}</div>
          <p className="muted small">
            The link expires in 30 minutes and works once. Check your spam folder if it doesn't arrive.
            Your current password keeps working until you set a new one.
          </p>
          <button className="btn btn-secondary btn-block" onClick={() => setSent('')}>Send another link</button>
        </div>
      ) : (
        <form onSubmit={submit} className="auth-form" noValidate>
          {error && <div className="alert alert-error">{error}</div>}
          <label className="field">
            <span>Email</span>
            <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy || !email.includes('@')}>
            {busy ? 'Sending…' : 'Email me a reset link'}
          </button>
        </form>
      )}
    </AuthLayout>
  );
}
