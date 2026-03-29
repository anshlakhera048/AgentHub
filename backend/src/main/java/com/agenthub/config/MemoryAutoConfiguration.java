package com.agenthub.config;

import com.agenthub.memory.LongTermMemory;
import com.agenthub.memory.NoOpLongTermMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for memory subsystem fallbacks.
 * Ensures a NoOpLongTermMemory bean exists when no other LongTermMemory is configured.
 */
@Configuration
public class MemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LongTermMemory.class)
    public NoOpLongTermMemory noOpLongTermMemory() {
        return new NoOpLongTermMemory();
    }
}
