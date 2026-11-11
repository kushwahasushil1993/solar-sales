import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { dateTime } from '../format.js';

const ACTION_LABEL = {
  ASSIGNED: 'Assigned', UNASSIGNED: 'Removed from task', PICKED: 'Picked up', ACCEPTED: 'Accepted', STATUS_CHANGED: 'Status updated',
};

/** Admin: every agent with their workload, and the activity of the selected agent (or of all agents). */
export default function Agents() {
  const [agents, setAgents] = useState(null);
  const [selected, setSelected] = useState(null); // null = all agents
  const [activity, setActivity] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.agents().then(setAgents).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    setActivity(null);
    (selected ? api.agentActivity(selected.id) : api.activity()).then(setActivity).catch((e) => setError(e.message));
  }, [selected]);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!agents) return <div className="loading">Loading agents…</div>;

  return (
    <div className="stack">
      {agents.length === 0 && (
        <div className="card empty-card">
          <p>No agents yet. Give someone the <strong>Agent</strong> role on the <Link to="/users" className="link">Users</Link> page.</p>
        </div>
      )}

      {agents.length > 0 && (
        <section className="card no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr><th>Agent</th><th>Contact</th><th className="num">Waiting acceptance</th><th className="num">In progress</th><th className="num">Completed</th><th>Last activity</th><th /></tr>
              </thead>
              <tbody>
                {agents.map((a) => (
                  <tr key={a.id} className={`${a.enabled ? '' : 'row-disabled'} ${selected?.id === a.id ? 'row-selected' : ''}`}>
                    <td>{a.fullName}{!a.enabled && <span className="tag">disabled</span>}</td>
                    <td className="small">{a.email || a.phone || '—'}</td>
                    <td className="num">{a.awaitingAcceptance}</td>
                    <td className="num">{a.inProgress}</td>
                    <td className="num text-green">{a.completed}</td>
                    <td className="small nowrap">{a.lastActivityAt ? dateTime(a.lastActivityAt) : '—'}</td>
                    <td className="actions">
                      <button className="btn btn-ghost" onClick={() => setSelected(selected?.id === a.id ? null : a)}>
                        {selected?.id === a.id ? 'Show all' : 'View activity'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="card">
        <div className="card-head">
          <h3>{selected ? `Activity · ${selected.fullName}` : 'Recent activity of all agents'}</h3>
          {selected && <button className="btn btn-ghost" onClick={() => setSelected(null)}>Show all agents</button>}
        </div>
        {!activity && <div className="loading">Loading…</div>}
        {activity?.length === 0 && <p className="muted">No activity yet.</p>}
        <ol className="timeline">
          {activity?.map((a) => (
            <li key={a.id}>
              <span className={`timeline-dot action-${a.action.toLowerCase()}`} />
              <div>
                <div>
                  <strong>{ACTION_LABEL[a.action] || a.action}</strong>
                  {' · '}{a.customerName} <span className="muted mono small">{a.invoiceNumber}</span>
                </div>
                <div className="muted small">
                  {a.detail && <>{a.detail} · </>}
                  by {a.actorName}{a.agentName && a.actorName !== a.agentName ? ` for ${a.agentName}` : ''} · {dateTime(a.createdAt)}
                </div>
              </div>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
