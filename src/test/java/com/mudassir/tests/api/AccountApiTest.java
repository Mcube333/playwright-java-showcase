package com.mudassir.tests.api;

import com.mudassir.framework.api.ApiResult;
import com.mudassir.framework.data.TestUser;
import com.mudassir.framework.data.UserFactory;
import com.mudassir.tests.base.BaseApiTest;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Feature("API - Account")
public class AccountApiTest extends BaseApiTest {

    @Test(groups = {"smoke", "api"}, description = "Account lifecycle: create, verify login, read, delete")
    public void accountLifecycle() {
        TestUser user = UserFactory.newUser();

        ApiResult created = api().createAccount(user);
        assertThat(created.responseCode()).isEqualTo(201);
        assertThat(created.message()).isEqualTo("User created!");

        try {
            ApiResult login = api().verifyLogin(user.email(), user.password());
            assertThat(login.responseCode()).isEqualTo(200);
            assertThat(login.message()).isEqualTo("User exists!");

            ApiResult details = api().getUserByEmail(user.email());
            assertThat(details.responseCode()).isEqualTo(200);
            assertThat(details.body().path("user").path("email").asText()).isEqualTo(user.email());
            assertThat(details.body().path("user").path("name").asText()).isEqualTo(user.name());
        } finally {
            ApiResult deleted = api().deleteAccount(user.email(), user.password());
            assertThat(deleted.responseCode()).isEqualTo(200);
            assertThat(deleted.message()).isEqualTo("Account deleted!");
        }

        assertThat(api().verifyLogin(user.email(), user.password()).responseCode())
                .as("login must fail after deletion").isEqualTo(404);
    }

    @Test(groups = {"regression", "api"}, description = "Duplicate email cannot create a second account")
    public void duplicateEmailIsRejected() {
        TestUser user = UserFactory.newUser();
        assertThat(api().createAccount(user).responseCode()).isEqualTo(201);
        try {
            ApiResult duplicate = api().createAccount(user);
            assertThat(duplicate.responseCode()).isEqualTo(400);
            assertThat(duplicate.message()).isEqualTo("Email already exists!");
        } finally {
            api().deleteAccount(user.email(), user.password());
        }
    }

    @Test(groups = {"regression", "api"}, description = "Unknown user cannot log in")
    public void unknownUserLoginReturns404() {
        TestUser stranger = UserFactory.newUser();
        ApiResult result = api().verifyLogin(stranger.email(), stranger.password());

        assertThat(result.responseCode()).isEqualTo(404);
        assertThat(result.message()).isEqualTo("User not found!");
    }

    @Test(groups = {"regression", "api"}, description = "Login without email returns 400")
    public void loginWithoutEmailIsBadRequest() {
        ApiResult result = api().verifyLoginWithoutEmail("anything");

        assertThat(result.responseCode()).isEqualTo(400);
        assertThat(result.message()).contains("email or password parameter is missing");
    }
}
