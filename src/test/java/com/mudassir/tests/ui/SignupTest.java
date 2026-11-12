package com.mudassir.tests.ui;

import com.mudassir.framework.data.TestUser;
import com.mudassir.framework.data.UserFactory;
import com.mudassir.framework.pages.HomePage;
import com.mudassir.framework.pages.LoginPage;
import com.mudassir.framework.pages.SignupPage;
import com.mudassir.tests.base.BaseUiTest;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Feature("Signup")
public class SignupTest extends BaseUiTest {

    @Test(groups = {"smoke", "ui"}, description = "New user can register through the UI and delete the account")
    public void newUserCanRegisterAndDeleteAccount() {
        TestUser user = UserFactory.newUser();

        SignupPage signup = new LoginPage(page()).open().startSignup(user.name(), user.email());
        assertThat(signup.accountInfoHeading()).isVisible();

        signup.createAccount(user);
        assertThat(signup.accountCreatedHeading()).hasText("Account Created!");

        HomePage home = signup.continueToHome();
        assertThat(home.loggedInAs()).containsText(user.name());

        home.deleteAccount();
        assertThat(home.accountDeletedHeading()).hasText("Account Deleted!");
    }
}
