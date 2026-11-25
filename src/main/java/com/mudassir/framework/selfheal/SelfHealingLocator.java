package com.mudassir.framework.selfheal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Self-healing wrapper around page.locator().
 *
 * <pre>
 * SelfHealingLocator heal = new SelfHealingLocator(page);
 * heal.find("login.submit", "button[data-qa='login-button']", "Login button on the login form").click();
 * </pre>
 *
 * Flow when the selector fails:
 *  1. CACHE     - a heal found in an earlier run for the same key + selector
 *  2. HEURISTIC - score visible elements against the last known fingerprint (no API call)
 *  3. LLM       - ask Claude to pick the element using the step description
 *  4. otherwise rethrow the original failure
 * Every heal is written to heal-report.jsonl. Heals are suggestions: update the selector in code after review.
 *
 * Config (system properties): selfheal.mode = HEAL | SUGGEST | OFF (default HEAL),
 * selfheal.timeoutMs (default 5000), selfheal.dir (default target/self-healing).
 * Use for action targets, not for elements whose presence is what the test asserts.
 */
public class SelfHealingLocator {

    public enum Mode {
        /** Heal, log, and continue the test. */
        HEAL,
        /** Find the fix and log it, but still fail (use as a CI gate). */
        SUGGEST,
        /** Plain page.locator() behaviour. */
        OFF
    }

    private static final double HEURISTIC_MIN = 0.75;
    private static final double HEURISTIC_MARGIN = 0.15;
    private static final double LLM_MIN_CONFIDENCE = 0.7;
    private static final int MAX_CANDIDATES = 150;

    private static final String FP_FN = """
            el => ({
              tag: el.tagName.toLowerCase(),
              id: el.id || '',
              testId: el.getAttribute('data-testid') || el.getAttribute('data-test-id') || el.getAttribute('data-qa') || '',
              name: el.getAttribute('name') || '',
              role: el.getAttribute('role') || '',
              ariaLabel: el.getAttribute('aria-label') || '',
              text: String(el.innerText || el.value || '').trim().replace(/\\s+/g, ' ').slice(0, 80),
              type: el.getAttribute('type') || '',
              placeholder: el.getAttribute('placeholder') || '',
              href: el.getAttribute('href') || '',
              classes: Array.from(el.classList).slice(0, 8)
            })""";

    private static final String SCAN_JS = """
            (max) => {
              const fp = %s;
              const visible = el => {
                const r = el.getBoundingClientRect();
                const s = getComputedStyle(el);
                return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
              };
              const generated = v => /[0-9]{4,}|^[a-f0-9-]{16,}$|:r[0-9a-z]+:/i.test(v);
              const cssPath = el => {
                const parts = [];
                while (el && el.nodeType === 1 && el !== document.body) {
                  let p = el.tagName.toLowerCase();
                  const same = el.parentElement
                    ? Array.from(el.parentElement.children).filter(c => c.tagName === el.tagName) : [];
                  if (same.length > 1) p += `:nth-of-type(${same.indexOf(el) + 1})`;
                  parts.unshift(p);
                  el = el.parentElement;
                }
                return 'body > ' + parts.join(' > ');
              };
              const selectors = (el, f) => {
                const out = [];
                for (const a of ['data-testid', 'data-test-id', 'data-qa', 'name', 'aria-label', 'placeholder']) {
                  const v = el.getAttribute(a);
                  if (v) out.push(`${f.tag}[${a}=${JSON.stringify(v)}]`);
                }
                if (f.id && !generated(f.id)) out.push(`${f.tag}[id=${JSON.stringify(f.id)}]`);
                if (f.text && f.text.length <= 50) out.push(`${f.tag}:has-text(${JSON.stringify(f.text)})`);
                out.push(cssPath(el));
                return out;
              };
              const interactiveSel = 'a,button,input,select,textarea,summary,[role],[onclick],[tabindex],[data-testid],[data-test-id],[data-qa]';
              const res = [];
              for (const el of document.querySelectorAll(interactiveSel + ',label,h1,h2,h3,h4,h5,h6,p,span,div,li,img')) {
                if (res.length >= max) break;
                if (!visible(el)) continue;
                const ownText = Array.from(el.childNodes).filter(n => n.nodeType === 3)
                  .map(n => n.textContent.trim()).join(' ');
                const keep = el.matches(interactiveSel) || el.tagName === 'IMG'
                  || (ownText.length > 0 && ownText.length <= 60);
                if (!keep) continue;
                const f = fp(el);
                res.push({ idx: res.length, fp: f, selectors: selectors(el, f) });
              }
              return res;
            }""".formatted(FP_FN);

    private final Page page;
    private final Mode mode;
    private final double timeoutMs;
    private final ObjectMapper mapper;
    private final HealingStore store;
    private final LlmHealer llm;

    public SelfHealingLocator(Page page) {
        this(page,
                Mode.valueOf(System.getProperty("selfheal.mode", "HEAL").toUpperCase()),
                Double.parseDouble(System.getProperty("selfheal.timeoutMs", "5000")),
                Path.of(System.getProperty("selfheal.dir", "target/self-healing")));
    }

    public SelfHealingLocator(Page page, Mode mode, double timeoutMs, Path storeDir) {
        this.page = page;
        this.mode = mode;
        this.timeoutMs = timeoutMs;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.store = HealingStore.forDir(storeDir, mapper);
        this.llm = new LlmHealer(mapper);
    }

    /**
     * @param key         stable name for this element, e.g. "login.submit"
     * @param selector    the selector as written in the page object
     * @param description what the element is, in plain words (used by the LLM)
     */
    public Locator find(String key, String selector, String description) {
        Locator primary = page.locator(selector);
        PlaywrightException failure;
        try {
            primary.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            if (mode != Mode.OFF && primary.count() == 1) {
                store.saveFingerprint(key, selector, fingerprint(primary));
            }
            return primary;
        } catch (PlaywrightException e) {
            failure = e;
        }
        if (mode == Mode.OFF) throw failure;

        HealingStore.Entry known = store.get(key);

        // 1. Cached heal from an earlier run
        if (known != null && selector.equals(known.original) && known.healed != null && usable(known.healed)) {
            return healed(key, selector, known.healed, "CACHE", 1.0, "healed in an earlier run", failure);
        }

        List<Candidate> candidates = scan();
        ElementFingerprint old = known == null ? null : known.fingerprint;

        // 2. Heuristic match on the last known fingerprint
        if (old != null) {
            Set<String> ignore = fieldsUsedIn(selector);
            Candidate best = null;
            double bestScore = 0;
            double second = 0;
            for (Candidate c : candidates) {
                double s = old.similarity(c.fp, ignore);
                if (s > bestScore) {
                    second = bestScore;
                    bestScore = s;
                    best = c;
                } else if (s > second) {
                    second = s;
                }
            }
            if (best != null && bestScore >= HEURISTIC_MIN && bestScore - second >= HEURISTIC_MARGIN) {
                Optional<String> sel = uniqueSelector(best);
                if (sel.isPresent()) {
                    return healed(key, selector, sel.get(), "HEURISTIC", bestScore,
                            String.format("fingerprint match (runner-up %.2f)", second), failure);
                }
            }
        }

        // 3. LLM
        if (llm.available()) {
            try {
                Optional<LlmHealer.Decision> d = llm.choose(description, selector, old, candidates);
                if (d.isPresent() && d.get().confidence() >= LLM_MIN_CONFIDENCE) {
                    Optional<String> sel = uniqueSelector(candidates.get(d.get().index()));
                    if (sel.isPresent()) {
                        return healed(key, selector, sel.get(), "LLM", d.get().confidence(), d.get().reason(), failure);
                    }
                }
            } catch (Exception e) {
                log("LLM healing failed for " + key + ": " + e.getMessage());
            }
        }

        store.report(event(key, selector, null, "FAILED", 0, "no confident match"));
        throw failure;
    }

    private Locator healed(String key, String original, String healedSelector, String method,
                           double score, String reason, PlaywrightException failure) {
        Locator loc = page.locator(healedSelector);
        store.report(event(key, original, healedSelector, method, score, reason));
        log(String.format("%s: '%s' -> '%s' via %s (%.2f) %s", key, original, healedSelector, method, score, reason));
        if (mode == Mode.SUGGEST) {
            throw new AssertionError("Broken locator '" + key + "'. Suggested fix: " + healedSelector
                    + " (" + method + ", " + String.format("%.2f", score) + ")", failure);
        }
        store.saveHeal(key, original, healedSelector, fingerprint(loc));
        return loc;
    }

    private Optional<String> uniqueSelector(Candidate c) {
        for (String s : c.selectors) {
            if (usable(s)) return Optional.of(s);
        }
        return Optional.empty();
    }

    private boolean usable(String selector) {
        try {
            Locator l = page.locator(selector);
            return l.count() == 1 && l.isVisible();
        } catch (PlaywrightException e) {
            return false;
        }
    }

    private ElementFingerprint fingerprint(Locator loc) {
        try {
            return mapper.convertValue(loc.evaluate(FP_FN), ElementFingerprint.class);
        } catch (PlaywrightException e) {
            return null;
        }
    }

    private List<Candidate> scan() {
        Object raw = page.evaluate(SCAN_JS, MAX_CANDIDATES);
        return mapper.convertValue(raw, new TypeReference<List<Candidate>>() {});
    }

    /** Attributes the broken selector relied on; likely the ones that changed, so ignored in scoring. */
    static Set<String> fieldsUsedIn(String selector) {
        String s = selector.toLowerCase();
        Set<String> f = new HashSet<>();
        if (s.contains("data-testid") || s.contains("data-test-id") || s.contains("data-qa")) f.add("testId");
        if (s.contains("#") || s.contains("[id")) f.add("id");
        if (s.contains("has-text") || s.contains("text=") || s.contains("text()")) f.add("text");
        if (s.contains("[name")) f.add("name");
        if (s.contains("aria-label")) f.add("ariaLabel");
        if (s.contains("placeholder")) f.add("placeholder");
        if (s.contains("href")) f.add("href");
        if (s.matches(".*(^|[\\s>+~(])[a-z]*\\.[a-z_-].*")) f.add("classes");
        return f;
    }

    private Map<String, Object> event(String key, String original, String healedSelector, String method,
                                      double score, String reason) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("time", Instant.now().toString());
        e.put("key", key);
        e.put("url", page.url());
        e.put("original", original);
        e.put("healed", healedSelector);
        e.put("method", method);
        e.put("score", Math.round(score * 100) / 100.0);
        e.put("reason", reason);
        e.put("mode", mode.name());
        return e;
    }

    private static void log(String msg) {
        System.out.println("[self-heal] " + msg);
    }
}
