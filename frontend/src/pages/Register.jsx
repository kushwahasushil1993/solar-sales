import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import AuthLayout from '../components/AuthLayout.jsx';
import OAuthButtons from '../components/OAuthButtons.jsx';
import OtpStep from '../components/OtpStep.jsx';
import RoleChoice from '../components/RoleChoice.jsx';
import { useToast } from '../components/Toast.jsx';
import { identifierKind, looksLikeFullPhone } from '../format.js';

/** Register with email + password, or with a mobile number verified by SMS code (no password). */
export default function Register() {
  const { user, loading, register, loginWithOtp } = useAuth();
  const notify = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', identifier: '', password: '', confirm: '' });
  const [requestedRole, setRequestedRole] = useState('USER');
  const [sent, setSent] = useState(null);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (!loading && user) return <Navigate to="/" replace />;

  const kind = identifierKind(form.identifier);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError('');
    setErrors({});
    if (kind === 'email' && !form.fullName.trim()) {
      setErrors({ fullName: 'Full name is required' });
      return;
    }
    if (kind === 'email' && form.password !== form.confirm) {
      setErrors({ confirm: 'Passwords do not match' });
      return;
    }
    setBusy(true);
    try {
      if (kind === 'phone') {
        setSent(await api.requestOtp(form.identifier));
      } else {
        await register(form.fullName, form.identifier, form.password, requestedRole);
        navigate('/', { replace: true });
      }
    } catch (err) {
      const fe = { ...err.fieldErrors };
      // backend field names → this form's fields
      if (fe.email) fe.identifier = fe.email;
      if (fe.phone) fe.identifier = fe.phone;
      setErrors(fe);
      setError(err.status === 400 && Object.keys(fe).length ? '' : err.message);
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <AuthLayout title="Verify your number" subtitle="Enter the code we sent by SMS.">
        <OtpStep
          sent={sent}
          verifyLabel="Verify & continue"
          onVerify={async (otp) => {
            const result = await loginWithOtp(sent.phone, otp);
            if (result.needsRegistration) {
              navigate('/register/details', {
                replace: true,
                state: { maskedPhone: result.maskedPhone, fullName: form.fullName, requestedRole },
              });
            } else {
              notify('You already have an account with this number, so we signed you in.');
              navigate('/', { replace: true });
            }
          }}
          onResend={async () => setSent(await api.requestOtp(sent.phone))}
          onChangeNumber={() => { setSent(null); setError(''); }}
        />
      </AuthLayout>
    );
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
      subtitle="Join the sales team or as an installation agent. An admin approves new accounts."
      footer={<>Already have an account? <Link to="/login" className="link">Sign in</Link></>}
    >
      <form onSubmit={submit} className="auth-form" noValidate>
        {error && <div className="alert alert-error">{error}</div>}
        <RoleChoice value={requestedRole} onChange={setRequestedRole} />
        {field('identifier', 'Email or mobile number', { autoComplete: 'username', placeholder: 'you@example.com or 98765 43210', autoFocus: true })}
        {kind === 'phone' && !errors.identifier && (
          <small className="muted hint">We'll text you a 6-digit code, then ask for your name. No password needed.</small>
        )}
        {kind === 'email' && (
          <>
            {field('fullName', 'Full name', { autoComplete: 'name' })}
            {field('password', 'Password', { type: 'password', autoComplete: 'new-password' })}
            {!errors.password && <small className="muted hint">At least 8 characters, with a letter and a number.</small>}
            {field('confirm', 'Confirm password', { type: 'password', autoComplete: 'new-password' })}
          </>
        )}
        {kind === 'phone'
          ? (
            <button className="btn btn-primary btn-block" disabled={busy || !looksLikeFullPhone(form.identifier)}>
              {busy ? 'Sending code…' : 'Send verification code'}
            </button>
          )
          : (
            <button className="btn btn-primary btn-block" disabled={busy || kind !== 'email'}>
              {busy ? 'Creating account…' : 'Create account'}
            </button>
          )}
        <OAuthButtons />
      </form>
    </AuthLayout>
  );
}
