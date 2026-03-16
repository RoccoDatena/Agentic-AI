package com.techfolio.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatCompletionsClient implements LlmClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl = env("LLM_BASE_URL");
    private final String apiKey = env("LLM_API_KEY");
    private final String model = env("LLM_MODEL");

    @Override
    public boolean isEnabled() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            throw new IllegalStateException("LLM is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        try {
            String payload = mapper.writeValueAsString(body);
            String endpoint = normalizeBaseUrl(baseUrl) + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("LLM error: HTTP " + response.statusCode() + " - " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("LLM response missing content");
            }
            return content.asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call failed", e);
        }
    }

    @Override
    public String provider() {
        return "chat-completions";
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeBaseUrl(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
