import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import './SessionPanel.css';

export default function SessionPanel({ onLoadSession, onDeleteSession, refreshKey, activeSessionId }) {
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
  }, [refreshKey]);

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
          <div
            key={session.id}
            className={`session-item${session.id === activeSessionId ? ' session-active' : ''}`}
          >
            <button
              className="session-info"
              onClick={() => onLoadSession(session.id, session.agentName)}
            >
              <span className="session-agent">{session.agentName}</span>
              <span className="session-date">
                {new Date(session.createdAt).toLocaleDateString()}
              </span>
            </button>
            <button
              className="btn-delete-session"
              onClick={(e) => {
                e.stopPropagation();
                onDeleteSession(session.id);
              }}
              title="Delete session"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
