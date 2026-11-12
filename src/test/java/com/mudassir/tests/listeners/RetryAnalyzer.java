package com.mudassir.tests.listeners;

import com.mudassir.framework.config.ConfigManager;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/** Retries a failed test up to retry.count times. Traces/screenshots are kept for every failed attempt. */
public class RetryAnalyzer implements IRetryAnalyzer {

    private int attempts = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (attempts < ConfigManager.retryCount()) {
            attempts++;
            return true;
        }
        return false;
    }
}
