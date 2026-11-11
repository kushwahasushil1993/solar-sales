import { useEffect, useRef, useState } from 'react';

/**
 * Second step of mobile sign-in/registration: enter the 6-digit SMS code.
 * `sent` is the backend's reply ({ phone, maskedPhone, resendInSeconds }).
 */
export default function OtpStep({ sent, onVerify, onResend, onChangeNumber, verifyLabel = 'Verify & sign in' }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [wait, setWait] = useState(sent.resendInSeconds);
  const inputRef = useRef(null);

  useEffect(() => {
    setWait(sent.resendInSeconds);
    setCode('');
    inputRef.current?.focus();
  }, [sent]);

  useEffect(() => {
    if (wait <= 0) return undefined;
    const t = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(t);
  }, [wait]);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onVerify(code);
    } catch (err) {
      setError(err.message);
      setCode('');
      inputRef.current?.focus();
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setError('');
    try {
      await onResend();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <form onSubmit={submit} className="auth-form">
      <div className="alert alert-info">
        We sent a 6-digit code to <strong>{sent.maskedPhone}</strong>. It expires in {Math.round(sent.expiresInSeconds / 60)} minutes.
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      <label className="field">
        <span>Verification code</span>
        <input
          ref={inputRef}
          className="otp-input"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          placeholder="••••••"
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          required
        />
      </label>
      <button className="btn btn-primary btn-block" disabled={busy || code.length !== 6}>
        {busy ? 'Verifying…' : verifyLabel}
      </button>
      <div className="otp-links">
        <button type="button" className="link-btn" onClick={onChangeNumber}>← Change number</button>
        {wait > 0
          ? <span className="muted small">Resend code in {wait}s</span>
          : <button type="button" className="link-btn" onClick={resend}>Resend code</button>}
      </div>
    </form>
  );
}
