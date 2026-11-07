import { useState } from 'react';
import Modal from './Modal.jsx';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { INSTALLATION_STATUSES, PANEL_TYPES, money, titleCase } from '../format.js';

// local date, not toISOString(): in IST that would give yesterday before 05:30
const today = () => new Date().toLocaleDateString('en-CA');

const EMPTY = {
  invoiceNumber: '', purchaseDate: today(), customerName: '', phone: '', email: '',
  city: '', state: '', panelType: 'Monocrystalline', systemCapacityKw: '', panelCount: '', inverterBrand: '',
  totalAmount: '', amountPaid: '', installationStatus: 'PENDING', installationDate: '', salesperson: '',
};

const toForm = (sale) =>
  Object.fromEntries(Object.keys(EMPTY).map((k) => [k, sale?.[k] ?? (sale ? '' : EMPTY[k])]));

function toPayload(form) {
  const p = { ...form };
  Object.keys(p).forEach((k) => p[k] === '' && (p[k] = null));
  ['systemCapacityKw', 'totalAmount', 'amountPaid'].forEach((k) => p[k] != null && (p[k] = Number(p[k])));
  if (p.panelCount != null) p.panelCount = parseInt(p.panelCount, 10);
  return p;
}

export default function SaleFormModal({ sale, onClose, onSaved }) {
  const { isAdmin } = useAuth();
  const [form, setForm] = useState(() => toForm(sale));
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const balance = Math.max(0, (Number(form.totalAmount) || 0) - (Number(form.amountPaid) || 0));

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setErrors({});
    setFormError('');
    try {
      const saved = sale ? await api.updateSale(sale.id, toPayload(form)) : await api.createSale(toPayload(form));
      onSaved(saved);
    } catch (err) {
      const fe = { ...err.fieldErrors };
      // cross-field checks come back keyed by their method name
      if (fe.amountPaidWithinTotal) fe.amountPaid = fe.amountPaidWithinTotal;
      if (fe.installationAfterPurchase) fe.installationDate = fe.installationAfterPurchase;
      setErrors(fe);
      setFormError(err.status === 400 ? 'Please fix the highlighted fields.' : err.message);
    } finally {
      setSaving(false);
    }
  }

  const field = (k, label, props = {}) => (
    <label className={`field ${errors[k] ? 'has-error' : ''}`}>
      <span>{label}{props.required && <em> *</em>}</span>
      <input value={form[k]} onChange={set(k)} {...props} />
      {errors[k] && <small className="error-text">{errors[k]}</small>}
    </label>
  );

  return (
    <Modal
      wide
      title={sale ? `Edit sale ${sale.invoiceNumber}` : 'Add solar sale'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" form="sale-form" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : sale ? 'Save changes' : 'Add sale'}
          </button>
        </>
      }
    >
      <form id="sale-form" onSubmit={submit} noValidate>
        {formError && <div className="alert alert-error">{formError}</div>}

        <fieldset>
          <legend>Purchase</legend>
          <div className="grid-3">
            {field('invoiceNumber', 'Invoice No', { required: true, placeholder: 'INV-1001' })}
            {field('purchaseDate', 'Purchase date', { required: true, type: 'date' })}
            {field('salesperson', 'Salesperson')}
          </div>
        </fieldset>

        <fieldset>
          <legend>Customer</legend>
          <div className="grid-3">
            {field('customerName', 'Customer name', { required: true })}
            {field('phone', 'Phone', { type: 'tel' })}
            {field('email', 'Email', { type: 'email' })}
            {field('city', 'City')}
            {field('state', 'State')}
          </div>
        </fieldset>

        <fieldset>
          <legend>System</legend>
          <div className="grid-3">
            <label className="field">
              <span>Panel type</span>
              <input list="panel-types" value={form.panelType} onChange={set('panelType')} />
              <datalist id="panel-types">
                {PANEL_TYPES.map((p) => <option key={p} value={p} />)}
              </datalist>
            </label>
            {field('systemCapacityKw', 'Capacity (kW)', { required: true, type: 'number', step: '0.01', min: '0' })}
            {field('panelCount', 'Panel count', { type: 'number', min: '0' })}
            {field('inverterBrand', 'Inverter brand')}
            {isAdmin && (
              <>
                <label className="field">
                  <span>Installation status</span>
                  <select value={form.installationStatus} onChange={set('installationStatus')}>
                    {INSTALLATION_STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
                  </select>
                </label>
                {field('installationDate', 'Installation date', { type: 'date' })}
              </>
            )}
          </div>
          {!isAdmin && (
            <p className="muted small">The installation starts as Pending; an admin schedules it. Track it on the Installation Status page.</p>
          )}
        </fieldset>

        <fieldset>
          <legend>Payment</legend>
          <div className="grid-3">
            {field('totalAmount', 'Total amount (₹)', { required: true, type: 'number', min: '0' })}
            {field('amountPaid', 'Amount paid (₹)', { type: 'number', min: '0' })}
            <div className="field">
              <span>Balance due</span>
              <div className="readonly">{money(balance)}</div>
            </div>
          </div>
        </fieldset>
      </form>
    </Modal>
  );
}
