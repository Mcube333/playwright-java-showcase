package com.mudassir.framework.selfheal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Snapshot of an element's identifying attributes, captured when its locator last worked. */
public class ElementFingerprint {
    public String tag = "";
    public String id = "";
    public String testId = "";
    public String name = "";
    public String role = "";
    public String ariaLabel = "";
    public String text = "";
    public String type = "";
    public String placeholder = "";
    public String href = "";
    public List<String> classes = new ArrayList<>();

    /**
     * Weighted similarity (0..1) between this (old) fingerprint and a candidate.
     * Fields in {@code ignore} are skipped: the attribute used by the broken selector
     * is usually the one that changed, so it shouldn't count against the candidate.
     */
    public double similarity(ElementFingerprint c, Set<String> ignore) {
        Score s = new Score(ignore);
        s.exact("testId", testId, c.testId, 3.0);
        s.exact("id", id, c.id, 2.5);
        s.exact("ariaLabel", ariaLabel, c.ariaLabel, 2.0);
        s.exact("name", name, c.name, 2.0);
        s.text("text", text, c.text, 2.0);
        s.exact("placeholder", placeholder, c.placeholder, 1.5);
        s.exact("role", role, c.role, 1.0);
        s.exact("type", type, c.type, 1.0);
        s.exact("tag", tag, c.tag, 1.0);
        s.exact("href", href, c.href, 1.0);
        if (!ignore.contains("classes") && classes != null && !classes.isEmpty()) {
            Set<String> a = new HashSet<>(classes);
            Set<String> b = c.classes == null ? Set.of() : new HashSet<>(c.classes);
            Set<String> inter = new HashSet<>(a);
            inter.retainAll(b);
            Set<String> union = new HashSet<>(a);
            union.addAll(b);
            s.add(1.0, union.isEmpty() ? 0 : (double) inter.size() / union.size());
        }
        return s.total == 0 ? 0 : s.got / s.total;
    }

    private static final class Score {
        final Set<String> ignore;
        double total;
        double got;

        Score(Set<String> ignore) { this.ignore = ignore; }

        void add(double weight, double ratio) {
            total += weight;
            got += weight * ratio;
        }

        void exact(String field, String a, String b, double w) {
            if (ignore.contains(field) || a == null || a.isEmpty()) return;
            add(w, a.equalsIgnoreCase(Objects.toString(b, "")) ? 1 : 0);
        }

        void text(String field, String a, String b, double w) {
            if (ignore.contains(field) || a == null || a.isEmpty()) return;
            String x = a.trim().toLowerCase();
            String y = Objects.toString(b, "").trim().toLowerCase();
            double r = x.equals(y) ? 1 : (!y.isEmpty() && (x.contains(y) || y.contains(x))) ? 0.5 : 0;
            add(w, r);
        }
    }
}
