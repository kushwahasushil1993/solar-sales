import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import AuthLayout from '../components/AuthLayout.jsx';
import RoleChoice from '../components/RoleChoice.jsx';

/** After a new mobile number is verified by SMS code: basic details, then the account is created. */
export default function CompleteProfile() {
  const { user, loading, completePhoneRegistration } = useAuth();
  const navigate = useNavigate();
  const { state } = useLocation();
  const [form, setForm] = useState({ fullName: state?.fullName || '', email: '' });
  const [requestedRole, setRequestedRole] = useState(state?.requestedRole || 'USER');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (!loading && user) return <Navigate to="/" replace />;

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError('');
    setErrors({});
    if (!form.fullName.trim()) {
      setErrors({ fullName: 'Full name is required' });
      return;
    }
    setBusy(true);
    try {
      await completePhoneRegistration(form.fullName, form.email, requestedRole);
      navigate('/', { replace: true });
    } catch (err) {
      setErrors(err.fieldErrors);
      // the verified number is kept for 10 minutes; after that the user has to verify again
      setError(err.status === 401 ? 'expired' : err.fieldErrors && Object.keys(err.fieldErrors).length ? '' : err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      title="Complete your profile"
      subtitle="Your number is verified. Tell us a little about you to finish creating your account."
      footer={<>Wrong number? <Link to="/login" className="link">Start again</Link></>}
    >
      <form onSubmit={submit} className="auth-form" noValidate>
        {state?.maskedPhone && (
          <div className="alert alert-success">✓ Mobile number <strong>{state.maskedPhone}</strong> verified</div>
        )}
        {error === 'expired' && (
          <div className="alert alert-error">
            Your verification has expired. <Link to="/login" className="link">Verify your number again</Link>.
          </div>
        )}
        {error && error !== 'expired' && <div className="alert alert-error">{error}</div>}
        <RoleChoice value={requestedRole} onChange={setRequestedRole} />
        <label className={`field ${errors.fullName ? 'has-error' : ''}`}>
          <span>Full name</span>
          <input value={form.fullName} onChange={set('fullName')} autoComplete="name" required autoFocus />
          {errors.fullName && <small className="error-text">{errors.fullName}</small>}
        </label>
        <label className={`field ${errors.email ? 'has-error' : ''}`}>
          <span>Email <em className="optional">(optional)</em></span>
          <input type="email" value={form.email} onChange={set('email')} autoComplete="email" placeholder="you@example.com" />
          {errors.email && <small className="error-text">{errors.email}</small>}
        </label>
        <small className="muted hint">You'll sign in with your mobile number and a code. No password needed.</small>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
      </form>
    </AuthLayout>
  );
}
