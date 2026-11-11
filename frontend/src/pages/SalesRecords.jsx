import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import Badge from '../components/Badge.jsx';
import Modal from '../components/Modal.jsx';
import Pagination from '../components/Pagination.jsx';
import SaleFormModal from '../components/SaleFormModal.jsx';
import CustomerEmailsModal from '../components/CustomerEmailsModal.jsx';
import InstallationModal from '../components/InstallationModal.jsx';
import { useToast } from '../components/Toast.jsx';
import { INSTALLATION_STATUSES, PAYMENT_STATUSES, date, money, number, titleCase } from '../format.js';

const NO_FILTERS = { search: '', paymentStatus: '', installationStatus: '', from: '', to: '', mine: false };

const COLUMNS = [
  { key: 'invoiceNumber', label: 'Invoice', sortable: true },
  { key: 'purchaseDate', label: 'Date', sortable: true },
  { key: 'customerName', label: 'Customer', sortable: true },
  { key: 'panelType', label: 'System' },
  { key: 'systemCapacityKw', label: 'kW', sortable: true, num: true },
  { key: 'totalAmount', label: 'Total', sortable: true, num: true },
  { key: 'amountPaid', label: 'Paid / Balance', sortable: true, num: true },
  { key: 'paymentStatus', label: 'Payment' },
  { key: 'installationStatus', label: 'Installation' },
];

export default function SalesRecords() {
  const notify = useToast();
  const { isAdmin } = useAuth();
  const [filters, setFilters] = useState(NO_FILTERS);
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState({ by: 'purchaseDate', dir: 'desc' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null); // null = closed, {} = new, sale = edit
  const [deleting, setDeleting] = useState(null);
  const [emailing, setEmailing] = useState(null);
  const [installing, setInstalling] = useState(null);

  // debounce the free-text search so we don't query on every keystroke
  useEffect(() => {
    const t = setTimeout(() => {
      setFilters((f) => (f.search === search ? f : { ...f, search }));
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const load = useCallback(() => {
    api.listSales({ ...filters, page, size, sortBy: sort.by, direction: sort.dir })
      .then((d) => { setData(d); setError(''); })
      .catch((e) => setError(e.message));
  }, [filters, page, size, sort]);

  useEffect(load, [load]);

  const setFilter = (k) => (e) => {
    setFilters((f) => ({ ...f, [k]: e.target.value }));
    setPage(0);
  };

  const toggleSort = (key) =>
    setSort((s) => ({ by: key, dir: s.by === key && s.dir === 'desc' ? 'asc' : 'desc' }));

  const hasFilters = search || Object.entries(filters).some(([k, v]) => k !== 'search' && v);

  async function confirmDelete() {
    try {
      await api.deleteSale(deleting.id);
      notify(`Deleted ${deleting.invoiceNumber}`);
      setDeleting(null);
      load();
    } catch (e) {
      notify(e.message, 'error');
    }
  }

  async function exportExcel() {
    try {
      const { mine, ...exportFilters } = filters;
      await api.exportSales(exportFilters);
    } catch (e) {
      notify(e.message, 'error');
    }
  }

  return (
    <div className="stack">
      <section className="card toolbar">
        <input
          className="search"
          type="search"
          placeholder="Search invoice, customer, phone, city…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select value={filters.paymentStatus} onChange={setFilter('paymentStatus')} aria-label="Payment status">
          <option value="">All payments</option>
          {PAYMENT_STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
        </select>
        <select value={filters.installationStatus} onChange={setFilter('installationStatus')} aria-label="Installation status">
          <option value="">All installations</option>
          {INSTALLATION_STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
        </select>
        <label className="inline-field">From <input type="date" value={filters.from} onChange={setFilter('from')} /></label>
        <label className="inline-field">To <input type="date" value={filters.to} onChange={setFilter('to')} /></label>
        <label className="checkbox">
          <input type="checkbox" checked={filters.mine} onChange={(e) => { setFilters((f) => ({ ...f, mine: e.target.checked })); setPage(0); }} />
          My sales only
        </label>
        {hasFilters && (
          <button className="btn btn-ghost" onClick={() => { setSearch(''); setFilters(NO_FILTERS); setPage(0); }}>
            Clear
          </button>
        )}
        <div className="toolbar-spacer" />
        {isAdmin && <button className="btn btn-secondary" onClick={exportExcel}>⤓ Export Excel</button>}
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ Add sale</button>
      </section>

      {error && <div className="alert alert-error">{error}</div>}

      <section className="card no-pad">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                {COLUMNS.map((c) => (
                  <th
                    key={c.key}
                    className={`${c.num ? 'num' : ''} ${c.sortable ? 'sortable' : ''}`}
                    onClick={c.sortable ? () => toggleSort(c.key) : undefined}
                  >
                    {c.label}
                    {sort.by === c.key && <span className="sort">{sort.dir === 'asc' ? ' ▲' : ' ▼'}</span>}
                  </th>
                ))}
                <th />
              </tr>
            </thead>
            <tbody>
              {!data && <tr><td colSpan={10} className="empty">Loading…</td></tr>}
              {data?.content.map((s) => (
                <tr key={s.id}>
                  <td className="mono">
                    {s.invoiceNumber}
                    {s.source === 'EXCEL' && <span className="tag" title="Imported from Excel">xlsx</span>}
                    {s.createdByName && <div className="muted small">by {s.createdByName}</div>}
                  </td>
                  <td>{date(s.purchaseDate)}</td>
                  <td>
                    <div>{s.customerName}</div>
                    <div className="muted small">{[s.city, s.phone].filter(Boolean).join(' · ')}</div>
                  </td>
                  <td>
                    <div>{s.panelType || '—'}</div>
                    <div className="muted small">{[s.panelCount && `${s.panelCount} panels`, s.inverterBrand].filter(Boolean).join(' · ')}</div>
                  </td>
                  <td className="num">{number(s.systemCapacityKw)}</td>
                  <td className="num">{money(s.totalAmount)}</td>
                  <td className="num">
                    <div>{money(s.amountPaid)}</div>
                    {s.balanceDue > 0 && <div className="small text-red">{money(s.balanceDue)} due</div>}
                  </td>
                  <td><Badge value={s.paymentStatus} /></td>
                  <td>
                    <Badge value={s.installationStatus} kind="install" />
                    {s.installationDate && <div className="muted small">{date(s.installationDate)}</div>}
                  </td>
                  <td className="actions">
                    <button className="icon-btn" title="Update installation status" onClick={() => setInstalling(s)}>⚡</button>
                    {isAdmin && (
                      <>
                        <button className="icon-btn" title="Customer emails / send invoice" onClick={() => setEmailing(s)}>✉</button>
                        <button className="icon-btn" title="Edit" onClick={() => setEditing(s)}>✎</button>
                        <button className="icon-btn danger" title="Delete" onClick={() => setDeleting(s)}>🗑</button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
              {data && data.content.length === 0 && (
                <tr><td colSpan={10} className="empty">No sales match your filters.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {data && (
          <Pagination
            page={data.page}
            size={size}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPage={setPage}
            onSize={(s) => { setSize(s); setPage(0); }}
          />
        )}
      </section>

      {editing && (
        <SaleFormModal
          sale={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            notify(editing.id ? `Updated ${saved.invoiceNumber}` : `Added ${saved.invoiceNumber}`);
            setEditing(null);
            load();
          }}
        />
      )}

      {emailing && <CustomerEmailsModal sale={emailing} onClose={() => setEmailing(null)} />}

      {installing && (
        <InstallationModal
          sale={installing}
          onClose={() => setInstalling(null)}
          onSaved={(saved) => {
            notify(`${saved.invoiceNumber}: ${titleCase(saved.installationStatus)}`);
            setInstalling(null);
            load();
          }}
        />
      )}

      {deleting && (
        <Modal
          title="Delete sale?"
          onClose={() => setDeleting(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDeleting(null)}>Cancel</button>
              <button className="btn btn-danger" onClick={confirmDelete}>Delete</button>
            </>
          }
        >
          <p>
            This permanently deletes <strong>{deleting.invoiceNumber}</strong> for {deleting.customerName} (
            {money(deleting.totalAmount)}).
          </p>
        </Modal>
      )}
    </div>
  );
}
