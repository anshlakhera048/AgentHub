import React from 'react';
import './AgentSelector.css';

export default function AgentSelector({ agents, selected, onSelect }) {
  return (
    <div className="agent-selector">
      <label className="label">Agent</label>
      <select
        value={selected}
        onChange={(e) => onSelect(e.target.value)}
        className="select"
      >
        {agents.map((agent) => (
          <option key={agent.name} value={agent.name}>
            {agent.name}
          </option>
        ))}
      </select>
      {agents.find((a) => a.name === selected) && (
        <p className="agent-desc">
          {agents.find((a) => a.name === selected).description}
        </p>
      )}
    </div>
  );
}
