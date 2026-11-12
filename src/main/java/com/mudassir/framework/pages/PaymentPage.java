package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.mudassir.framework.data.Card;
import io.qameta.allure.Step;

public class PaymentPage extends BasePage {

    public PaymentPage(Page page) {
        super(page);
    }

    @Step("Pay with dummy card")
    public PaymentPage pay(Card card) {
        page.locator("input[data-qa='name-on-card']").fill(card.nameOnCard());
        page.locator("input[data-qa='card-number']").fill(card.number());
        page.locator("input[data-qa='cvc']").fill(card.cvc());
        page.locator("input[data-qa='expiry-month']").fill(card.expiryMonth());
        page.locator("input[data-qa='expiry-year']").fill(card.expiryYear());
        page.locator("button[data-qa='pay-button']").click();
        return this;
    }

    public Locator orderPlacedHeading() {
        return page.locator("h2[data-qa='order-placed']");
    }
}
