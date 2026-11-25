package triage;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classifies failed tests with Claude and writes a markdown table to the GitHub job summary.
 *
 * Usage: TriageFailures <results-dir> <heals-dir> <out-dir>
 *   results-dir  downloaded "test-results-<browser>" artifacts (Surefire TEST-*.xml)
 *   heals-dir    downloaded "self-healing-<browser>" artifacts (heal-report.jsonl), may be missing
 *   out-dir      receives triage.md and triage.json
 *
 * Without ANTHROPIC_API_KEY it prints a notice and exits 0. If the API call fails, the failures are still
 * listed (unclassified) and a warning is printed, so the triage never hides the original test failures.
 */
public class TriageFailures {

    static final String MODEL = "claude-sonnet-5-5";
    static final int STACK_LINES = 30;
    static final int MESSAGE_CHARS = 4000;

    public enum Category { TEST_BUG, PRODUCT_BUG, ENVIRONMENT, FLAKY }

    public record Verdict(
            @JsonPropertyDescription("The id of the failure being classified, copied from the input") String id,
            Category category,
            @JsonPropertyDescription("One line: why this category") String reason,
            @JsonPropertyDescription("One line: the concrete change that would fix it") String suggestedFix) {}

    public record Triage(List<Verdict> verdicts) {}

    record Failure(String id, String browser, String test, String message, String stackTrace) {}

    static final String SYSTEM = """
            You triage failed end-to-end tests from a Playwright + TestNG suite that runs against \
            automationexercise.com, a public demo e-commerce site that is often slow or overloaded (502/503/504).
            For each failure, choose exactly one category:
            - TEST_BUG: the test or page object is wrong (bad selector, wrong expected value, broken test logic).
            - PRODUCT_BUG: the site behaves incorrectly and the test caught it.
            - ENVIRONMENT: infrastructure, network, browser startup, CI runner or site availability problems.
            - FLAKY: timing or ordering issue that would likely pass on rerun with no code change.
            Self-healing heal events, if present, list locators that broke during the run; use them as evidence.
            Give a one-line reason and a one-line suggested fix for every failure, keyed by its id.""";

    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: TriageFailures <results-dir> <heals-dir> <out-dir>");
            System.exit(2);
        }
        Path resultsDir = Path.of(args[0]);
        Path healsDir = Path.of(args[1]);
        Path outDir = Path.of(args[2]);

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("::notice title=Failure triage::ANTHROPIC_API_KEY secret is not set, skipping triage");
            return;
        }

        List<Failure> failures = collectFailures(resultsDir);
        Map<String, List<JsonNode>> heals = collectHeals(healsDir);
        Files.createDirectories(outDir);

        if (failures.isEmpty()) {
            write(outDir, "## Failure triage\n\nNo failed tests found in the Surefire reports.\n", Map.of());
            return;
        }

        Map<String, Verdict> verdicts = Map.of();
        String error = null;
        try {
            verdicts = classify(failures, heals);
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            System.out.println("::warning title=Failure triage::Claude classification failed, listing failures unclassified. " + oneLine(error));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Failure f : failures) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("browser", f.browser());
            row.put("test", f.test());
            row.put("message", f.message());
            row.put("verdict", verdicts.get(f.id()));
            rows.add(row);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("model", MODEL);
        json.put("error", error);
        json.put("failures", rows);
        write(outDir, markdown(failures, verdicts, error), json);
    }

    // ---- input ---------------------------------------------------------------------------------

    static List<Failure> collectFailures(Path resultsDir) throws Exception {
        if (!Files.isDirectory(resultsDir)) return List.of();
        List<Path> reports;
        try (Stream<Path> s = Files.walk(resultsDir)) {
            reports = s.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList();
        }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Map<String, Failure> byKey = new LinkedHashMap<>();
        for (Path report : reports) {
            String browser = browserOf(resultsDir, report);
            Document doc = factory.newDocumentBuilder().parse(report.toFile());
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element tc = (Element) cases.item(i);
                Element problem = firstChild(tc, "failure").or(() -> firstChild(tc, "error")).orElse(null);
                if (problem == null) continue;
                String test = shortClass(tc.getAttribute("classname")) + "." + tc.getAttribute("name");
                String key = browser + "|" + test;
                if (byKey.containsKey(key)) continue; // same test in the suite-level and class-level report
                String message = problem.getAttribute("message");
                if (message.isBlank()) message = problem.getAttribute("type");
                byKey.put(key, new Failure("F" + (byKey.size() + 1), browser, test,
                        cap(message, MESSAGE_CHARS), firstLines(problem.getTextContent(), STACK_LINES)));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    static Map<String, List<JsonNode>> collectHeals(Path healsDir) throws IOException {
        Map<String, List<JsonNode>> out = new LinkedHashMap<>();
        if (!Files.isDirectory(healsDir)) return out;
        List<Path> files;
        try (Stream<Path> s = Files.walk(healsDir)) {
            files = s.filter(p -> p.getFileName().toString().equals("heal-report.jsonl")).sorted().toList();
        }
        for (Path f : files) {
            List<JsonNode> events = new ArrayList<>();
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    events.add(JSON.readTree(line));
                } catch (IOException e) {
                    System.out.println("Skipping unreadable heal-report line in " + f + ": " + e.getMessage());
                }
            }
            if (!events.isEmpty()) out.put(browserOf(healsDir, f), events);
        }
        return out;
    }

    /** "results/test-results-firefox/TEST-x.xml" -> "firefox"; falls back to the first sub-directory name. */
    static String browserOf(Path root, Path file) {
        Path rel = root.relativize(file);
        if (rel.getNameCount() < 2) return "unknown";
        String dir = rel.getName(0).toString();
        int dash = dir.lastIndexOf('-');
        return dash >= 0 ? dir.substring(dash + 1) : dir;
    }

    // ---- Claude ----------------------------------------------------------------------------------

    static Map<String, Verdict> classify(List<Failure> failures, Map<String, List<JsonNode>> heals) throws IOException {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("failures", failures);
        input.put("healEventsByBrowser", heals);

        AnthropicClient client = AnthropicOkHttpClient.fromEnv();
        StructuredMessageCreateParams<Triage> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(SYSTEM)
                .outputConfig(Triage.class)
                // Server-side fallback: if a safety classifier declines, the API retries on a suitable model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage("Classify these test failures:\n" + JSON.writeValueAsString(input))
                .build();

        StructuredMessage<Triage> response = client.messages().create(params);
        String stop = response.stopReason().map(Object::toString).orElse("");
        if (stop.equalsIgnoreCase("refusal")) {
            throw new IllegalStateException("Claude declined to classify (stop_reason=refusal)");
        }
        if (stop.equalsIgnoreCase("max_tokens")) {
            throw new IllegalStateException("response hit max_tokens before the JSON was complete");
        }
        Triage triage = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("response had no text block"));
        return triage.verdicts().stream()
                .collect(Collectors.toMap(Verdict::id, v -> v, (a, b) -> a, LinkedHashMap::new));
    }

    // ---- output ----------------------------------------------------------------------------------

    static String markdown(List<Failure> failures, Map<String, Verdict> verdicts, String error) {
        StringBuilder md = new StringBuilder("## Failure triage\n\n");
        if (error != null) {
            md.append("> Classification unavailable: ").append(cell(error)).append("\n\n");
        } else {
            md.append("Classified by `").append(MODEL).append("`. Treat as a starting point, not a verdict.\n\n");
        }
        md.append("| Browser | Test | Category | Reason | Suggested fix |\n|---|---|---|---|---|\n");
        for (Failure f : failures) {
            Verdict v = verdicts.get(f.id());
            md.append("| ").append(cell(f.browser()))
              .append(" | `").append(cell(f.test())).append('`')
              .append(" | ").append(v == null ? "UNCLASSIFIED" : "**" + v.category() + "**")
              .append(" | ").append(v == null ? cell(f.message()) : cell(v.reason()))
              .append(" | ").append(v == null ? "" : cell(v.suggestedFix()))
              .append(" |\n");
        }
        return md.toString();
    }

    static void write(Path outDir, String markdown, Map<String, Object> json) throws IOException {
        Files.writeString(outDir.resolve("triage.md"), markdown, StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve("triage.json"), JSON.writeValueAsString(json), StandardCharsets.UTF_8);
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), markdown + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.out.println(markdown);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    static Optional<Element> firstChild(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? Optional.empty() : Optional.of((Element) nodes.item(0));
    }

    static String shortClass(String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    static String firstLines(String text, int n) {
        return text.strip().lines().limit(n).collect(Collectors.joining("\n"));
    }

    static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + " ...[truncated]";
    }

    static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Safe for a markdown table cell: single line, no pipes, bounded length. */
    static String cell(String s) {
        if (s == null) return "";
        return cap(oneLine(s), 300).replace("|", "\\|").replace("`", "'");
    }
}
