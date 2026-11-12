package com.mudassir.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Loads config/{env}.properties from the classpath.
 * Any key can be overridden at runtime with a JVM system property, e.g. -Dbrowser=firefox -Dheadless=false
 */
public final class ConfigManager {

    private static final String ENV = System.getProperty("env", "prod");
    private static final Properties PROPS = load();

    private ConfigManager() {
    }

    private static Properties load() {
        String path = "config/" + ENV + ".properties";
        Properties props = new Properties();
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Config file not found on classpath: " + path);
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
        return props;
    }

    public static String get(String key) {
        String value = System.getProperty(key, PROPS.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing config key '" + key + "' for env '" + ENV + "'");
        }
        return value.trim();
    }

    /** Optional key: returns null when not set anywhere. */
    public static String getOptional(String key) {
        String value = System.getProperty(key, PROPS.getProperty(key));
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static boolean getBool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public static String env() {
        return ENV;
    }

    public static String baseUrl() {
        return get("base.url");
    }

    public static String browser() {
        return get("browser").toLowerCase();
    }

    /** WebSocket endpoint of a remote Playwright server (Docker), e.g. ws://playwright-server:3000/ */
    public static String browserWsEndpoint() {
        return getOptional("browser.ws");
    }

    public static boolean headless() {
        return getBool("headless");
    }

    public static double slowMo() {
        return Double.parseDouble(get("slow.mo"));
    }

    public static int timeoutMs() {
        return getInt("default.timeout.ms");
    }

    public static int navigationTimeoutMs() {
        return getInt("navigation.timeout.ms");
    }

    public static int retryCount() {
        return getInt("retry.count");
    }

    public static boolean traceOnFailure() {
        return getBool("trace.on.failure");
    }
}
