import { useState } from 'react';
import Modal from './Modal.jsx';

/**
 * Collects a new password (twice) and, when `askCurrent`, the current one. `onSubmit(current, next)` saves it;
 * its errors (incl. fieldErrors) are shown in the form.
 */
export default function PasswordModal({ title, intro, askCurrent, submitLabel = 'Save password', onSubmit, onClose }) {
  const [form, setForm] = useState({ current: '', next: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (form.next !== form.confirm) {
      setErrors({ confirm: 'Passwords do not match' });
      return;
    }
    setErrors({});
    setSaving(true);
    try {
      await onSubmit(form.current, form.next);
    } catch (err) {
      const fe = err.fieldErrors || {};
      setErrors({ current: fe.currentPassword, next: fe.newPassword });
      setError(fe.currentPassword || fe.newPassword ? '' : err.message);
    } finally {
      setSaving(false);
    }
  }

  const field = (k, label, autoComplete) => (
    <label className={`field ${errors[k] ? 'has-error' : ''}`}>
      <span>{label}</span>
      <input type="password" value={form[k]} onChange={set(k)} autoComplete={autoComplete} required />
      {errors[k] && <small className="error-text">{errors[k]}</small>}
    </label>
  );

  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" form="password-form" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : submitLabel}
          </button>
        </>
      }
    >
      <form id="password-form" onSubmit={submit} className="stack-sm" noValidate>
        {intro && <p className="muted small">{intro}</p>}
        {error && <div className="alert alert-error">{error}</div>}
        {askCurrent && field('current', 'Current password', 'current-password')}
        {field('next', 'New password', 'new-password')}
        {!errors.next && <small className="muted hint">At least 8 characters, with a letter and a number.</small>}
        {field('confirm', 'Confirm new password', 'new-password')}
      </form>
    </Modal>
  );
}
