package com.agenthub.tools;

import java.util.Map;

/**
 * Abstraction for tools that agents can invoke to interact with
 * external systems, files, APIs, etc.
 */
public interface Tool {

    String getName();

    String getDescription();

    Object execute(Map<String, Object> params);
}
