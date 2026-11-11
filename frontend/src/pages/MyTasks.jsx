import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import InstallationModal from '../components/InstallationModal.jsx';
import StatCard from '../components/StatCard.jsx';
import TaskCard from '../components/TaskCard.jsx';
import { useToast } from '../components/Toast.jsx';
import { titleCase } from '../format.js';

const TABS = [
  { id: 'active', label: 'To do' },
  { id: 'waiting', label: 'Waiting for my acceptance' },
  { id: 'done', label: 'Completed' },
];

/** Agent dashboard: only the tasks this agent picked up or was assigned. */
export default function MyTasks() {
  const notify = useToast();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState('active');
  const [updating, setUpdating] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(() => {
    api.myTasks().then((d) => { setData(d); setError(''); }).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  async function accept(task) {
    setBusyId(task.id);
    try {
      await api.acceptTask(task.id);
      notify(`Accepted: ${task.customerName}`);
      window.dispatchEvent(new Event('inbox-changed'));
      load();
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      setBusyId(null);
    }
  }

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!data) return <div className="loading">Loading your tasks…</div>;

  const done = (t) => t.installationStatus === 'INSTALLED';
  const lists = {
    active: data.tasks.filter((t) => !done(t) && t.taskState === 'ACCEPTED'),
    waiting: data.tasks.filter((t) => !done(t) && t.taskState === 'ASSIGNED'),
    done: data.tasks.filter(done),
  };

  return (
    <div className="stack">
      <div className="stats">
        <StatCard label="To do" value={data.inHand} hint="Accepted, not installed yet" accent="blue" />
        <StatCard label="Waiting for my acceptance" value={data.awaitingAcceptance} hint="Assigned by an admin" accent="amber" />
        <StatCard label="Completed" value={data.completed} hint="Installed" accent="green" />
      </div>

      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t.id} role="tab" aria-selected={tab === t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
            {t.label}<span className="tab-count">{lists[t.id].length}</span>
          </button>
        ))}
      </div>

      <div className="install-grid">
        {lists[tab].map((t) => (
          <TaskCard
            key={t.id}
            task={t}
            actions={
              t.taskState === 'ASSIGNED' && !done(t) ? (
                <button className="btn btn-primary btn-sm" disabled={busyId === t.id} onClick={() => accept(t)}>Accept task</button>
              ) : !done(t) ? (
                <button className="btn btn-secondary btn-sm" onClick={() => setUpdating(t)}>Update status</button>
              ) : null
            }
          />
        ))}
      </div>

      {lists[tab].length === 0 && (
        <div className="card empty-card">
          {tab === 'active' && <p>Nothing to do right now. <Link to="/tasks/available" className="link">Pick up an available task</Link>.</p>}
          {tab === 'waiting' && <p>No tasks waiting for you. New assignments appear in your notifications 🔔.</p>}
          {tab === 'done' && <p>No completed installations yet.</p>}
        </div>
      )}

      {updating && (
        <InstallationModal
          sale={updating}
          save={(body, notifyCustomer) => api.updateTaskStatus(updating.id, body, notifyCustomer)}
          onClose={() => setUpdating(null)}
          onSaved={(saved) => {
            setUpdating(null);
            notify(`${saved.customerName}: ${titleCase(saved.installationStatus)}`);
            load();
          }}
        />
      )}
    </div>
  );
}
