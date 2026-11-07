export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="auth-page">
      <div className="auth-hero">
        <div className="brand">
          <span className="brand-icon">☀</span>
          <div>
            <div className="brand-name">SolarSales</div>
            <div className="brand-sub">Sales &amp; Purchases</div>
          </div>
        </div>
        <div className="auth-hero-copy">
          <h2>Every solar sale, from invoice to installation.</h2>
          <ul>
            <li>Record sales and track payments</li>
            <li>Follow each installation from pending to done</li>
            <li>Admins bulk-import purchase records from Excel</li>
          </ul>
        </div>
      </div>
      <div className="auth-panel">
        <div className="auth-card">
          <h1>{title}</h1>
          <p className="muted">{subtitle}</p>
          {children}
          {footer && <div className="auth-footer">{footer}</div>}
        </div>
      </div>
    </div>
  );
}
