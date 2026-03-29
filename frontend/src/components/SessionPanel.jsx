import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import './SessionPanel.css';

export default function SessionPanel({ onLoadSession }) {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(false);

  const loadSessions = async () => {
    try {
      setLoading(true);
      const data = await api.getUserSessions();
      setSessions(data);
    } catch {
      // Silently fail — sessions panel is non-critical
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  return (
    <div className="session-panel">
      <div className="session-header">
        <span className="label">Sessions</span>
        <button className="btn-refresh" onClick={loadSessions} disabled={loading}>
          ↻
        </button>
      </div>
      <div className="session-list">
        {sessions.length === 0 && (
          <p className="no-sessions">No sessions yet</p>
        )}
        {sessions.map((session) => (
          <button
            key={session.id}
            className="session-item"
            onClick={() => onLoadSession(session.id)}
          >
            <span className="session-agent">{session.agentName}</span>
            <span className="session-date">
              {new Date(session.createdAt).toLocaleDateString()}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
