import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import Badge from '../components/Badge.jsx';
import { date, number, titleCase } from '../format.js';

const TABS = ['', 'PENDING', 'SCHEDULED', 'INSTALLED'];
const PAGE_SIZE = 12;

export default function Installations() {
  const { isAdmin } = useAuth();
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [mine, setMine] = useState(false);
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const t = setTimeout(() => setQuery(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  useEffect(() => {
    api.dashboard()
      .then((d) => setCounts(Object.fromEntries(d.byInstallationStatus.map((b) => [b.label, b.count]))))
      .catch(() => {});
  }, []);

  // reset the list whenever the filters change, then append further pages on "Load more"
  useEffect(() => {
    setPage(0);
  }, [query, status, mine]);

  useEffect(() => {
    let stale = false; // a newer filter/page superseded this request
    setLoading(true);
    api.listSales({ search: query, installationStatus: status, mine, page, size: PAGE_SIZE, sortBy: 'purchaseDate', direction: 'desc' })
      .then((d) => {
        if (stale) return;
        setItems((prev) => (page === 0 ? d.content : [...prev, ...d.content]));
        setTotal(d.totalElements);
        setError('');
      })
      .catch((e) => !stale && setError(e.message))
      .finally(() => !stale && setLoading(false));
    return () => { stale = true; };
  }, [query, status, mine, page]);

  const allCount = Object.values(counts).reduce((a, b) => a + b, 0);

  return (
    <div className="stack">
      <section className="card toolbar">
        <input
          className="search"
          type="search"
          placeholder="Find by invoice no, customer name or phone…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          autoFocus
        />
        <label className="checkbox">
          <input type="checkbox" checked={mine} onChange={(e) => setMine(e.target.checked)} />
          Only sales I added
        </label>
      </section>

      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t || 'all'} role="tab" aria-selected={status === t} className={`tab ${status === t ? 'active' : ''}`} onClick={() => setStatus(t)}>
            {t ? titleCase(t) : 'All'}
            <span className="tab-count">{t ? counts[t] ?? 0 : allCount}</span>
          </button>
        ))}
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="install-grid">
        {items.map((s) => <InstallCard key={s.id} sale={s} />)}
      </div>

      {!loading && items.length === 0 && (
        <div className="card empty-card">
          <p>No installations match{query ? ` “${query}”` : ''}.</p>
          {!isAdmin && <p className="muted small">New sales start as Pending until an admin schedules the installation.</p>}
        </div>
      )}
      {loading && <div className="loading">Loading…</div>}
      {!loading && items.length < total && (
        <div className="center">
          <button className="btn btn-secondary" onClick={() => setPage((p) => p + 1)}>
            Load more ({total - items.length} left)
          </button>
        </div>
      )}
    </div>
  );
}

function InstallCard({ sale: s }) {
  const scheduled = s.installationStatus === 'SCHEDULED' || s.installationStatus === 'INSTALLED';
  const installed = s.installationStatus === 'INSTALLED';
  const steps = [
    { label: 'Purchased', done: true, when: date(s.purchaseDate) },
    { label: 'Scheduled', done: scheduled, when: s.installationStatus === 'SCHEDULED' && s.installationDate ? `for ${date(s.installationDate)}` : scheduled ? '✓' : 'Awaiting' },
    { label: 'Installed', done: installed, when: installed ? date(s.installationDate) : '—' },
  ];
  return (
    <article className="card install-card">
      <div className="install-head">
        <div>
          <div className="install-name">{s.customerName}</div>
          <div className="muted small mono">{s.invoiceNumber}</div>
        </div>
        <Badge value={s.installationStatus} kind="install" />
      </div>
      <div className="muted small">
        {[s.city, s.state].filter(Boolean).join(', ') || '—'} · {number(s.systemCapacityKw)} kW {s.panelType || ''}
        {s.phone && <> · {s.phone}</>}
      </div>
      <ol className="tracker">
        {steps.map((st) => (
          <li key={st.label} className={st.done ? 'done' : ''}>
            <span className="tracker-dot">{st.done ? '✓' : ''}</span>
            <span className="tracker-label">{st.label}</span>
            <span className="tracker-when">{st.when}</span>
          </li>
        ))}
      </ol>
      {s.createdByName && <div className="muted small">Added by {s.createdByName}</div>}
    </article>
  );
}
