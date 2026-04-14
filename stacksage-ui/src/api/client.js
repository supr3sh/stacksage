const BASE = '/api/v1';

async function request(url, options = {}) {
  const res = await fetch(`${BASE}${url}`, options);
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw Object.assign(new Error(body.error || `HTTP ${res.status}`), { status: res.status, body });
  }
  if (res.status === 204) return null;
  return res.json();
}

export function uploadFile(file, retain = false, sessionId = null) {
  const form = new FormData();
  form.append('file', file);
  const headers = {};
  if (sessionId) headers['X-Session-Id'] = sessionId;
  return request(`/uploads?retain=${retain}`, { method: 'POST', body: form, headers });
}

export function getUpload(id, includeContent = false) {
  return request(`/uploads/${id}?content=${includeContent}`);
}

export function deleteUpload(id) {
  return request(`/uploads/${id}`, { method: 'DELETE' });
}

export function getAnalysisByUpload(uploadId) {
  return request(`/uploads/${uploadId}/analysis`);
}

export function getAnalysis(analysisId) {
  return request(`/analyses/${analysisId}`);
}
