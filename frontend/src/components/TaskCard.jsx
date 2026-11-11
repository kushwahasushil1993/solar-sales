import Badge from './Badge.jsx';
import { date, dateTime, number } from '../format.js';

const TASK_LABEL = { OPEN: 'Open', ASSIGNED: 'Waiting for acceptance', ACCEPTED: 'Accepted' };

/** An installation job as agents see it (no prices). `actions` renders the buttons. */
export default function TaskCard({ task: t, actions }) {
  return (
    <article className="card install-card">
      <div className="install-head">
        <div>
          <div className="install-name">{t.customerName}</div>
          <div className="muted small mono">{t.invoiceNumber}</div>
        </div>
        <Badge value={t.installationStatus} kind="install" />
      </div>
      <dl className="task-facts">
        <div><dt>Location</dt><dd>{[t.city, t.state].filter(Boolean).join(', ') || '—'}</dd></div>
        <div><dt>Phone</dt><dd>{t.phone ? <a href={`tel:${t.phone}`} className="link">{t.phone}</a> : '—'}</dd></div>
        <div>
          <dt>System</dt>
          <dd>{number(t.systemCapacityKw)} kW {t.panelType || ''}{t.panelCount ? ` · ${t.panelCount} panels` : ''}{t.inverterBrand ? ` · ${t.inverterBrand}` : ''}</dd>
        </div>
        <div><dt>Ordered</dt><dd>{date(t.purchaseDate)}</dd></div>
        <div><dt>Installation</dt><dd>{t.installationDate ? date(t.installationDate) : 'Not scheduled'}</dd></div>
      </dl>
      <div className="install-foot">
        <div className="muted small">
          {t.taskState !== 'OPEN' && <span className={`task-chip task-${t.taskState.toLowerCase()}`}>{TASK_LABEL[t.taskState]}</span>}
          {t.installationUpdatedBy && <div>Updated by {t.installationUpdatedBy} · {dateTime(t.installationUpdatedAt)}</div>}
        </div>
        <div className="task-actions">{actions}</div>
      </div>
    </article>
  );
}
