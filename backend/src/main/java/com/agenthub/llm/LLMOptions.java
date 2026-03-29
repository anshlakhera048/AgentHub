package com.agenthub.llm;

public record LLMOptions(
        String model,
        double temperature,
        int maxTokens,
        double topP,
        int timeoutSeconds
) {
    public static LLMOptions defaults() {
        return new LLMOptions("mistral", 0.7, 2048, 0.9, 120);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model = "mistral";
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private double topP = 0.9;
        private int timeoutSeconds = 120;

        public Builder model(String model) { this.model = model; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder topP(double topP) { this.topP = topP; return this; }
        public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }

        public LLMOptions build() {
            return new LLMOptions(model, temperature, maxTokens, topP, timeoutSeconds);
        }
    }
}
