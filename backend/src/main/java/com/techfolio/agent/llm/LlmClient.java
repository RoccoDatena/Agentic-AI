package com.techfolio.agent.llm;

public interface LlmClient {
    boolean isEnabled();
    String complete(String systemPrompt, String userPrompt);
    String provider();
}
