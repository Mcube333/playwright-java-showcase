package com.mudassir.tests.demo;

import com.mudassir.framework.pages.LoginPage;
import com.mudassir.tests.base.BaseUiTest;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Fails on purpose, to demo the CI failure-triage job. Only the demo-failure suite runs it
 * (Actions > CI > Run workflow with demo_failure ticked); no regular suite scans this package.
 */
@Feature("Failure triage demo")
public class DemoFailureTest extends BaseUiTest {

    @Test(description = "Intentionally failing: expects the login button to say 'Sign in' (it says 'Login')")
    public void loginButtonSaysSignIn() {
        LoginPage loginPage = new LoginPage(page()).open();
        assertThat(loginPage.loginButton()).hasText("Sign in");
    }
}
