import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import AuthLayout from '../components/AuthLayout.jsx';
import OAuthButtons from '../components/OAuthButtons.jsx';
import OtpStep from '../components/OtpStep.jsx';
import { identifierKind, looksLikeFullPhone } from '../format.js';

// error codes the backend puts in /login?error=... after a Google/GitHub sign-in
const OAUTH_ERRORS = {
  email_not_verified: 'Your account has no verified email address. Verify it with the provider, then try again.',
  account_disabled: 'This account has been disabled. Contact an administrator.',
  access_denied: 'Sign-in was cancelled.',
  provider_error: 'The sign-in provider could not be reached. Please try again.',
};

/** One field for email or mobile number: email → password, mobile → SMS code. */
export default function Login() {
  const { user, loading, expired, loginWithPassword, loginWithOtp } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [sent, setSent] = useState(null); // OTP sent → show the code step
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [setupRequired, setSetupRequired] = useState(false);

  useEffect(() => {
    // a fresh install has no administrator yet: send the first visitor to setup
    api.setupStatus().then((s) => setSetupRequired(s.setupRequired)).catch(() => {});
  }, []);

  if (!loading && user) return <Navigate to="/" replace />;
  if (setupRequired) return <Navigate to="/setup" replace />;

  const kind = identifierKind(identifier);
  const code = params.get('error');
  const shownError = error || (code ? OAUTH_ERRORS[code] || 'Sign-in failed. Please try again.' : '');
  const done = () => navigate(location.state?.from || '/', { replace: true });

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (kind === 'phone') {
        setSent(await api.requestOtp(identifier));
      } else {
        await loginWithPassword(identifier, password);
        done();
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <AuthLayout title="Enter your code" subtitle="Check your phone for an SMS from us.">
        <OtpStep
          sent={sent}
          verifyLabel="Verify & continue"
          onVerify={async (otp) => {
            const result = await loginWithOtp(sent.phone, otp);
            if (result.needsRegistration) {
              navigate('/register/details', { replace: true, state: { maskedPhone: result.maskedPhone } });
            } else {
              done();
            }
          }}
          onResend={async () => setSent(await api.requestOtp(sent.phone))}
          onChangeNumber={() => { setSent(null); setError(''); }}
        />
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Use your email and password, or your mobile number for a one-time code."
      footer={<>New here? <Link to="/register" className="link">Create an account</Link></>}
    >
      <form onSubmit={submit} className="auth-form">
        {location.state?.message && !shownError && <div className="alert alert-success">{location.state.message}</div>}
        {expired && !shownError && !location.state?.message && (
          <div className="alert alert-info">Your session has ended. Please sign in again.</div>
        )}
        {shownError && <div className="alert alert-error">{shownError}</div>}
        <label className="field">
          <span>Email or mobile number</span>
          <input
            autoComplete="username"
            placeholder="you@example.com or 98765 43210"
            value={identifier}
            onChange={(e) => { setIdentifier(e.target.value); setError(''); }}
            required
            autoFocus
          />
        </label>
        {kind === 'email' && (
          <label className="field">
            <span className="field-row">
              Password
              <Link to="/forgot-password" state={{ email: identifier }} className="link small">Forgot password?</Link>
            </span>
            <input type="password" autoComplete="current-password" value={password}
                   onChange={(e) => setPassword(e.target.value)} required />
          </label>
        )}
        {kind === 'phone'
          ? (
            <button className="btn btn-primary btn-block" disabled={busy || !looksLikeFullPhone(identifier)}>
              {busy ? 'Sending code…' : 'Send code by SMS'}
            </button>
          )
          : (
            <button className="btn btn-primary btn-block" disabled={busy || kind !== 'email'}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          )}
        <OAuthButtons />
      </form>
    </AuthLayout>
  );
}
