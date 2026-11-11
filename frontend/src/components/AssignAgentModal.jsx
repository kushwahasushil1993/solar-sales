import { useEffect, useState } from 'react';
import { api } from '../api.js';
import Modal from './Modal.jsx';

/** Admin: assign / reassign / unassign the installation task of a sale. */
export default function AssignAgentModal({ sale, onClose, onSaved }) {
  const [agents, setAgents] = useState(null);
  const [agentId, setAgentId] = useState(sale.agentId || '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.agents().then((list) => setAgents(list.filter((a) => a.enabled))).catch((e) => setError(e.message));
  }, []);

  async function run(action) {
    setBusy(true);
    setError('');
    try {
      onSaved(await action());
    } catch (e) {
      setError(e.fieldErrors?.agentId || e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={`Assign agent · ${sale.customerName}`}
      onClose={onClose}
      footer={
        <>
          {sale.agentId && (
            <button className="btn btn-ghost danger-text" disabled={busy} onClick={() => run(() => api.unassignTask(sale.id))}>
              Remove agent
            </button>
          )}
          <div className="toolbar-spacer" />
          <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy || !agentId || Number(agentId) === sale.agentId}
                  onClick={() => run(() => api.assignTask(sale.id, Number(agentId)))}>
            {sale.agentId ? 'Reassign' : 'Assign'}
          </button>
        </>
      }
    >
      <div className="stack-sm">
        <p className="muted small">{sale.invoiceNumber} · {sale.city || '—'}</p>
        {sale.agentName && (
          <p className="small">Currently: <strong>{sale.agentName}</strong> ({sale.taskState === 'ACCEPTED' ? 'accepted' : 'waiting for acceptance'})</p>
        )}
        {error && <div className="alert alert-error">{error}</div>}
        {agents?.length === 0 && <div className="alert alert-info">No active agents. Give someone the Agent role on the Users page.</div>}
        {agents?.length > 0 && (
          <label className="field">
            <span>Agent</span>
            <select value={agentId} onChange={(e) => setAgentId(e.target.value)}>
              <option value="">Choose an agent…</option>
              {agents.map((a) => (
                <option key={a.id} value={a.id}>{a.fullName} · {a.inProgress + a.awaitingAcceptance} open task(s)</option>
              ))}
            </select>
          </label>
        )}
        <p className="muted small">The agent gets a notification and accepts the task by opening it. You'll be notified when they do.</p>
      </div>
    </Modal>
  );
}
