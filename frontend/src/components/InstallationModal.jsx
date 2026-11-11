import { useState } from 'react';
import { api } from '../api.js';
import Modal from './Modal.jsx';
import { INSTALLATION_STATUSES, titleCase } from '../format.js';

const today = () => new Date().toLocaleDateString('en-CA');

/**
 * Update only the installation progress of a sale. `save(body, notify)` defaults to the sales endpoint
 * (sales users and admins); agents pass their task endpoint.
 */
export default function InstallationModal({ sale, onClose, onSaved, save }) {
  const [status, setStatus] = useState(sale.installationStatus);
  const [date, setDate] = useState(sale.installationDate || '');
  const [notify, setNotify] = useState(true);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  function pickStatus(next) {
    setStatus(next);
    if (next === 'INSTALLED' && !date) setDate(today());
  }

  async function save(e) {
    e.preventDefault();
    setSaving(true);
    setErrors({});
    setError('');
    try {
      const body = { installationStatus: status, installationDate: date || null };
      const updated = save ? await save(body, notify) : await api.updateInstallation(sale.id, body, notify);
      onSaved(updated);
    } catch (err) {
      setErrors(err.fieldErrors);
      setError(Object.keys(err.fieldErrors || {}).length ? '' : err.message);
    } finally {
      setSaving(false);
    }
  }

  const changed = status !== sale.installationStatus;

  return (
    <Modal
      title={`Installation · ${sale.customerName}`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" form="installation-form" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      <form id="installation-form" onSubmit={save} className="stack-sm">
        <p className="muted small">{sale.invoiceNumber} · {sale.city || '—'}</p>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="status-picker" role="radiogroup" aria-label="Installation status">
          {INSTALLATION_STATUSES.map((s) => (
            <button
              type="button"
              key={s}
              role="radio"
              aria-checked={status === s}
              className={`status-option ${status === s ? 'selected' : ''}`}
              onClick={() => pickStatus(s)}
            >
              {titleCase(s)}
            </button>
          ))}
        </div>
        <label className={`field ${errors.installationDate ? 'has-error' : ''}`}>
          <span>{status === 'INSTALLED' ? 'Installed on' : 'Installation date'}</span>
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          {errors.installationDate && <small className="error-text">{errors.installationDate}</small>}
        </label>
        {'email' in sale ? (
          <label className={`checkbox ${sale.email ? '' : 'disabled'}`}>
            <input type="checkbox" checked={notify && !!sale.email && changed} disabled={!sale.email || !changed}
                   onChange={(e) => setNotify(e.target.checked)} />
            {sale.email
              ? (changed ? `Email ${sale.email} about this update` : 'Change the status to email the customer')
              : 'No customer email on this sale'}
          </label>
        ) : (
          <label className="checkbox">
            <input type="checkbox" checked={notify} onChange={(e) => setNotify(e.target.checked)} />
            Email the customer about the new status (if they have an email)
          </label>
        )}
      </form>
    </Modal>
  );
}
