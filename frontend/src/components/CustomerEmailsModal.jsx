import { useEffect, useState } from 'react';
import { api } from '../api.js';
import Badge from './Badge.jsx';
import Modal from './Modal.jsx';
import { useToast } from './Toast.jsx';
import { dateTime } from '../format.js';

/** Admin: the emails a customer received for one sale, with "send invoice" and resend. */
export default function CustomerEmailsModal({ sale, onClose }) {
  const notify = useToast();
  const [items, setItems] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = () => api.saleNotifications(sale.id).then(setItems).catch((e) => notify(e.message, 'error'));
  useEffect(() => { load(); }, [sale.id]); // eslint-disable-line react-hooks/exhaustive-deps

  async function run(action, success) {
    setBusy(true);
    try {
      const result = await action();
      notify(result.status === 'SENT' ? success : `${result.typeLabel}: ${result.detail}`, result.status === 'SENT' ? 'success' : 'error');
      await load();
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      wide
      title={`Customer emails · ${sale.invoiceNumber}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose}>Close</button>
          <button
            className="btn btn-primary"
            disabled={busy || !sale.email}
            title={sale.email ? '' : 'Add the customer’s email to the sale first'}
            onClick={() => run(() => api.sendInvoice(sale.id), `Invoice emailed to ${sale.email}`)}
          >
            ✉ Send invoice now
          </button>
        </>
      }
    >
      <p className="muted small" style={{ marginBottom: 12 }}>
        {sale.email
          ? <>Emails go to <strong>{sale.email}</strong>. The customer is emailed automatically when the sale is created and when the installation is scheduled, started or completed. Any change to the amounts sends the full, updated invoice.</>
          : <>This customer has no email address, so emails are skipped. Edit the sale to add one.</>}
      </p>
      {!items && <div className="loading">Loading…</div>}
      {items?.length === 0 && <p className="muted">No emails yet.</p>}
      {items?.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>When</th><th>Email</th><th>Status</th><th>By</th><th /></tr></thead>
            <tbody>
              {items.map((n) => (
                <tr key={n.id}>
                  <td className="small nowrap">{dateTime(n.createdAt)}</td>
                  <td>
                    <div>{n.typeLabel}</div>
                    <div className="muted small">{n.subject}</div>
                  </td>
                  <td>
                    <Badge value={n.status} />
                    {n.detail && <div className="muted small">{n.detail}</div>}
                  </td>
                  <td className="small">{n.triggeredBy || '—'}</td>
                  <td className="actions">
                    {n.status !== 'SENT' && sale.email && (
                      <button className="btn btn-ghost" disabled={busy}
                              onClick={() => run(() => api.resendNotification(n.id), `${n.typeLabel} email sent`)}>
                        Resend
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  );
}
