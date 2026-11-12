package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

public class CartPage extends BasePage {

    public CartPage(Page page) {
        super(page);
    }

    @Step("Open cart")
    public CartPage open() {
        page.navigate("/view_cart");
        return this;
    }

    public Locator rows() {
        return page.locator("#cart_info_table tbody tr");
    }

    public String nameAt(int row) {
        return rows().nth(row).locator("td.cart_description h4 a").textContent().trim();
    }

    public String priceAt(int row) {
        return rows().nth(row).locator("td.cart_price p").textContent().trim();
    }

    public String quantityAt(int row) {
        return rows().nth(row).locator("td.cart_quantity button").textContent().trim();
    }

    public String totalAt(int row) {
        return rows().nth(row).locator("td.cart_total p.cart_total_price").textContent().trim();
    }

    public Locator emptyCartMessage() {
        return page.locator("#empty_cart");
    }

    @Step("Remove cart row #{row}")
    public CartPage removeRow(int row) {
        rows().nth(row).locator("a.cart_quantity_delete").click();
        return this;
    }

    @Step("Proceed to checkout (logged in)")
    public CheckoutPage proceedToCheckout() {
        page.locator("a.check_out").click();
        return new CheckoutPage(page);
    }

    @Step("Proceed to checkout as guest")
    public CartPage proceedToCheckoutAsGuest() {
        page.locator("a.check_out").click();
        return this;
    }

    public Locator loginPromptModal() {
        return page.locator("#checkoutModal");
    }
}
