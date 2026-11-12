package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

import java.util.List;

public class ProductsPage extends BasePage {

    public ProductsPage(Page page) {
        super(page);
    }

    @Step("Open products page")
    public ProductsPage open() {
        page.navigate("/products");
        return this;
    }

    public Locator title() {
        return page.locator(".features_items h2.title");
    }

    public Locator productCards() {
        return page.locator(".features_items .productinfo");
    }

    public List<String> productNames() {
        return page.locator(".features_items .productinfo p").allTextContents();
    }

    public String priceOf(int index) {
        return productCards().nth(index).locator("h2").textContent().trim();
    }

    public String nameOf(int index) {
        return productCards().nth(index).locator("p").textContent().trim();
    }

    @Step("Search for '{term}'")
    public ProductsPage search(String term) {
        page.locator("#search_product").fill(term);
        page.locator("#submit_search").click();
        return this;
    }

    @Step("Add product #{index} to cart and continue shopping")
    public ProductsPage addToCartAndContinue(int index) {
        clickAddToCart(index);
        page.locator("#cartModal button.close-modal").click();
        page.locator("#cartModal").waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
        return this;
    }

    @Step("Add product #{index} to cart and view cart")
    public CartPage addToCartAndViewCart(int index) {
        clickAddToCart(index);
        page.locator("#cartModal a[href='/view_cart']").click();
        return new CartPage(page);
    }

    @Step("Open product #{index} details")
    public ProductDetailsPage viewProduct(int index) {
        page.locator(".features_items a[href^='/product_details/']").nth(index).click();
        return new ProductDetailsPage(page);
    }

    private void clickAddToCart(int index) {
        Locator card = productCards().nth(index);
        card.scrollIntoViewIfNeeded();
        card.locator("a.add-to-cart").click();
        page.locator("#cartModal").waitFor(); // condition-based wait for the modal
    }
}
