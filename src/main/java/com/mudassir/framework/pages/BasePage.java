package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

/** Shared header navigation. All waits are Playwright auto-waits; no Thread.sleep anywhere. */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
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
