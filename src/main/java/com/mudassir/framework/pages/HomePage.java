package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

public class HomePage extends BasePage {

    public HomePage(Page page) {
        super(page);
    }

    @Step("Open home page")
    public HomePage open() {
        navigate(page, "/");
        return this;
    }

    @Step("Subscribe with {email}")
    public HomePage subscribe(String email) {
        page.locator("#susbscribe_email").fill(email); // id is misspelled on the site itself
        page.locator("#subscribe").click();
        return this;
    }

    public Locator subscribeSuccess() {
        return page.locator("#success-subscribe");
    }
}
