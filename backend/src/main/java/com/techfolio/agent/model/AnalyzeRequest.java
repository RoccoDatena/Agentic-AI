package com.techfolio.agent.model;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeRequest(
        @NotBlank(message = "Transcript is required")
        String transcript,
        String language
) {}
