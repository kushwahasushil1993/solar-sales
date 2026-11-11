import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import Modal from '../components/Modal.jsx';
import TaskCard from '../components/TaskCard.jsx';
import { useToast } from '../components/Toast.jsx';

/** Open installation jobs any agent can pick up (first come, first served). */
export default function AvailableTasks() {
  const notify = useToast();
  const navigate = useNavigate();
  const [tasks, setTasks] = useState(null);
  const [error, setError] = useState('');
  const [picking, setPicking] = useState(null);
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState('');

  const load = useCallback(() => {
    api.availableTasks().then((t) => { setTasks(t); setError(''); }).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  async function pick() {
    setBusy(true);
    try {
      await api.pickTask(picking.id);
      notify(`You picked up ${picking.customerName}'s installation`);
      setPicking(null);
      navigate('/tasks');
    } catch (e) {
      notify(e.message, 'error');
      setPicking(null);
      load();
    } finally {
      setBusy(false);
    }
  }

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!tasks) return <div className="loading">Loading available tasks…</div>;

  const q = search.trim().toLowerCase();
  const shown = q ? tasks.filter((t) => [t.customerName, t.invoiceNumber, t.city, t.state].join(' ').toLowerCase().includes(q)) : tasks;

  return (
    <div className="stack">
      <section className="card toolbar">
        <input className="search" type="search" placeholder="Filter by customer, invoice or city…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <span className="muted small">{tasks.length} open {tasks.length === 1 ? 'task' : 'tasks'}</span>
      </section>
      <div className="install-grid">
        {shown.map((t) => (
          <TaskCard key={t.id} task={t}
                    actions={<button className="btn btn-primary btn-sm" onClick={() => setPicking(t)}>Pick up</button>} />
        ))}
      </div>
      {shown.length === 0 && <div className="card empty-card"><p>No open tasks right now.</p></div>}

      {picking && (
        <Modal
          title="Pick up this task?"
          onClose={() => setPicking(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setPicking(null)}>Cancel</button>
              <button className="btn btn-primary" disabled={busy} onClick={pick}>{busy ? 'Picking up…' : 'Pick up'}</button>
            </>
          }
        >
          <p>
            You'll be responsible for the installation for <strong>{picking.customerName}</strong> ({picking.invoiceNumber},{' '}
            {picking.city || 'no city'}). The admin is notified.
          </p>
        </Modal>
      )}
    </div>
  );
}
