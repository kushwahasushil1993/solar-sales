import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import { dateTime } from '../format.js';

const ICONS = {
  TASK_ASSIGNED: '📋', TASK_UNASSIGNED: '↩', TASK_ACCEPTED: '✅', TASK_PICKED: '🙋', TASK_STATUS_UPDATED: '⚡',
  ACCOUNT_PENDING: '🆕',
};

/** In-app notifications. For agents, opening a "New task assigned" notification accepts the task. */
export default function Inbox() {
  const { user } = useAuth();
  const notify = useToast();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(() => {
    api.inbox().then((d) => { setData(d); setError(''); }).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  const changed = () => window.dispatchEvent(new Event('inbox-changed'));

  async function open(n) {
    setBusyId(n.id);
    try {
      const result = await api.openNotification(n.id);
      changed();
      if (result.accepted) {
        notify(`Task accepted: ${result.task.customerName}`);
        navigate('/tasks');
        return;
      }
      if (n.type === 'ACCOUNT_PENDING') {
        navigate('/users');
        return;
      }
      if (n.type === 'TASK_ASSIGNED' && result.message) notify(result.message, 'error');
      load();
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      setBusyId(null);
    }
  }

  async function readAll() {
    await api.readAllNotifications();
    changed();
    load();
  }

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!data) return <div className="loading">Loading notifications…</div>;

  return (
    <div className="stack">
      <section className="card toolbar">
        <span className="muted">
          {data.unread ? `${data.unread} unread` : 'All caught up'}
          {user.role === 'AGENT' && ' · Click a "New task assigned" notification to accept the task.'}
        </span>
        <div className="toolbar-spacer" />
        {data.unread > 0 && <button className="btn btn-secondary" onClick={readAll}>Mark all as read</button>}
      </section>

      <section className="card no-pad">
        {data.items.length === 0 && <p className="empty">No notifications yet.</p>}
        <ul className="inbox-list">
          {data.items.map((n) => (
            <li key={n.id}>
              <button className={`inbox-item ${n.read ? '' : 'unread'}`} disabled={busyId === n.id} onClick={() => open(n)}>
                <span className="inbox-icon" aria-hidden="true">{ICONS[n.type] || '🔔'}</span>
                <span className="inbox-body">
                  <span className="inbox-title">
                    {n.title}
                    {n.type === 'TASK_ASSIGNED' && !n.read && user.role === 'AGENT' && <span className="tag">click to accept</span>}
                  </span>
                  <span className="inbox-message">{n.message}</span>
                  <span className="muted small">{dateTime(n.createdAt)}</span>
                </span>
                {!n.read && <span className="unread-dot" aria-label="unread" />}
              </button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
