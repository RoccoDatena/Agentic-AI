package com.techfolio.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techfolio.agent.llm.LlmClient;
import com.techfolio.agent.model.ActionItem;
import com.techfolio.agent.model.AnalyzeRequest;
import com.techfolio.agent.model.AnalyzeResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentPipelineService {
    private static final Pattern ACTION_PATTERN = Pattern.compile("(?i)(todo|to do|azione|action|da fare|next step)[:\\-]?\\s*(.+)");
    private static final Pattern OWNER_PATTERN = Pattern.compile("(?i)(owner|assegnat[oa]|responsabile)[:\\-]?\\s*([^,;.]+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(?i)(entro|due|scadenza|by)[:\\-]?\\s*([0-9]{1,2}/[0-9]{1,2}(?:/[0-9]{2,4})?)");

    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentPipelineService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        String transcript = request.transcript();
        String language = request.language() == null || request.language().isBlank() ? "it" : request.language();

        if (llmClient.isEnabled()) {
            AnalyzeResponse fromLlm = tryLlm(transcript, language);
            if (fromLlm != null) {
                return fromLlm;
            }
        }

        return analyzeHeuristic(transcript, "heuristic");
    }

    private AnalyzeResponse tryLlm(String transcript, String language) {
        String systemPrompt = "You are a meeting-notes agent. Return ONLY valid JSON with keys: summary (array of strings), " +
                "actionItems (array of {task, owner, dueDate}), risks (array of strings), followUpEmail (string). " +
                "Use language: " + language + ".";

        String userPrompt = "Analyze the following meeting notes and produce the JSON.\n\n" + transcript;

        try {
            String raw = llmClient.complete(systemPrompt, userPrompt);
            AnalyzeResponse parsed = mapper.readValue(raw, AnalyzeResponse.class);
            return new AnalyzeResponse(
                    safeList(parsed.summary()),
                    safeActions(parsed.actionItems()),
                    safeList(parsed.risks()),
                    safeText(parsed.followUpEmail()),
                    "llm:" + llmClient.provider()
            );
        } catch (JsonProcessingException ex) {
            return analyzeHeuristic(transcript, "heuristic-fallback");
        } catch (Exception ex) {
            return analyzeHeuristic(transcript, "heuristic-fallback");
        }
    }

    private AnalyzeResponse analyzeHeuristic(String transcript, String pipelineLabel) {
        List<String> summary = summarize(transcript, 6);
        List<ActionItem> actionItems = extractActions(transcript);
        List<String> risks = extractRisks(transcript);
        String email = buildFollowUpEmail(summary, actionItems, risks);

        return new AnalyzeResponse(summary, actionItems, risks, email, pipelineLabel);
    }

    private List<String> summarize(String transcript, int max) {
        String[] sentences = transcript
                .replace("\n", " ")
                .split("(?<=[.!?])\\s+");

        List<String> result = new ArrayList<>();
        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.length() < 20) {
                continue;
            }
            result.add(s);
            if (result.size() >= max) {
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(transcript.substring(0, Math.min(120, transcript.length())).trim());
        }
        return result;
    }

    private List<ActionItem> extractActions(String transcript) {
        List<ActionItem> items = new ArrayList<>();
        List<String> lines = Arrays.asList(transcript.split("\\r?\\n"));

        for (String line : lines) {
            Matcher actionMatch = ACTION_PATTERN.matcher(line);
            if (!actionMatch.find()) {
                continue;
            }

            String task = actionMatch.group(2).trim();
            String owner = "";
            String due = "";

            Matcher ownerMatch = OWNER_PATTERN.matcher(line);
            if (ownerMatch.find()) {
                owner = ownerMatch.group(2).trim();
            }

            Matcher dateMatch = DATE_PATTERN.matcher(line);
            if (dateMatch.find()) {
                due = dateMatch.group(2).trim();
            }

            if (!task.isBlank()) {
                items.add(new ActionItem(task, owner, due));
            }
        }

        if (items.isEmpty()) {
            items.add(new ActionItem("Condividere i punti chiave con il team", "", ""));
        }

        return items;
    }

    private List<String> extractRisks(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        List<String> risks = new ArrayList<>();
        if (lower.contains("rischio") || lower.contains("risk") || lower.contains("blocco") || lower.contains("dipendenz")) {
            for (String line : transcript.split("\\r?\\n")) {
                if (line.toLowerCase(Locale.ROOT).contains("risch") || line.toLowerCase(Locale.ROOT).contains("blocc") || line.toLowerCase(Locale.ROOT).contains("dipendenz")) {
                    risks.add(line.trim());
                }
            }
        }

        if (risks.isEmpty()) {
            risks.add("Nessun rischio esplicito emerso dagli appunti.");
        }

        return risks;
    }

    private String buildFollowUpEmail(List<String> summary, List<ActionItem> actions, List<String> risks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ciao team,\n\n");
        sb.append("Ecco il riepilogo della riunione di oggi:\n");
        for (String s : summary) {
            sb.append("- ").append(s).append("\n");
        }
        sb.append("\nAzioni:\n");
        for (ActionItem action : actions) {
            sb.append("- ").append(action.task());
            if (action.owner() != null && !action.owner().isBlank()) {
                sb.append(" (Owner: ").append(action.owner()).append(")");
            }
            if (action.dueDate() != null && !action.dueDate().isBlank()) {
                sb.append(" (Scadenza: ").append(action.dueDate()).append(")");
            }
            sb.append("\n");
        }
        sb.append("\nRischi/Blocchi:\n");
        for (String r : risks) {
            sb.append("- ").append(r).append("\n");
        }
        sb.append("\nGrazie a tutti!\n");
        return sb.toString();
    }

    private List<String> safeList(List<String> items) {
        return items == null ? List.of() : items;
    }

    private List<ActionItem> safeActions(List<ActionItem> items) {
        return items == null ? List.of() : items;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}

