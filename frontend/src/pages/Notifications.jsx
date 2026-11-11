import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import Badge from '../components/Badge.jsx';
import Pagination from '../components/Pagination.jsx';
import { useToast } from '../components/Toast.jsx';
import { dateTime } from '../format.js';

/** Admin: every invoice / installation workflow email sent to customers. */
export default function Notifications() {
  const notify = useToast();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(() => {
    api.notifications({ page, size }).then((d) => { setData(d); setError(''); }).catch((e) => setError(e.message));
  }, [page, size]);
  useEffect(load, [load]);

  async function resend(n) {
    setBusyId(n.id);
    try {
      const r = await api.resendNotification(n.id);
      notify(r.status === 'SENT' ? `${r.typeLabel} email sent to ${r.recipient}` : `${r.typeLabel}: ${r.detail}`,
        r.status === 'SENT' ? 'success' : 'error');
      load();
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      setBusyId(null);
    }
  }

  const counts = (data?.content || []).reduce((acc, n) => ({ ...acc, [n.status]: (acc[n.status] || 0) + 1 }), {});

  return (
    <div className="stack">
      <section className="card info-card">
        <strong>Automatic customer emails.</strong>{' '}
        Invoice when a sale is recorded → <em>Installation scheduled</em> → <em>In progress</em> → <em>Installation completed</em>.
        Any change to the amounts sends the full, updated invoice (<em>Payment received</em> or <em>Updated invoice</em>). Excel imports only email invoices when you tick the box on the upload page.
        {(counts.SKIPPED || counts.FAILED) ? (
          <span className="muted"> Skipped or failed emails show the reason and can be resent.</span>
        ) : null}
      </section>

      {error && <div className="alert alert-error">{error}</div>}

      <section className="card no-pad">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>When</th><th>Invoice</th><th>Customer</th><th>Email</th><th>Sent to</th><th>Status</th><th>By</th><th /></tr>
            </thead>
            <tbody>
              {!data && <tr><td colSpan={8} className="empty">Loading…</td></tr>}
              {data?.content.map((n) => (
                <tr key={n.id}>
                  <td className="small nowrap">{dateTime(n.createdAt)}</td>
                  <td className="mono">{n.invoiceNumber}</td>
                  <td>{n.customerName}</td>
                  <td>
                    <div>{n.typeLabel}</div>
                    <div className="muted small">{n.subject}</div>
                  </td>
                  <td className="small">{n.recipient || <span className="muted">no email</span>}</td>
                  <td>
                    <Badge value={n.status} />
                    {n.detail && <div className="muted small detail">{n.detail}</div>}
                  </td>
                  <td className="small">{n.triggeredBy || '—'}</td>
                  <td className="actions">
                    {n.status !== 'SENT' && n.recipient && (
                      <button className="btn btn-ghost" disabled={busyId === n.id} onClick={() => resend(n)}>Resend</button>
                    )}
                  </td>
                </tr>
              ))}
              {data && data.content.length === 0 && (
                <tr><td colSpan={8} className="empty">No customer emails yet. They appear here as sales move through the workflow.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {data && (
          <Pagination page={data.page} size={size} totalPages={data.totalPages} totalElements={data.totalElements}
                      onPage={setPage} onSize={(s) => { setSize(s); setPage(0); }} />
        )}
      </section>
    </div>
  );
}
