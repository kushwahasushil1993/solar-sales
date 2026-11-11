const BASE = '/api';

let onUnauthorized = () => {};

/** Called by AuthProvider: a 401 from any API call (other than the initial /auth/me check) signs the user out. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

// The backend sets XSRF-TOKEN as a readable cookie; it must be echoed on POST/PUT/DELETE.
function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function withCsrf(options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const token = csrfToken();
  if (method === 'GET' || method === 'HEAD' || !token) return options;
  return { ...options, headers: { ...options.headers, 'X-XSRF-TOKEN': token } };
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
    res = await fetch(BASE + path, withCsrf(options));
  } catch {
    throw new ApiError('Cannot reach the server. Is the backend running on port 8081?', 0);
  }
  if (res.status === 401 && !['/auth/me', '/auth/login', '/auth/otp/verify', '/auth/otp/register'].includes(path)) {
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
  const res = await fetch(BASE + path);
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
  providers: () => request('/auth/providers'),
  // first administrator on a fresh install (refused once any admin exists)
  setupStatus: () => request('/setup'),
  setupAdmin: (fullName, email, password) => request('/setup/admin', json('POST', { fullName, email, password })),
  // forgot password: always 202 (no hint whether the email exists); the email links to /reset-password?token=
  forgotPassword: (email) => request('/auth/password/forgot', json('POST', { email })),
  checkResetToken: (token) => request(`/auth/password/reset${query({ token })}`),
  resetPassword: (token, newPassword) => request('/auth/password/reset', json('POST', { token, newPassword })),
  changePassword: (currentPassword, newPassword) =>
    request('/account/password', json('PUT', { currentPassword: currentPassword || null, newPassword })),
  resetUserPassword: (id, newPassword) => request(`/users/${id}/password`, json('PUT', { newPassword })),
  me: () => request('/auth/me'),
  // requestedRole: 'USER' (sales team) or 'AGENT'; only a request, an admin approves the role
  register: (fullName, email, password, requestedRole) =>
    request('/auth/register', json('POST', { fullName, email, password, requestedRole })),
  // Spring Security form login: form-encoded body, 204 on success
  login: (email, password) => request('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ email, password }),
  }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  // mobile sign-in: any number gets a code. verifyOtp → null (signed in) or
  // { status: 'REGISTRATION_REQUIRED', phone, maskedPhone } for a new number → completePhoneRegistration
  requestOtp: (phone) => request('/auth/otp/request', json('POST', { phone })),
  verifyOtp: (phone, code) => request('/auth/otp/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ phone, code }),
  }),
  completePhoneRegistration: (fullName, email, requestedRole) => request('/auth/otp/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ fullName, email: email || '', requestedRole: requestedRole || '' }),
  }),
  users: () => request('/users'),
  setUserRole: (id, role) => request(`/users/${id}/role`, json('PUT', { role })),
  setUserEnabled: (id, enabled) => request(`/users/${id}/enabled`, json('PUT', { enabled })),
  dashboard: () => request('/dashboard'),
  listSales: (params) => request('/sales' + query(params)),
  // notify: email the customer (invoice on create; installation/payment updates on edit)
  createSale: (sale, notify = true) => request(`/sales${query({ notify })}`, json('POST', sale)),
  updateSale: (id, sale, notify = true) => request(`/sales/${id}${query({ notify })}`, json('PUT', sale)),
  deleteSale: (id) => request(`/sales/${id}`, { method: 'DELETE' }),
  // installation progress only (sales users and admins)
  updateInstallation: (id, body, notify = true) =>
    request(`/sales/${id}/installation${query({ notify })}`, json('PATCH', body)),
  uploadExcel: (file, dryRun, emailInvoices = false) => {
    const form = new FormData();
    form.append('file', file);
    return request(`/sales/upload${query({ dryRun, emailInvoices })}`, { method: 'POST', body: form });
  },
  saleNotifications: (id) => request(`/sales/${id}/notifications`),
  sendInvoice: (id) => request(`/sales/${id}/notifications/invoice`, { method: 'POST' }),
  notifications: (params) => request('/notifications' + query(params)),
  // installation tasks (agents) and assignment (admins)
  availableTasks: () => request('/tasks/available'),
  myTasks: () => request('/tasks/mine'),
  pickTask: (id) => request(`/tasks/${id}/pick`, { method: 'POST' }),
  acceptTask: (id) => request(`/tasks/${id}/accept`, { method: 'POST' }),
  updateTaskStatus: (id, body, notify = true) => request(`/tasks/${id}/status${query({ notify })}`, json('PATCH', body)),
  assignTask: (id, agentId) => request(`/tasks/${id}/assign`, json('POST', { agentId })),
  taskBoard: (state, search) => request('/tasks/board' + query({ state, search })),
  assignMany: (saleIds, agentId) => request('/tasks/assign', json('POST', { saleIds, agentId })),
  unassignTask: (id) => request(`/tasks/${id}/unassign`, { method: 'POST' }),
  agents: () => request('/agents'),
  agentActivity: (id) => request(`/agents/${id}/activity`),
  activity: () => request('/activity'),
  // in-app notifications (bell)
  inbox: () => request('/inbox'),
  openNotification: (id) => request(`/inbox/${id}/open`, { method: 'POST' }),
  readAllNotifications: () => request('/inbox/read-all', { method: 'POST' }),
  resendNotification: (id) => request(`/notifications/${id}/resend`, { method: 'POST' }),
  uploads: () => request('/uploads'),
  downloadTemplate: () => download('/sales/template', 'solar-purchase-template.xlsx'),
  exportSales: (params) => download('/sales/export' + query(params), 'solar-sales.xlsx'),
};
