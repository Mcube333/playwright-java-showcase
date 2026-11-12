package com.mudassir.tests.ui;

import com.mudassir.framework.data.TestUser;
import com.mudassir.framework.pages.HomePage;
import com.mudassir.framework.pages.LoginPage;
import com.mudassir.tests.base.BaseUiTest;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Feature("Login")
public class LoginTest extends BaseUiTest {

    @Test(groups = {"smoke", "ui"}, description = "Registered user can log in and log out")
    public void registeredUserCanLoginAndLogout() {
        TestUser user = createUserViaApi();
        try {
            HomePage home = new LoginPage(page()).open().login(user.email(), user.password());
            assertThat(home.loggedInAs()).containsText(user.name());

            LoginPage loginPage = home.logout();
            assertThat(loginPage.loginButton()).isVisible();
            assertThat(loginPage.loggedInAs()).isHidden();
        } finally {
            deleteUserViaApi(user);
        }
    }

    @Test(groups = {"regression", "ui"}, description = "Wrong password shows an error and does not log in")
    public void wrongPasswordShowsError() {
        TestUser user = createUserViaApi();
        try {
            LoginPage loginPage = new LoginPage(page()).open();
            loginPage.login(user.email(), "wrong-" + user.password());

            assertThat(loginPage.loginError()).containsText("Your email or password is incorrect!");
            assertThat(loginPage.loggedInAs()).isHidden();
        } finally {
            deleteUserViaApi(user);
        }
    }

    @Test(groups = {"regression", "ui"}, description = "Signing up with an existing email is rejected")
    public void existingEmailCannotSignUpAgain() {
        TestUser user = createUserViaApi();
        try {
            LoginPage loginPage = new LoginPage(page()).open();
            loginPage.startSignup(user.name(), user.email());

            assertThat(loginPage.signupError()).containsText("Email Address already exist!");
        } finally {
            deleteUserViaApi(user);
        }
    }
}
