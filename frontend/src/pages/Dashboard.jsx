import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import StatCard from '../components/StatCard.jsx';
import Badge from '../components/Badge.jsx';
import { date, money, moneyCompact, monthLabel, number, titleCase } from '../format.js';

const PANEL_COLORS = ['#f59e0b', '#0ea5e9', '#10b981', '#8b5cf6', '#ef4444', '#64748b'];
const INSTALL_COLORS = { INSTALLED: '#10b981', SCHEDULED: '#0ea5e9', PENDING: '#94a3b8' };

export default function Dashboard() {
  const { isAdmin } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.dashboard().then(setData).catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!data) return <div className="loading">Loading dashboard…</div>;

  const monthly = data.monthly.map((m) => ({ ...m, label: monthLabel(m.month), revenue: Number(m.revenue) }));
  const panels = data.byPanelType.map((p) => ({ ...p, value: Number(p.value) }));
  const installTotal = data.byInstallationStatus.reduce((s, b) => s + b.count, 0) || 1;
  const collectedPct = data.totalRevenue > 0 ? Math.round((data.amountCollected / data.totalRevenue) * 100) : 0;

  return (
    <div className="stack">
      <div className="stats">
        <StatCard label="Total sales" value={number(data.totalSales)} hint="All recorded purchases" />
        <StatCard label="Total revenue" value={moneyCompact(data.totalRevenue)} hint={money(data.totalRevenue)} accent="amber" />
        <StatCard label="Collected" value={moneyCompact(data.amountCollected)} hint={`${collectedPct}% · ${moneyCompact(data.outstandingBalance)} outstanding`} accent="green" />
        <StatCard label="Capacity sold" value={`${number(data.totalCapacityKw)} kW`} hint="Across all systems" accent="blue" />
        <StatCard label="Pending installs" value={number(data.pendingInstallations)} hint="Pending or scheduled" accent="red" />
      </div>

      <div className="grid-charts">
        <section className="card">
          <div className="card-head">
            <h3>Monthly revenue</h3>
            <span className="muted">Last 12 months</span>
          </div>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={monthly} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} fontSize={12} />
              <YAxis tickFormatter={moneyCompact} tickLine={false} axisLine={false} fontSize={12} width={70} />
              <Tooltip
                formatter={(v, n) => (n === 'revenue' ? [money(v), 'Revenue'] : [v, n])}
                labelFormatter={(l, p) => `${l} · ${p?.[0]?.payload.sales ?? 0} sales · ${number(p?.[0]?.payload.capacityKw)} kW`}
              />
              <Bar dataKey="revenue" fill="#f59e0b" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </section>

        <section className="card">
          <div className="card-head">
            <h3>Capacity by panel type</h3>
          </div>
          {panels.length === 0 ? (
            <p className="muted">No data yet.</p>
          ) : (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie data={panels} dataKey="value" nameKey="label" innerRadius={50} outerRadius={80} paddingAngle={2}>
                    {panels.map((p, i) => <Cell key={p.label} fill={PANEL_COLORS[i % PANEL_COLORS.length]} />)}
                  </Pie>
                  <Tooltip formatter={(v, n) => [`${number(v)} kW`, n]} />
                </PieChart>
              </ResponsiveContainer>
              <ul className="legend">
                {panels.map((p, i) => (
                  <li key={p.label}>
                    <span className="dot" style={{ background: PANEL_COLORS[i % PANEL_COLORS.length] }} />
                    {p.label}
                    <span className="muted">{p.count} · {number(p.value)} kW</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      </div>

      <div className="grid-charts">
        <section className="card">
          <div className="card-head">
            <h3>Recent sales</h3>
            <Link to="/sales" className="link">View all →</Link>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr><th>Invoice</th><th>Customer</th><th>Date</th><th className="num">kW</th><th className="num">Amount</th><th>Payment</th></tr>
              </thead>
              <tbody>
                {data.recentSales.map((s) => (
                  <tr key={s.id}>
                    <td className="mono">{s.invoiceNumber}</td>
                    <td>{s.customerName}</td>
                    <td>{date(s.purchaseDate)}</td>
                    <td className="num">{number(s.systemCapacityKw)}</td>
                    <td className="num">{money(s.totalAmount)}</td>
                    <td><Badge value={s.paymentStatus} /></td>
                  </tr>
                ))}
                {data.recentSales.length === 0 && (
                  <tr>
                    <td colSpan={6} className="empty">
                      No sales yet — {isAdmin ? <Link to="/upload" className="link">upload an Excel sheet</Link> : <Link to="/sales" className="link">add a sale</Link>}.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="card">
          <div className="card-head">
            <h3>Installation status</h3>
          </div>
          <div className="progress-list">
            {['INSTALLED', 'SCHEDULED', 'PENDING'].map((status) => {
              const b = data.byInstallationStatus.find((x) => x.label === status) || { count: 0 };
              const pct = Math.round((b.count / installTotal) * 100);
              return (
                <div key={status} className="progress-row">
                  <div className="progress-label">
                    <span>{titleCase(status)}</span>
                    <span className="muted">{b.count} ({pct}%)</span>
                  </div>
                  <div className="progress"><div style={{ width: `${pct}%`, background: INSTALL_COLORS[status] }} /></div>
                </div>
              );
            })}
          </div>
        </section>
      </div>
    </div>
  );
}
