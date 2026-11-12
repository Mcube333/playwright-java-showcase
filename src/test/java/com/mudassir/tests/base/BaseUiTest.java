package com.mudassir.tests.base;

import com.microsoft.playwright.Page;
import com.mudassir.framework.api.ApiResult;
import com.mudassir.framework.api.AutomationExerciseApi;
import com.mudassir.framework.config.ConfigManager;
import com.mudassir.framework.data.TestUser;
import com.mudassir.framework.data.UserFactory;
import com.mudassir.framework.driver.PlaywrightFactory;
import io.qameta.allure.Allure;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Every test method gets a fresh, isolated browser session on its own thread.
 * Tests must use page() rather than a field so parallel methods never share a Page.
 */
public abstract class BaseUiTest {

    @BeforeMethod(alwaysRun = true)
    public void startSession() {
        PlaywrightFactory.startBrowser();
    }

    @AfterMethod(alwaysRun = true)
    public void endSession(ITestResult result) {
        try {
            Path trace = null;
            if (!result.isSuccess() && ConfigManager.traceOnFailure()) {
                trace = Paths.get("target", "traces",
                        result.getMethod().getMethodName() + "-" + System.currentTimeMillis() + ".zip");
            }
            if (trace != null) {
                Files.createDirectories(trace.getParent());
            }
            PlaywrightFactory.stopTracing(trace);
            if (trace != null && Files.exists(trace)) {
                try (var in = Files.newInputStream(trace)) {
                    Allure.addAttachment("Playwright trace (open with: npx playwright show-trace)",
                            "application/zip", in, "zip");
                }
            }
        } catch (IOException e) {
            Allure.addAttachment("Trace error", e.toString());
        } finally {
            PlaywrightFactory.quit();
        }
    }

    protected Page page() {
        return PlaywrightFactory.page();
    }

    protected AutomationExerciseApi api() {
        return new AutomationExerciseApi(PlaywrightFactory.api());
    }

    /** Fast, deterministic setup: create the user through the API instead of the UI. */
    protected TestUser createUserViaApi() {
        TestUser user = UserFactory.newUser();
        ApiResult result = api().createAccount(user);
        if (result.responseCode() != 201) {
            throw new IllegalStateException("Test setup failed, could not create user: " + result.body());
        }
        return user;
    }

    protected void deleteUserViaApi(TestUser user) {
        api().deleteAccount(user.email(), user.password());
    }
}
