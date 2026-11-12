package com.mudassir.tests.listeners;

import com.microsoft.playwright.Page;
import com.mudassir.framework.driver.PlaywrightFactory;
import io.qameta.allure.Allure;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;

/** Attaches a full-page screenshot and the current URL to Allure when a UI test fails. */
public class FailureListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        attach();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        // A retried attempt is reported as skipped; keep its evidence too.
        if (result.wasRetried()) {
            attach();
        }
    }

    private void attach() {
        if (!PlaywrightFactory.hasPage()) {
            return; // API test: nothing to screenshot
        }
        try {
            Page page = PlaywrightFactory.page();
            byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment("Failure screenshot", "image/png", new ByteArrayInputStream(png), "png");
            Allure.addAttachment("Failure URL", page.url());
        } catch (Exception e) {
            Allure.addAttachment("Screenshot error", e.toString());
        }
    }
}
