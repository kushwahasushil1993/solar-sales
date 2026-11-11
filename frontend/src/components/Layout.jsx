import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { api } from '../api.js';
import PasswordModal from './PasswordModal.jsx';
import { useAuth } from '../auth.jsx';

const ADMIN = ['ADMIN'];
const STAFF = ['ADMIN', 'USER'];
const NAV = [
  { to: '/', label: 'Dashboard', icon: '▦', end: true, roles: STAFF },
  { to: '/sales', label: 'Sales Records', icon: '☰', roles: STAFF },
  { to: '/installations', label: 'Installation Status', icon: '⚡', roles: STAFF },
  { to: '/tasks', label: 'My Tasks', icon: '✔', end: true, roles: ['AGENT'] },
  { to: '/tasks/available', label: 'Available Tasks', icon: '🙋', roles: ['AGENT'] },
  { to: '/inbox', label: 'Notifications', icon: '🔔', roles: ['ADMIN', 'AGENT'], badge: true },
  { to: '/assign', label: 'Assign Agents', icon: '📌', roles: ADMIN },
  { to: '/agents', label: 'Agents', icon: '🧑‍🔧', roles: ADMIN },
  { to: '/upload', label: 'Upload Purchases', icon: '⇪', roles: ADMIN },
  { to: '/notifications', label: 'Customer Emails', icon: '✉', roles: ADMIN },
  { to: '/users', label: 'Users', icon: '👥', roles: ADMIN },
];

const ROLE_LABEL = { ADMIN: 'Admin', USER: 'Sales user', AGENT: 'Agent', PENDING: 'Waiting for approval' };

const TITLES = {
  '/': ['Dashboard', 'Overview of solar sales, revenue and installations'],
  '/sales': ['Sales Records', 'Search, add and review solar sales'],
  '/installations': ['Installation Status', 'Track each customer’s installation progress'],
  '/upload': ['Upload Purchases', 'Import purchase records from an Excel sheet'],
  '/users': ['Users', 'Manage accounts and admin access'],
  '/tasks': ['My Tasks', 'Installation jobs you picked up or accepted'],
  '/tasks/available': ['Available Tasks', 'Open installation jobs you can pick up'],
  '/inbox': ['Notifications', 'Task assignments and updates'],
  '/agents': ['Agents', 'Installation agents, their workload and activity'],
  '/assign': ['Assign Agents', 'Give new installation jobs to agents'],
  '/notifications': ['Customer Emails', 'Invoices and installation updates sent to customers'],
};

const initials = (name) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');

export default function Layout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user, isAdmin, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [unread, setUnread] = useState(0);
  const hasInbox = user.role === 'ADMIN' || user.role === 'AGENT';

  // unread notifications for the bell: on navigation, every 30 s, and when the inbox changes
  useEffect(() => {
    if (!hasInbox) return undefined;
    const refresh = () => api.inbox().then((d) => setUnread(d.unread)).catch(() => {});
    refresh();
    const timer = setInterval(refresh, 30000);
    window.addEventListener('inbox-changed', refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener('inbox-changed', refresh);
    };
  }, [hasInbox, pathname]);
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
          {NAV.filter((n) => n.roles.includes(user.role)).map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="nav-link" onClick={() => setOpen(false)}>
              <span className="nav-icon">{n.icon}</span>
              {n.label}
              {n.badge && unread > 0 && <span className="nav-badge">{unread > 99 ? '99+' : unread}</span>}
            </NavLink>
          ))}
        </nav>
        {isAdmin && (
          <a className="nav-link nav-external" href="/swagger-ui.html" target="_blank" rel="noreferrer">
            <span className="nav-icon">{'{}'}</span>
            API docs ↗
          </a>
        )}
        <div className="sidebar-foot">v0.3 · React + Spring Boot</div>
      </aside>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}

      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-btn" onClick={() => setOpen(true)} aria-label="Open menu">☰</button>
          <div className="topbar-title">
            <h1>{title}</h1>
            <p className="muted">{subtitle}</p>
          </div>
          {hasInbox && (
            <NavLink to="/inbox" className="bell" aria-label={`Notifications${unread ? ` (${unread} unread)` : ''}`}>
              🔔{unread > 0 && <span className="bell-count">{unread > 99 ? '99+' : unread}</span>}
            </NavLink>
          )}
          <div className="user-chip">
            {user.avatarUrl
              ? <img className="avatar" src={user.avatarUrl} alt="" referrerPolicy="no-referrer" />
              : <span className="avatar">{initials(user.fullName)}</span>}
            <div className="user-meta">
              <div className="user-name">{user.fullName}</div>
              <span className={`role role-${user.role.toLowerCase()}`}>{ROLE_LABEL[user.role] || user.role}</span>
            </div>
            {user.email && (
              <button className="btn btn-ghost" onClick={() => setChangingPassword(true)}>
                {user.passwordLogin ? 'Change password' : 'Set password'}
              </button>
            )}
            <button className="btn btn-ghost" onClick={async () => { await logout(); navigate('/login'); }}>Sign out</button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
        {changingPassword && (
          <PasswordModal
            title={user.passwordLogin ? 'Change password' : 'Set a password'}
            intro={user.passwordLogin ? null : `Lets you sign in with ${user.email} and a password.`}
            askCurrent={user.passwordLogin}
            onClose={() => setChangingPassword(false)}
            onSubmit={async (current, next) => {
              await api.changePassword(current, next);
              // a password change signs out every session, including this one
              setChangingPassword(false);
              await logout();
              navigate('/login', { replace: true, state: { message: 'Password changed. Sign in with your new password.' } });
            }}
          />
        )}
      </div>
    </div>
  );
}
