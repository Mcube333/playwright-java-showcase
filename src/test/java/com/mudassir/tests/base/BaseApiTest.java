package com.mudassir.tests.base;

import com.mudassir.framework.api.AutomationExerciseApi;
import com.mudassir.framework.driver.PlaywrightFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/** API-only tests: Playwright APIRequestContext, no browser launched. */
public abstract class BaseApiTest {

    @BeforeMethod(alwaysRun = true)
    public void startApi() {
        PlaywrightFactory.startPlaywright();
    }

    @AfterMethod(alwaysRun = true)
    public void stopApi() {
        PlaywrightFactory.quit();
    }

    protected AutomationExerciseApi api() {
        return new AutomationExerciseApi(PlaywrightFactory.api());
    }
}
