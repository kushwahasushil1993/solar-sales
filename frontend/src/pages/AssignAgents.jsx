import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import Badge from '../components/Badge.jsx';
import { useToast } from '../components/Toast.jsx';
import { date, dateTime, number } from '../format.js';

const TABS = [
  { id: 'OPEN', label: 'New (no agent)', count: 'open' },
  { id: 'ASSIGNED', label: 'Waiting for acceptance', count: 'assigned' },
  { id: 'ACCEPTED', label: 'Accepted', count: 'accepted' },
];

/** Admin dispatch board: give new installation jobs to agents, one by one or several at once. */
export default function AssignAgents() {
  const notify = useToast();
  const [tab, setTab] = useState('OPEN');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [board, setBoard] = useState(null);
  const [agents, setAgents] = useState([]);
  const [rowAgent, setRowAgent] = useState({}); // saleId -> agentId
  const [selected, setSelected] = useState(new Set());
  const [bulkAgent, setBulkAgent] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const t = setTimeout(() => setQuery(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  const load = useCallback(() => {
    api.taskBoard(tab, query).then((b) => { setBoard(b); setError(''); }).catch((e) => setError(e.message));
    api.agents().then((list) => setAgents(list.filter((a) => a.enabled))).catch(() => {});
  }, [tab, query]);

  useEffect(() => {
    setSelected(new Set());
    load();
  }, [load]);

  async function assignOne(task) {
    const agentId = Number(rowAgent[task.id]);
    if (!agentId) return;
    setBusy(true);
    try {
      const t = await api.assignTask(task.id, agentId);
      notify(`${task.invoiceNumber} assigned to ${t.agentName}. Waiting for acceptance.`);
      load();
    } catch (e) {
      notify(e.fieldErrors?.agentId || e.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function assignSelected() {
    setBusy(true);
    try {
      const r = await api.assignMany([...selected], Number(bulkAgent));
      const name = agents.find((a) => a.id === Number(bulkAgent))?.fullName;
      notify(`${r.assigned} record${r.assigned === 1 ? '' : 's'} assigned to ${name}` + (r.skipped.length ? ` · skipped: ${r.skipped.join(', ')}` : ''),
        r.skipped.length ? 'error' : 'success');
      setSelected(new Set());
      setBulkAgent('');
      load();
    } catch (e) {
      notify(e.fieldErrors?.agentId || e.fieldErrors?.saleIds || e.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function unassign(task) {
    setBusy(true);
    try {
      await api.unassignTask(task.id);
      notify(`${task.invoiceNumber} is back in New`);
      load();
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const toggle = (id) => setSelected((s) => {
    const next = new Set(s);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  const tasks = board?.tasks || [];
  const allSelected = tasks.length > 0 && tasks.every((t) => selected.has(t.id));
  const agentOptions = (exceptId) => agents.filter((a) => a.id !== exceptId).map((a) => (
    <option key={a.id} value={a.id}>{a.fullName} · {a.inProgress + a.awaitingAcceptance} open</option>
  ));

  return (
    <div className="assign-layout">
      <div className="stack">
        <div className="tabs" role="tablist">
          {TABS.map((t) => (
            <button key={t.id} role="tab" aria-selected={tab === t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
              {t.label}<span className="tab-count">{board ? board[t.count] : '…'}</span>
            </button>
          ))}
        </div>

        <section className="card toolbar">
          <input className="search" type="search" placeholder="Search customer, invoice, city or agent…" value={search} onChange={(e) => setSearch(e.target.value)} />
          {selected.size > 0 && (
            <div className="bulk-bar">
              <strong>{selected.size} selected</strong>
              <select value={bulkAgent} onChange={(e) => setBulkAgent(e.target.value)} aria-label="Agent for selected records">
                <option value="">Choose an agent…</option>
                {agentOptions(null)}
              </select>
              <button className="btn btn-primary" disabled={busy || !bulkAgent} onClick={assignSelected}>Assign selected</button>
              <button className="btn btn-ghost" onClick={() => setSelected(new Set())}>Clear</button>
            </div>
          )}
        </section>

        {error && <div className="alert alert-error">{error}</div>}
        {agents.length === 0 && board && (
          <div className="alert alert-info">No active agents yet. Give installers the <strong>Agent</strong> role on the <Link to="/users" className="link">Users</Link> page.</div>
        )}

        <section className="card no-pad">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th><input type="checkbox" aria-label="Select all" checked={allSelected} disabled={!tasks.length}
                             onChange={() => setSelected(allSelected ? new Set() : new Set(tasks.map((t) => t.id)))} /></th>
                  <th>Invoice</th><th>Customer</th><th>System</th><th>Ordered</th><th>Installation</th>
                  {tab !== 'OPEN' && <th>Agent</th>}
                  <th>{tab === 'OPEN' ? 'Assign to' : 'Reassign to'}</th>
                </tr>
              </thead>
              <tbody>
                {!board && <tr><td colSpan={8} className="empty">Loading…</td></tr>}
                {tasks.map((t) => (
                  <tr key={t.id} className={selected.has(t.id) ? 'row-selected' : ''}>
                    <td><input type="checkbox" aria-label={`Select ${t.invoiceNumber}`} checked={selected.has(t.id)} onChange={() => toggle(t.id)} /></td>
                    <td className="mono">
                      {t.invoiceNumber}
                      <div className="muted small">added {dateTime(t.createdAt)}</div>
                    </td>
                    <td>
                      <div>{t.customerName}</div>
                      <div className="muted small">{[t.city, t.phone].filter(Boolean).join(' · ')}</div>
                    </td>
                    <td className="small">{number(t.systemCapacityKw)} kW {t.panelType || ''}</td>
                    <td className="small nowrap">{date(t.purchaseDate)}</td>
                    <td><Badge value={t.installationStatus} kind="install" /></td>
                    {tab !== 'OPEN' && (
                      <td className="small">
                        <div>{t.agentName}</div>
                        <div className="muted">{t.taskState === 'ACCEPTED' ? `accepted ${dateTime(t.acceptedAt)}` : `assigned ${dateTime(t.assignedAt)}`}</div>
                      </td>
                    )}
                    <td>
                      <div className="assign-cell">
                        <select value={rowAgent[t.id] || ''} onChange={(e) => setRowAgent((m) => ({ ...m, [t.id]: e.target.value }))}
                                aria-label={`Agent for ${t.invoiceNumber}`} disabled={!agents.length}>
                          <option value="">Agent…</option>
                          {agentOptions(t.agentId)}
                        </select>
                        <button className="btn btn-secondary btn-sm" disabled={busy || !rowAgent[t.id]} onClick={() => assignOne(t)}>
                          {tab === 'OPEN' ? 'Assign' : 'Reassign'}
                        </button>
                        {tab !== 'OPEN' && (
                          <button className="btn btn-ghost btn-sm danger-text" disabled={busy} onClick={() => unassign(t)} title="Remove agent">✕</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {board && tasks.length === 0 && (
                  <tr><td colSpan={8} className="empty">
                    {tab === 'OPEN' ? 'No new records waiting for an agent. 🎉' : 'Nothing here.'}
                  </td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <aside className="card agent-panel">
        <div className="card-head"><h3>Agents' workload</h3><Link to="/agents" className="link small">Details →</Link></div>
        {agents.length === 0 && <p className="muted small">No active agents.</p>}
        <ul className="workload">
          {agents.map((a) => {
            const open = a.inProgress + a.awaitingAcceptance;
            return (
              <li key={a.id}>
                <div className="workload-head">
                  <strong>{a.fullName}</strong>
                  <span className="muted small">{open} open · {a.completed} done</span>
                </div>
                <div className="progress"><div style={{ width: `${Math.min(100, open * 10)}%`, background: open > 8 ? '#dc2626' : '#f59e0b' }} /></div>
                {a.awaitingAcceptance > 0 && <div className="muted small">{a.awaitingAcceptance} waiting for acceptance</div>}
              </li>
            );
          })}
        </ul>
      </aside>
    </div>
  );
}
