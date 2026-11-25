package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.mudassir.framework.selfheal.SelfHealingLocator;
import io.qameta.allure.Step;

public class LoginPage extends BasePage {

    private static final String LOGIN_BUTTON = "button[data-qa='login-button']";

    private final SelfHealingLocator heal;

    public LoginPage(Page page) {
        super(page);
        this.heal = new SelfHealingLocator(page);
    }

    @Step("Open login page")
    public LoginPage open() {
        navigate(page, "/login");
        return this;
    }

    @Step("Log in as {email}")
    public HomePage login(String email, String password) {
        page.locator("input[data-qa='login-email']").fill(email);
        page.locator("input[data-qa='login-password']").fill(password);
        heal.find("login.submit", LOGIN_BUTTON, "Login button on the login form").click();
        return new HomePage(page);
    }

    @Step("Start signup for {name} / {email}")
    public SignupPage startSignup(String name, String email) {
        page.locator("input[data-qa='signup-name']").fill(name);
        page.locator("input[data-qa='signup-email']").fill(email);
        page.locator("button[data-qa='signup-button']").click();
        return new SignupPage(page);
    }

    /** Plain locator for assertions: a heal must never make a missing button look present. */
    public Locator loginButton() {
        return page.locator(LOGIN_BUTTON);
    }

    public Locator loginError() {
        return page.locator("form[action='/login'] p");
    }

    public Locator signupError() {
        return page.locator("form[action='/signup'] p");
    }
}
