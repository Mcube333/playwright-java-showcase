package com.mudassir.framework.driver;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.mudassir.framework.config.ConfigManager;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * One isolated Playwright session per test thread.
 * Playwright objects are not thread-safe, so every thread gets its own
 * Playwright -> Browser -> BrowserContext -> Page chain via ThreadLocal.
 */
public final class PlaywrightFactory {

    /** Third-party ad/consent hosts that slow the demo site down and cause flaky overlays. */
    private static final Pattern BLOCKED_HOSTS = Pattern.compile(
            ".*(googlesyndication|doubleclick|googleadservices|adservice\\.google|"
                    + "googletagservices|fundingchoicesmessages|google-analytics).*");

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();
    private static final ThreadLocal<APIRequestContext> API = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TRACING = ThreadLocal.withInitial(() -> false);

    private PlaywrightFactory() {
    }

    public static void startPlaywright() {
        if (PLAYWRIGHT.get() == null) {
            PLAYWRIGHT.set(Playwright.create());
        }
    }

    public static Page startBrowser() {
        startPlaywright();
        Playwright playwright = PLAYWRIGHT.get();

        BrowserType browserType = switch (ConfigManager.browser()) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };

        String wsEndpoint = ConfigManager.browserWsEndpoint();
        Browser browser = wsEndpoint != null
                ? connectWithRetry(browserType, wsEndpoint)
                : browserType.launch(new BrowserType.LaunchOptions()
                        .setHeadless(ConfigManager.headless())
                        .setSlowMo(ConfigManager.slowMo()));

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(ConfigManager.baseUrl())
                .setViewportSize(1440, 900));
        context.setDefaultTimeout(ConfigManager.timeoutMs());
        context.setDefaultNavigationTimeout(ConfigManager.navigationTimeoutMs());
        context.route(BLOCKED_HOSTS, route -> route.abort());

        if (ConfigManager.traceOnFailure()) {
            context.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(false));
            TRACING.set(true);
        }

        Page page = context.newPage();
        BROWSER.set(browser);
        CONTEXT.set(context);
        PAGE.set(page);
        return page;
    }

    /**
     * Remote mode: browsers run in a separate Playwright server container (the Playwright
     * equivalent of a Selenium Grid node). Retries while the server container is starting.
     */
    private static Browser connectWithRetry(BrowserType browserType, String wsEndpoint) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 15; attempt++) {
            try {
                return browserType.connect(wsEndpoint);
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(2000); // only used while waiting for infrastructure, never inside tests
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Could not connect to Playwright server at " + wsEndpoint, last);
    }

    public static Page page() {
        Page page = PAGE.get();
        if (page == null) {
            throw new IllegalStateException("No browser session on this thread. Did the test extend BaseUiTest?");
        }
        return page;
    }

    public static boolean hasPage() {
        return PAGE.get() != null;
    }

    /** API context shares the thread's Playwright instance; created lazily. */
    public static APIRequestContext api() {
        if (API.get() == null) {
            startPlaywright();
            API.set(PLAYWRIGHT.get().request().newContext(new APIRequest.NewContextOptions()
                    .setBaseURL(ConfigManager.baseUrl())
                    .setTimeout(ConfigManager.timeoutMs())));
        }
        return API.get();
    }

    /** Stops tracing. Pass a path to keep the trace (failed test) or null to discard it (passed test). */
    public static void stopTracing(Path saveTo) {
        BrowserContext context = CONTEXT.get();
        if (context == null || !TRACING.get()) {
            return;
        }
        if (saveTo != null) {
            context.tracing().stop(new Tracing.StopOptions().setPath(saveTo));
        } else {
            context.tracing().stop();
        }
        TRACING.set(false);
    }

    public static void quit() {
        try {
            if (API.get() != null) API.get().dispose();
            if (CONTEXT.get() != null) CONTEXT.get().close();
            if (BROWSER.get() != null) BROWSER.get().close();
            if (PLAYWRIGHT.get() != null) PLAYWRIGHT.get().close();
        } finally {
            API.remove();
            PAGE.remove();
            CONTEXT.remove();
            BROWSER.remove();
            PLAYWRIGHT.remove();
            TRACING.remove();
        }
    }
}
