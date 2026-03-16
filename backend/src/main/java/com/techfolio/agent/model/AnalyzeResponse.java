package com.techfolio.agent.model;

import java.util.List;

public record AnalyzeResponse(
        List<String> summary,
        List<ActionItem> actionItems,
        List<String> risks,
        String followUpEmail,
        String pipeline
) {}
