const API_BASE = import.meta.env.VITE_CMS_API_BASE || 'http://localhost:8090';
const TOKEN_KEY = 'p2p_cms_token';
const ADMIN_KEY = 'p2p_cms_admin';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredAdmin() {
  const raw = localStorage.getItem(ADMIN_KEY);
  return raw ? JSON.parse(raw) : null;
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ADMIN_KEY);
}

export async function login(username, password) {
  const data = await request('/cms/auth/login', {
    method: 'POST',
    body: { username, password },
    auth: false
  });
  localStorage.setItem(TOKEN_KEY, data.accessToken);
  localStorage.setItem(ADMIN_KEY, JSON.stringify(data.admin));
  return data.admin;
}

export function fetchStats() {
  return request('/cms/dashboard/stats');
}

export function fetchChart() {
  return request('/cms/dashboard/chart');
}

export function fetchUsers(params = {}) {
  return request(`/cms/users?${toQuery(params)}`);
}

export function fetchLoans(params = {}) {
  return request(`/cms/loans?${toQuery(params)}`);
}

export function decideKyc(userId, decision, reason = '') {
  return request(`/cms/users/${userId}/kyc`, {
    method: 'PUT',
    body: { decision, reason }
  });
}

export function updateUserStatus(userId, status) {
  return request(`/cms/users/${userId}/status`, {
    method: 'PUT',
    body: { status }
  });
}

export function approveLoan(loanId, note = '') {
  return request(`/cms/loans/${loanId}/approve`, {
    method: 'PUT',
    body: { reason: note }
  });
}

export function rejectLoan(loanId, note = '') {
  return request(`/cms/loans/${loanId}/reject`, {
    method: 'PUT',
    body: { reason: note }
  });
}

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (options.auth !== false && getToken()) {
    headers.Authorization = `Bearer ${getToken()}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  if (res.status === 401) {
    clearSession();
  }

  if (!res.ok) {
    const detail = await readError(res);
    throw new Error(detail || `HTTP ${res.status}`);
  }

  if (res.status === 204) {
    return null;
  }
  return res.json();
}

async function readError(res) {
  try {
    const data = await res.json();
    return data.detail || data.title;
  } catch {
    return res.statusText;
  }
}

function toQuery(params) {
  return new URLSearchParams(
    Object.entries(params)
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => [key, String(value)])
  ).toString();
}
