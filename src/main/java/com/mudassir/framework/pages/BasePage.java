package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import io.qameta.allure.Step;

/**
 * Shared header navigation. Test waits are Playwright auto-waits; the only sleep is the bounded backoff in
 * {@link #navigate(Page, String)}, used while the third-party demo site answers 502/503/504.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    private static final int NAV_ATTEMPTS = 4;

    /** page.navigate that retries when the demo site is overloaded ("queue full" 503) instead of failing the test. */
    protected static void navigate(Page page, String path) {
        for (int attempt = 1; attempt <= NAV_ATTEMPTS; attempt++) {
            Response response = page.navigate(path);
            int status = response == null ? 200 : response.status();
            if (status != 502 && status != 503 && status != 504) {
                return;
            }
            if (attempt < NAV_ATTEMPTS) {
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    protected Locator header() {
        return page.locator("header");
    }

    public Locator loggedInAs() {
        return header().locator("a:has-text('Logged in as')");
    }

    public Locator accountDeletedHeading() {
        return page.locator("h2[data-qa='account-deleted']");
    }

    @Step("Go to Products")
    public ProductsPage goToProducts() {
        header().locator("a[href='/products']").click();
        return new ProductsPage(page);
    }

    @Step("Go to Cart")
    public CartPage goToCart() {
        header().locator("a[href='/view_cart']").click();
        return new CartPage(page);
    }

    @Step("Go to Signup / Login")
    public LoginPage goToLogin() {
        header().locator("a[href='/login']").click();
        return new LoginPage(page);
    }

    @Step("Go to Contact Us")
    public ContactUsPage goToContactUs() {
        header().locator("a[href='/contact_us']").click();
        return new ContactUsPage(page);
    }

    @Step("Log out")
    public LoginPage logout() {
        header().locator("a[href='/logout']").click();
        return new LoginPage(page);
    }

    @Step("Delete account from header")
    public BasePage deleteAccount() {
        header().locator("a[href='/delete_account']").click();
        return this;
    }
}
