package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

public class ProductDetailsPage extends BasePage {

    public ProductDetailsPage(Page page) {
        super(page);
    }

    public Locator name() {
        return page.locator(".product-information h2");
    }

    public Locator price() {
        return page.locator(".product-information span span");
    }

    public Locator info() {
        return page.locator(".product-information");
    }

    @Step("Set quantity {quantity} and add to cart")
    public CartPage addToCartWithQuantity(int quantity) {
        page.locator("#quantity").fill(String.valueOf(quantity));
        page.locator("button.cart").click();
        page.locator("#cartModal a[href='/view_cart']").click();
        return new CartPage(page);
    }
}
