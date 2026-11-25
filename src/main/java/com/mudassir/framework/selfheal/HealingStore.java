package com.mudassir.framework.selfheal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps, per locator key: the original selector, the last known fingerprint and the healed selector.
 * Files (in the store dir):
 *   locators.json     - fingerprints + healed selectors (reused across runs)
 *   heal-report.jsonl - one line per heal/failed heal, for human review
 * One instance per directory per JVM; methods are synchronized for parallel threads.
 */
public final class HealingStore {

    public static class Entry {
        public String original;
        public String healed;
        public ElementFingerprint fingerprint;
    }

    private static final Map<Path, HealingStore> INSTANCES = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;
    private final Path storeFile;
    private final Path reportFile;
    private final Map<String, Entry> entries;

    public static HealingStore forDir(Path dir, ObjectMapper mapper) {
        return INSTANCES.computeIfAbsent(dir.toAbsolutePath().normalize(), d -> new HealingStore(d, mapper));
    }

    private HealingStore(Path dir, ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.storeFile = dir.resolve("locators.json");
        this.reportFile = dir.resolve("heal-report.jsonl");
        try {
            Files.createDirectories(dir);
            this.entries = Files.exists(storeFile)
                    ? new TreeMap<>(this.mapper.readValue(storeFile.toFile(), new TypeReference<Map<String, Entry>>() {}))
                    : new TreeMap<>();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot init self-healing store at " + dir, e);
        }
    }

    public synchronized Entry get(String key) {
        return entries.get(key);
    }

    /** Called when the original selector works: refresh fingerprint and drop any stale heal. */
    public synchronized void saveFingerprint(String key, String selector, ElementFingerprint fp) {
        Entry e = entries.computeIfAbsent(key, k -> new Entry());
        boolean changed = !selector.equals(e.original) || e.healed != null || !sameFp(e.fingerprint, fp);
        if (!changed) return;
        e.original = selector;
        e.healed = null;
        e.fingerprint = fp;
        persist();
    }

    public synchronized void saveHeal(String key, String original, String healed, ElementFingerprint fp) {
        Entry e = entries.computeIfAbsent(key, k -> new Entry());
        e.original = original;
        e.healed = healed;
        if (fp != null) e.fingerprint = fp;
        persist();
    }

    public synchronized void report(Map<String, Object> event) {
        try {
            Files.writeString(reportFile, mapper.copy().disable(SerializationFeature.INDENT_OUTPUT)
                            .writeValueAsString(event) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[self-heal] could not write report: " + e.getMessage());
        }
    }

    private boolean sameFp(ElementFingerprint a, ElementFingerprint b) {
        if (a == null || b == null) return a == b;
        return Objects.equals(mapper.valueToTree(a), mapper.valueToTree(b));
    }

    private void persist() {
        try {
            Path tmp = storeFile.resolveSibling(storeFile.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), entries);
            Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[self-heal] could not save store: " + e.getMessage());
        }
    }
}
