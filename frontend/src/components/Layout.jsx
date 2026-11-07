import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../auth.jsx';

const NAV = [
  { to: '/', label: 'Dashboard', icon: '▦', end: true },
  { to: '/sales', label: 'Sales Records', icon: '☰' },
  { to: '/installations', label: 'Installation Status', icon: '⚡' },
  { to: '/upload', label: 'Upload Purchases', icon: '⇪', admin: true },
  { to: '/users', label: 'Users', icon: '👥', admin: true },
];

const TITLES = {
  '/': ['Dashboard', 'Overview of solar sales, revenue and installations'],
  '/sales': ['Sales Records', 'Search, add and review solar sales'],
  '/installations': ['Installation Status', 'Track each customer’s installation progress'],
  '/upload': ['Upload Purchases', 'Import purchase records from an Excel sheet'],
  '/users': ['Users', 'Manage accounts and admin access'],
};

const initials = (name) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');

export default function Layout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user, isAdmin, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [title, subtitle] = TITLES[pathname] || TITLES['/'];

  return (
    <div className="shell">
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand">
          <span className="brand-icon">☀</span>
          <div>
            <div className="brand-name">SolarSales</div>
            <div className="brand-sub">Sales &amp; Purchases</div>
          </div>
        </div>
        <nav>
          {NAV.filter((n) => !n.admin || isAdmin).map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="nav-link" onClick={() => setOpen(false)}>
              <span className="nav-icon">{n.icon}</span>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">v0.2 · React + Spring Boot</div>
      </aside>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}

      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-btn" onClick={() => setOpen(true)} aria-label="Open menu">☰</button>
          <div className="topbar-title">
            <h1>{title}</h1>
            <p className="muted">{subtitle}</p>
          </div>
          <div className="user-chip">
            <span className="avatar">{initials(user.fullName)}</span>
            <div className="user-meta">
              <div className="user-name">{user.fullName}</div>
              <span className={`role role-${user.role.toLowerCase()}`}>{user.role === 'ADMIN' ? 'Admin' : 'Sales user'}</span>
            </div>
            <button className="btn btn-ghost" onClick={() => { logout(); navigate('/login'); }}>Sign out</button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
