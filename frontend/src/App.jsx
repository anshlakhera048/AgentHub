import React, { useState, useEffect } from 'react';
import ChatInterface from './components/ChatInterface';
import AgentSelector from './components/AgentSelector';
import SessionPanel from './components/SessionPanel';
import { api } from './services/api';
import './App.css';

export default function App() {
  const [agents, setAgents] = useState([]);
  const [selectedAgent, setSelectedAgent] = useState('');
  const [sessionId, setSessionId] = useState(() => {
    return localStorage.getItem('agenthub_sessionId') || null;
  });
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [sessionsKey, setSessionsKey] = useState(0);

  useEffect(() => {
    api.getAgents()
      .then((data) => {
        setAgents(data);
        if (data.length > 0 && !selectedAgent) setSelectedAgent(data[0].name);
      })
      .catch((err) => setError('Failed to load agents: ' + err.message));
  }, []);

  // Restore session history on mount if a sessionId was persisted
  useEffect(() => {
    if (sessionId) {
      api.getSessionHistory(sessionId)
        .then((history) => {
          setMessages(history);
          const savedAgent = localStorage.getItem('agenthub_agentName');
          if (savedAgent) setSelectedAgent(savedAgent);
        })
        .catch(() => {
          // Session no longer exists in DB — clear stale reference
          localStorage.removeItem('agenthub_sessionId');
          localStorage.removeItem('agenthub_agentName');
          setSessionId(null);
          setMessages([]);
        });
    }
  }, []); // run once on mount

  const handleNewSession = async () => {
    if (!selectedAgent) return;
    try {
      setLoading(true);
      setError(null);
      const session = await api.createSession(selectedAgent);
      setSessionId(session.id);
      localStorage.setItem('agenthub_sessionId', session.id);
      localStorage.setItem('agenthub_agentName', selectedAgent);
      setMessages([]);
      setSessionsKey((k) => k + 1);
    } catch (err) {
      setError('Failed to create session: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSend = async (input) => {
    if (!input.trim() || !selectedAgent) return;

    const userMsg = { role: 'user', content: input, timestamp: new Date().toISOString() };
    setMessages((prev) => [...prev, userMsg]);
    setLoading(true);
    setError(null);

    try {
      const response = await api.executeAgent(selectedAgent, input, sessionId);
      const assistantMsg = {
        role: 'assistant',
        content: response.output || response.errorMessage || 'No response',
        agentName: response.agentName,
        latencyMs: response.latencyMs,
        success: response.success,
        timestamp: response.timestamp,
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err) {
      setError(err.message);
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', content: 'Error: ' + err.message, success: false, timestamp: new Date().toISOString() },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleLoadSession = async (sid, agentName) => {
    try {
      setLoading(true);
      const history = await api.getSessionHistory(sid);
      setSessionId(sid);
      setMessages(history);
      if (agentName) setSelectedAgent(agentName);
      localStorage.setItem('agenthub_sessionId', sid);
      if (agentName) localStorage.setItem('agenthub_agentName', agentName);
    } catch (err) {
      setError('Failed to load session: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteSession = async (sid) => {
    try {
      await api.deleteSession(sid);
      // If deleting the active session, clear it
      if (sid === sessionId) {
        setSessionId(null);
        setMessages([]);
        localStorage.removeItem('agenthub_sessionId');
        localStorage.removeItem('agenthub_agentName');
      }
      setSessionsKey((k) => k + 1);
    } catch (err) {
      setError('Failed to delete session: ' + err.message);
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>🤖 AgentHub</h1>
        <span className="subtitle">Multi-Agent AI Orchestration Platform</span>
      </header>

      <div className="app-body">
        <aside className="sidebar">
          <AgentSelector
            agents={agents}
            selected={selectedAgent}
            onSelect={setSelectedAgent}
          />
          <button className="btn-primary" onClick={handleNewSession} disabled={loading}>
            + New Session
          </button>
          <SessionPanel
            onLoadSession={handleLoadSession}
            onDeleteSession={handleDeleteSession}
            refreshKey={sessionsKey}
            activeSessionId={sessionId}
          />
        </aside>

        <main className="main-content">
          {error && <div className="error-banner">{error}</div>}
          <ChatInterface
            messages={messages}
            onSend={handleSend}
            loading={loading}
            agentName={selectedAgent}
            sessionId={sessionId}
          />
        </main>
      </div>
    </div>
  );
}
