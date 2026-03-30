const API_BASE = '/api';

async function request(url, options = {}) {
  const res = await fetch(`${API_BASE}${url}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(error.message || `Request failed: ${res.status}`);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  getAgents: () => request('/agents'),

  executeAgent: (agentName, input, sessionId, chainAgents = null, parameters = {}) =>
    request('/agents/execute', {
      method: 'POST',
      body: JSON.stringify({ agentName, input, sessionId, chainAgents, parameters }),
    }),

  createSession: (agentName, userId = 'default') =>
    request('/sessions', {
      method: 'POST',
      body: JSON.stringify({ agentName, userId }),
    }),

  getSessionHistory: (sessionId) => request(`/sessions/${sessionId}/history`),

  getUserSessions: (userId = 'default') => request(`/sessions?userId=${userId}`),

  deleteSession: (sessionId) =>
    request(`/sessions/${sessionId}`, { method: 'DELETE' }),
};
