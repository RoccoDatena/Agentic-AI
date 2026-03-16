package com.techfolio.agent.controller;

import com.techfolio.agent.model.AnalyzeRequest;
import com.techfolio.agent.model.AnalyzeResponse;
import com.techfolio.agent.service.AgentPipelineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class AnalyzeController {
    private final AgentPipelineService pipelineService;

    public AnalyzeController(AgentPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return pipelineService.analyze(request);
    }
}
