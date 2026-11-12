package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.mudassir.framework.data.TestUser;
import io.qameta.allure.Step;

/** "Enter Account Information" form shown after the first signup step. */
public class SignupPage extends BasePage {

    public SignupPage(Page page) {
        super(page);
    }

    public Locator accountInfoHeading() {
        return page.locator("h2:has-text('Enter Account Information')");
    }

    @Step("Fill account details and create account")
    public SignupPage createAccount(TestUser user) {
        page.locator("Mr".equals(user.title()) ? "#id_gender1" : "#id_gender2").check();
        page.locator("input[data-qa='password']").fill(user.password());
        page.locator("select[data-qa='days']").selectOption(user.birthDay());
        page.locator("select[data-qa='months']").selectOption(user.birthMonth());
        page.locator("select[data-qa='years']").selectOption(user.birthYear());
        page.locator("#newsletter").check();
        page.locator("#optin").check();
        page.locator("input[data-qa='first_name']").fill(user.firstName());
        page.locator("input[data-qa='last_name']").fill(user.lastName());
        page.locator("input[data-qa='company']").fill(user.company());
        page.locator("input[data-qa='address']").fill(user.address1());
        page.locator("input[data-qa='address2']").fill(user.address2());
        page.locator("select[data-qa='country']").selectOption(user.country());
        page.locator("input[data-qa='state']").fill(user.state());
        page.locator("input[data-qa='city']").fill(user.city());
        page.locator("input[data-qa='zipcode']").fill(user.zipcode());
        page.locator("input[data-qa='mobile_number']").fill(user.mobile());
        page.locator("button[data-qa='create-account']").click();
        return this;
    }

    public Locator accountCreatedHeading() {
        return page.locator("h2[data-qa='account-created']");
    }

    @Step("Continue after account creation")
    public HomePage continueToHome() {
        page.locator("a[data-qa='continue-button']").click();
        return new HomePage(page);
    }
}
