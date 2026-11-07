import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useToast } from '../components/Toast.jsx';
import { dateTime } from '../format.js';

export default function Users() {
  const { user: me } = useAuth();
  const notify = useToast();
  const [users, setUsers] = useState(null);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

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

  return (
    <div className="stack">
      <div className="stats">
        <div className="card stat"><div className="stat-label">Accounts</div><div className="stat-value">{users.length}</div></div>
        <div className="card stat stat-amber"><div className="stat-label">Active admins</div><div className="stat-value">{admins}</div></div>
        <div className="card stat stat-red"><div className="stat-label">Disabled</div><div className="stat-value">{users.filter((u) => !u.enabled).length}</div></div>
      </div>

      <section className="card no-pad">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th><th>Last sign-in</th><th /></tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const self = u.id === me.id;
                return (
                  <tr key={u.id} className={u.enabled ? '' : 'row-disabled'}>
                    <td>{u.fullName}{self && <span className="tag">you</span>}</td>
                    <td>{u.email}</td>
                    <td>
                      <select
                        value={u.role}
                        disabled={self || busyId === u.id}
                        onChange={(e) => change(u, () => api.setUserRole(u.id, e.target.value))}
                        aria-label={`Role for ${u.fullName}`}
                      >
                        <option value="USER">Sales user</option>
                        <option value="ADMIN">Admin</option>
                      </select>
                    </td>
                    <td>
                      <span className={`badge ${u.enabled ? 'badge-green' : 'badge-grey'}`}>{u.enabled ? 'Active' : 'Disabled'}</span>
                    </td>
                    <td>{dateTime(u.createdAt)}</td>
                    <td>{dateTime(u.lastLoginAt)}</td>
                    <td className="actions">
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
      <p className="muted small">
        New sign-ups are sales users. Admins can upload purchase records, edit or delete sales, change installation status and manage users.
      </p>
    </div>
  );
}
