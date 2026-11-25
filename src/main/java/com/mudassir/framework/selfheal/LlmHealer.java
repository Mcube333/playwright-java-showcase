package com.mudassir.framework.selfheal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Asks Claude to pick which candidate element the broken step meant.
 * Enabled only when ANTHROPIC_API_KEY is set. Model: -Dselfheal.model or SELFHEAL_MODEL env.
 * Text from the page is PII-masked before it leaves the machine.
 */
public class LlmHealer {

    public record Decision(int index, double confidence, String reason) {}

    private static final String SYSTEM = """
            You repair broken UI test locators.
            You get: the goal of the test step, the broken selector, the element's last known attributes \
            (may be null), and a numbered list of visible elements on the current page.
            Pick the ONE candidate the step intends to interact with. Prefer same purpose and label over same position.
            If no candidate clearly matches, return index -1. Never guess between two similar elements.
            Respond with ONLY JSON, no markdown: {"index": <int>, "confidence": <0..1>, "reason": "<max 15 words>"}""";

    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(\\+?91[ -]?)?[6-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern LONG_NUM = Pattern.compile("\\d{6,}");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public LlmHealer(ObjectMapper mapper) {
        this.mapper = mapper;
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
        String envModel = System.getenv("SELFHEAL_MODEL");
        this.model = System.getProperty("selfheal.model", envModel != null ? envModel : "claude-haiku-5-5");
    }

    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Optional<Decision> choose(String goal, String brokenSelector, ElementFingerprint old,
                                     List<Candidate> candidates) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goal", goal);
        payload.put("brokenSelector", brokenSelector);
        payload.put("lastKnownElement", old == null ? null : mask(old));
        payload.put("candidates", candidates.stream()
                .map(c -> Map.<String, Object>of("index", c.idx, "element", mask(c.fp)))
                .toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 300);
        body.put("system", SYSTEM);
        body.put("messages", List.of(Map.of("role", "user", "content", mapper.writeValueAsString(payload))));

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("LLM call failed: HTTP " + res.statusCode() + " " + res.body());
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode block : mapper.readTree(res.body()).path("content")) {
            if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
        }
        String json = text.toString().replaceAll("```(json)?", "").trim();
        JsonNode d = mapper.readTree(json);

        int index = d.path("index").asInt(-1);
        if (index < 0 || index >= candidates.size()) return Optional.empty();
        return Optional.of(new Decision(index, d.path("confidence").asDouble(0), d.path("reason").asText("")));
    }

    private ElementFingerprint mask(ElementFingerprint fp) {
        ElementFingerprint m = mapper.convertValue(fp, ElementFingerprint.class);
        m.text = maskText(m.text);
        m.ariaLabel = maskText(m.ariaLabel);
        m.placeholder = maskText(m.placeholder);
        m.href = maskText(m.href);
        return m;
    }

    private static String maskText(String s) {
        if (s == null || s.isEmpty()) return s;
        s = EMAIL.matcher(s).replaceAll("[EMAIL]");
        s = MOBILE.matcher(s).replaceAll("[PHONE]");
        return LONG_NUM.matcher(s).replaceAll("[NUM]");
    }
}
