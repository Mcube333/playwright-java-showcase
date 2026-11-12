package com.mudassir.tests.ui;

import com.mudassir.framework.data.Card;
import com.mudassir.framework.data.TestUser;
import com.mudassir.framework.pages.CartPage;
import com.mudassir.framework.pages.CheckoutPage;
import com.mudassir.framework.pages.HomePage;
import com.mudassir.framework.pages.LoginPage;
import com.mudassir.framework.pages.PaymentPage;
import com.mudassir.framework.pages.ProductsPage;
import com.mudassir.tests.base.BaseUiTest;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Feature("Checkout & Payment")
public class CheckoutTest extends BaseUiTest {

    @Test(groups = {"smoke", "ui"}, description = "Logged-in user can place an order end to end")
    public void loggedInUserCanPlaceOrder() {
        TestUser user = createUserViaApi(); // API setup, UI verification
        try {
            HomePage home = new LoginPage(page()).open().login(user.email(), user.password());
            assertThat(home.loggedInAs()).containsText(user.name());

            CartPage cart = home.goToProducts().addToCartAndViewCart(0);
            CheckoutPage checkout = cart.proceedToCheckout();

            assertThat(checkout.deliveryAddress()).containsText(user.address1());
            assertThat(checkout.deliveryAddress()).containsText(user.city());
            assertThat(checkout.billingAddress()).containsText(user.address1());

            PaymentPage payment = checkout.placeOrder("Automated order - please ignore").pay(Card.dummy());
            assertThat(payment.orderPlacedHeading()).hasText("Order Placed!");
        } finally {
            deleteUserViaApi(user);
        }
    }

    @Test(groups = {"regression", "ui"}, description = "Guest is asked to register or log in at checkout")
    public void guestIsAskedToLoginAtCheckout() {
        CartPage cart = new ProductsPage(page()).open().addToCartAndViewCart(0);
        cart.proceedToCheckoutAsGuest();

        assertThat(cart.loginPromptModal()).isVisible();
        assertThat(cart.loginPromptModal()).containsText("Register / Login");
    }
}
