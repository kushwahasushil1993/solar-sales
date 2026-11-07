const BASE = '/api';

let authToken = null;
let onUnauthorized = () => {};

/** Called by AuthProvider so every request carries the JWT and a 401 signs the user out. */
export function configureAuth(token, unauthorizedHandler) {
  authToken = token;
  if (unauthorizedHandler) onUnauthorized = unauthorizedHandler;
}

function withAuth(options = {}) {
  if (!authToken) return options;
  return { ...options, headers: { ...options.headers, Authorization: `Bearer ${authToken}` } };
}

export class ApiError extends Error {
  constructor(message, status, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || {};
  }
}

async function request(path, options = {}) {
  let res;
  try {
    res = await fetch(BASE + path, withAuth(options));
  } catch {
    throw new ApiError('Cannot reach the server. Is the backend running on port 8081?', 0);
  }
  if (res.status === 401 && authToken && !path.startsWith('/auth/')) {
    onUnauthorized();
  }
  if (!res.ok) {
    let body = {};
    try {
      body = await res.json();
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(body.detail || `Request failed (${res.status})`, res.status, body.errors);
  }
  if (res.status === 204) return null;
  const type = res.headers.get('content-type') || '';
  return type.includes('application/json') ? res.json() : res.blob();
}

function query(params = {}) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.append(k, v);
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

const json = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

async function download(path, fallbackName) {
  const res = await fetch(BASE + path, withAuth());
  if (res.status === 401) onUnauthorized();
  if (!res.ok) throw new ApiError(res.status === 403 ? 'Only an admin can download this' : `Download failed (${res.status})`, res.status);
  const disposition = res.headers.get('content-disposition') || '';
  const match = disposition.match(/filename="?([^";]+)"?/);
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = match ? match[1] : fallbackName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export const api = {
  login: (email, password) => request('/auth/login', json('POST', { email, password })),
  register: (fullName, email, password) => request('/auth/register', json('POST', { fullName, email, password })),
  me: () => request('/auth/me'),
  users: () => request('/users'),
  setUserRole: (id, role) => request(`/users/${id}/role`, json('PUT', { role })),
  setUserEnabled: (id, enabled) => request(`/users/${id}/enabled`, json('PUT', { enabled })),
  dashboard: () => request('/dashboard'),
  listSales: (params) => request('/sales' + query(params)),
  createSale: (sale) => request('/sales', json('POST', sale)),
  updateSale: (id, sale) => request(`/sales/${id}`, json('PUT', sale)),
  deleteSale: (id) => request(`/sales/${id}`, { method: 'DELETE' }),
  uploadExcel: (file, dryRun) => {
    const form = new FormData();
    form.append('file', file);
    return request(`/sales/upload${query({ dryRun })}`, { method: 'POST', body: form });
  },
  uploads: () => request('/uploads'),
  downloadTemplate: () => download('/sales/template', 'solar-purchase-template.xlsx'),
  exportSales: (params) => download('/sales/export' + query(params), 'solar-sales.xlsx'),
};
