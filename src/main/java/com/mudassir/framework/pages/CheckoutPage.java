package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

public class CheckoutPage extends BasePage {

    public CheckoutPage(Page page) {
        super(page);
    }

    public Locator deliveryAddress() {
        return page.locator("#address_delivery");
    }

    public Locator billingAddress() {
        return page.locator("#address_invoice");
    }

    @Step("Add order comment and place order")
    public PaymentPage placeOrder(String comment) {
        page.locator("textarea[name='message']").fill(comment);
        page.locator("a[href='/payment']").click();
        return new PaymentPage(page);
    }
}
