import { titleCase } from '../format.js';

const TONES = {
  PAID: 'green',
  PARTIAL: 'amber',
  PENDING: 'red',
  INSTALLED: 'green',
  SCHEDULED: 'blue',
  IN_PROGRESS: 'purple',
  // installation task
  ASSIGNED: 'amber',
  ACCEPTED: 'green',
  OPEN: 'grey',
  // customer email status
  SENT: 'green',
  FAILED: 'red',
  SKIPPED: 'grey',
};

/** Status pill; installation PENDING is shown grey rather than red since it's not an error. */
export default function Badge({ value, kind }) {
  const tone = kind === 'install' && value === 'PENDING' ? 'grey' : TONES[value] || 'grey';
  return <span className={`badge badge-${tone}`}>{titleCase(value)}</span>;
}
