package com.mudassir.tests.ui;

import com.mudassir.framework.pages.CartPage;
import com.mudassir.framework.pages.ProductsPage;
import com.mudassir.tests.base.BaseUiTest;
import io.qameta.allure.Feature;
import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.mudassir.framework.utils.PriceUtils.parseRupees;

@Feature("Cart")
public class CartTest extends BaseUiTest {

    @Test(groups = {"smoke", "ui"}, description = "Two products added from listing appear in cart with correct totals")
    public void addTwoProductsShowsCorrectLineTotals() {
        ProductsPage products = new ProductsPage(page()).open();
        String first = products.nameOf(0);
        String second = products.nameOf(1);

        CartPage cart = products.addToCartAndContinue(0).addToCartAndViewCart(1);

        assertThat(cart.rows()).hasCount(2);
        Assertions.assertThat(cart.nameAt(0)).isEqualTo(first);
        Assertions.assertThat(cart.nameAt(1)).isEqualTo(second);

        for (int row = 0; row < 2; row++) {
            int price = parseRupees(cart.priceAt(row));
            int quantity = Integer.parseInt(cart.quantityAt(row));
            int total = parseRupees(cart.totalAt(row));
            Assertions.assertThat(total).as("line total for row %d", row).isEqualTo(price * quantity);
        }
    }

    @Test(groups = {"regression", "ui"}, description = "Quantity chosen on product details is kept in cart")
    public void quantityFromDetailsPageIsKept() {
        CartPage cart = new ProductsPage(page()).open()
                .viewProduct(0)
                .addToCartWithQuantity(4);

        assertThat(cart.rows()).hasCount(1);
        Assertions.assertThat(cart.quantityAt(0)).isEqualTo("4");
        Assertions.assertThat(parseRupees(cart.totalAt(0))).isEqualTo(parseRupees(cart.priceAt(0)) * 4);
    }

    @Test(groups = {"regression", "ui"}, description = "Removing the only product empties the cart")
    public void removingLastProductEmptiesCart() {
        CartPage cart = new ProductsPage(page()).open().addToCartAndViewCart(0);
        assertThat(cart.rows()).hasCount(1);

        cart.removeRow(0);
        assertThat(cart.emptyCartMessage()).isVisible();
        assertThat(cart.emptyCartMessage()).containsText("Cart is empty!");
    }
}
