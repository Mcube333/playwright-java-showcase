package com.mudassir.tests.selfheal;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.mudassir.framework.selfheal.SelfHealingLocator;
import io.qameta.allure.Feature;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs offline (no site, no API key): proves the heuristic heal, the cache, and SUGGEST mode. */
@Feature("Self-healing locators")
public class SelfHealingDemoTest {

    private Playwright playwright;
    private Browser browser;

    static final String KEY = "login.submit";
    static final String SELECTOR = "button[data-qa='login-button']";
    static final String DESCRIPTION = "Login button on the login form";

    static final String V1 = "<form class='login-form'>"
            + "<input data-qa='login-email' placeholder='Email Address'>"
            + "<button class='btn'>Signup</button>"
            + "<button data-qa='login-button' class='btn btn-default'>Login</button></form>";
    // Dev renamed the data-qa attribute and moved the button
    static final String V2 = "<form class='login-form'>"
            + "<input data-qa='login-email' placeholder='Email Address'>"
            + "<button data-qa='login-submit' class='btn btn-default'>Login</button>"
            + "<button class='btn'>Signup</button></form>";

    @BeforeClass(alwaysRun = true)
    public void start() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test(groups = {"regression", "selfheal"}, description = "A renamed data-qa is healed by fingerprint, then reused from cache")
    public void healsRenamedTestIdAndReusesCache() throws Exception {
        Path dir = Files.createTempDirectory("selfheal-");
        Page page = browser.newPage();
        try {
            SelfHealingLocator heal = new SelfHealingLocator(page, SelfHealingLocator.Mode.HEAL, 1000, dir);

            page.setContent(V1);
            heal.find(KEY, SELECTOR, DESCRIPTION).click(); // stores the fingerprint

            page.setContent(V2);
            Locator healed = heal.find(KEY, SELECTOR, DESCRIPTION);
            assertThat(healed.innerText()).isEqualTo("Login");
            healed.click();

            page.setContent(V2);
            assertThat(heal.find(KEY, SELECTOR, DESCRIPTION).innerText()).isEqualTo("Login");

            String report = Files.readString(dir.resolve("heal-report.jsonl"));
            assertThat(report).contains("HEURISTIC").contains("CACHE");
        } finally {
            page.close();
        }
    }

    @Test(groups = {"regression", "selfheal"}, description = "SUGGEST mode fails the step but names the fix")
    public void suggestModeFailsWithFix() throws Exception {
        Path dir = Files.createTempDirectory("selfheal-");
        Page page = browser.newPage();
        try {
            page.setContent(V1);
            new SelfHealingLocator(page, SelfHealingLocator.Mode.HEAL, 1000, dir).find(KEY, SELECTOR, DESCRIPTION);

            page.setContent(V2);
            SelfHealingLocator suggest = new SelfHealingLocator(page, SelfHealingLocator.Mode.SUGGEST, 1000, dir);
            assertThatThrownBy(() -> suggest.find(KEY, SELECTOR, DESCRIPTION))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("login-submit");
        } finally {
            page.close();
        }
    }
}
