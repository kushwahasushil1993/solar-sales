const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inrCompact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1 });
const num = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });

export const money = (v) => (v == null ? '—' : inr.format(v));
export const moneyCompact = (v) => (v == null ? '—' : inrCompact.format(v));
export const number = (v) => (v == null ? '—' : num.format(v));

export function date(v) {
  if (!v) return '—';
  const d = new Date(v.length === 10 ? `${v}T00:00:00` : v);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function dateTime(v) {
  if (!v) return '—';
  return new Date(v).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function monthLabel(yyyyMm) {
  const [y, m] = yyyyMm.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'short', year: '2-digit' });
}

// "IN_PROGRESS" → "In progress"
export const titleCase = (s) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : '');

/** What the user typed in an "Email or mobile number" field: 'email', 'phone' or null. */
export function identifierKind(value) {
  const v = (value || '').trim();
  if (!v) return null;
  if (/[@a-z]/i.test(v)) return 'email';
  if (/^[+\d][\d\s().-]*$/.test(v)) return 'phone';
  return null;
}

/** Enough digits to be a mobile number (10 for India, more with a country code). */
export const looksLikeFullPhone = (value) => (value || '').replace(/\D/g, '').length >= 10;

export const PAYMENT_STATUSES = ['PAID', 'PARTIAL', 'PENDING'];
export const INSTALLATION_STATUSES = ['PENDING', 'SCHEDULED', 'IN_PROGRESS', 'INSTALLED'];
export const PANEL_TYPES = ['Monocrystalline', 'Polycrystalline', 'Bifacial', 'Thin-Film'];
