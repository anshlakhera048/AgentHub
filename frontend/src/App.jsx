import React, { useState, useEffect } from 'react';
import ChatInterface from './components/ChatInterface';
import AgentSelector from './components/AgentSelector';
import SessionPanel from './components/SessionPanel';
import { api } from './services/api';
import './App.css';

export default function App() {
  const [agents, setAgents] = useState([]);
  const [selectedAgent, setSelectedAgent] = useState('');
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.getAgents()
      .then((data) => {
        setAgents(data);
        if (data.length > 0) setSelectedAgent(data[0].name);
      })
      .catch((err) => setError('Failed to load agents: ' + err.message));
  }, []);

  const handleNewSession = async () => {
    if (!selectedAgent) return;
    try {
      setLoading(true);
      setError(null);
      const session = await api.createSession(selectedAgent);
      setSessionId(session.id);
      setMessages([]);
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

  const handleLoadSession = async (sid) => {
    try {
      setLoading(true);
      const history = await api.getSessionHistory(sid);
      setSessionId(sid);
      setMessages(history);
    } catch (err) {
      setError('Failed to load session: ' + err.message);
    } finally {
      setLoading(false);
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
          <SessionPanel onLoadSession={handleLoadSession} />
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
