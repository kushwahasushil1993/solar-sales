import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import PasswordModal from '../components/PasswordModal.jsx';
import ProviderIcon from '../components/ProviderIcon.jsx';
import { dateTime } from '../format.js';

const PROVIDER_NAMES = { google: 'Google', github: 'GitHub', password: 'Email & password', phone: 'Mobile OTP' };

export default function Users() {
  const { user: me } = useAuth();
  const notify = useToast();
  const [users, setUsers] = useState(null);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [resetting, setResetting] = useState(null);

  useEffect(() => {
    api.users().then(setUsers).catch((e) => setError(e.message));
  }, []);

  async function change(u, action) {
    setBusyId(u.id);
    try {
      const updated = await action();
      setUsers((list) => list.map((x) => (x.id === updated.id ? updated : x)));
      notify(`Updated ${updated.fullName}`);
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      setBusyId(null);
    }
  }

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!users) return <div className="loading">Loading users…</div>;

  const admins = users.filter((u) => u.role === 'ADMIN' && u.enabled).length;
  const pending = users.filter((u) => u.role === 'PENDING');
  const WANTS = { USER: 'Sales team', AGENT: 'Installation agent' };

  return (
    <div className="stack">
      {pending.length > 0 && (
        <section className="card pending-card">
          <div className="card-head">
            <h3>🆕 Waiting for approval ({pending.length})</h3>
            <span className="muted small">Choose what each new account can do</span>
          </div>
          <ul className="pending-list">
            {pending.map((u) => (
              <li key={u.id}>
                <div>
                  <strong>{u.fullName}</strong> <span className="muted small">{u.email || u.phone}</span>
                  <div className="muted small">
                    Signed up {dateTime(u.createdAt)}
                    {u.requestedRole && <> · asked to join as <strong>{WANTS[u.requestedRole]}</strong></>}
                  </div>
                </div>
                <div className="task-actions">
                  <button className={`btn btn-sm ${u.requestedRole === 'USER' ? 'btn-primary' : 'btn-secondary'}`} disabled={busyId === u.id}
                          onClick={() => change(u, () => api.setUserRole(u.id, 'USER'))}>Approve as Sales</button>
                  <button className={`btn btn-sm ${u.requestedRole === 'AGENT' ? 'btn-primary' : 'btn-secondary'}`} disabled={busyId === u.id}
                          onClick={() => change(u, () => api.setUserRole(u.id, 'AGENT'))}>Approve as Agent</button>
                  <button className="btn btn-ghost btn-sm danger-text" disabled={busyId === u.id}
                          onClick={() => change(u, () => api.setUserEnabled(u.id, false))}>Reject</button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="stats">
        <div className="card stat"><div className="stat-label">Accounts</div><div className="stat-value">{users.length}</div></div>
        <div className="card stat stat-amber"><div className="stat-label">Active admins</div><div className="stat-value">{admins}</div></div>
        <div className="card stat stat-red"><div className="stat-label">Disabled</div><div className="stat-value">{users.filter((u) => !u.enabled).length}</div></div>
      </div>

      <section className="card no-pad">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Email / mobile</th><th>Signs in with</th><th>Role</th><th>Status</th><th>Joined</th><th>Last sign-in</th><th /></tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const self = u.id === me.id;
                return (
                  <tr key={u.id} className={u.enabled ? '' : 'row-disabled'}>
                    <td>{u.fullName}{self && <span className="tag">you</span>}</td>
                    <td>{u.email || u.phone}</td>
                    <td>
                      {u.provider
                        ? <span className="provider"><ProviderIcon id={u.provider} />{PROVIDER_NAMES[u.provider] || u.provider}</span>
                        : <span className="muted">—</span>}
                    </td>
                    <td>
                      <select
                        value={u.role}
                        disabled={self || busyId === u.id}
                        onChange={(e) => change(u, () => api.setUserRole(u.id, e.target.value))}
                        aria-label={`Role for ${u.fullName}`}
                      >
                        <option value="PENDING">Waiting for approval</option>
                        <option value="USER">Sales user</option>
                        <option value="AGENT">Agent</option>
                        <option value="ADMIN">Admin</option>
                      </select>
                    </td>
                    <td>
                      <span className={`badge ${u.enabled ? 'badge-green' : 'badge-grey'}`}>{u.enabled ? 'Active' : 'Disabled'}</span>
                    </td>
                    <td>{dateTime(u.createdAt)}</td>
                    <td>{dateTime(u.lastLoginAt)}</td>
                    <td className="actions">
                      {!self && u.email && (
                        <button className="btn btn-ghost" disabled={busyId === u.id} onClick={() => setResetting(u)}>
                          Set password
                        </button>
                      )}
                      {!self && (
                        <button
                          className={`btn ${u.enabled ? 'btn-ghost' : 'btn-secondary'}`}
                          disabled={busyId === u.id}
                          onClick={() => change(u, () => api.setUserEnabled(u.id, !u.enabled))}
                        >
                          {u.enabled ? 'Disable' : 'Enable'}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
      {resetting && (
        <PasswordModal
          title={`Set password · ${resetting.fullName}`}
          intro={`${resetting.email} will sign in with this password. Share it with them securely and ask them to change it.`}
          submitLabel="Set password"
          onClose={() => setResetting(null)}
          onSubmit={async (_current, next) => {
            const updated = await api.resetUserPassword(resetting.id, next);
            setUsers((list) => list.map((x) => (x.id === updated.id ? updated : x)));
            setResetting(null);
            notify(`Password set for ${updated.fullName}`);
          }}
        />
      )}
      <p className="muted small">
        New accounts (registered, mobile or Google/GitHub) wait for approval: you decide here whether they are sales users or agents. Change your own password with the button next to Sign out. Sales users can add sales and update installation status. Agents only see installation tasks: they pick them up or accept assigned ones from their notifications. Admins can also upload purchase records, edit or delete sales and manage users.
      </p>
    </div>
  );
}
