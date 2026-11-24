package com.mudassir.framework.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.mudassir.framework.data.TestUser;
import io.qameta.allure.Step;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Thin client over the public AutomationExercise API (https://automationexercise.com/api_list). */
public class AutomationExerciseApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final APIRequestContext request;

    public AutomationExerciseApi(APIRequestContext request) {
        this.request = retryingOnGatewayErrors(request);
    }

    @Step("API: GET products list")
    public ApiResult getAllProducts() {
        return parse(request.get("/api/productsList"));
    }

    @Step("API: POST products list (unsupported method)")
    public ApiResult postToProductsList() {
        return parse(request.post("/api/productsList"));
    }

    @Step("API: GET brands list")
    public ApiResult getAllBrands() {
        return parse(request.get("/api/brandsList"));
    }

    @Step("API: search products for '{term}'")
    public ApiResult searchProduct(String term) {
        return parse(request.post("/api/searchProduct",
                RequestOptions.create().setForm(FormData.create().set("search_product", term))));
    }

    @Step("API: search products without search_product param")
    public ApiResult searchProductWithoutParam() {
        return parse(request.post("/api/searchProduct"));
    }

    @Step("API: verify login for {email}")
    public ApiResult verifyLogin(String email, String password) {
        return parse(request.post("/api/verifyLogin", RequestOptions.create()
                .setForm(FormData.create().set("email", email).set("password", password))));
    }

    @Step("API: verify login without email")
    public ApiResult verifyLoginWithoutEmail(String password) {
        return parse(request.post("/api/verifyLogin",
                RequestOptions.create().setForm(FormData.create().set("password", password))));
    }

    @Step("API: create account")
    public ApiResult createAccount(TestUser user) {
        FormData form = FormData.create()
                .set("name", user.name())
                .set("email", user.email())
                .set("password", user.password())
                .set("title", user.title())
                .set("birth_date", user.birthDay())
                .set("birth_month", user.birthMonth())
                .set("birth_year", user.birthYear())
                .set("firstname", user.firstName())
                .set("lastname", user.lastName())
                .set("company", user.company())
                .set("address1", user.address1())
                .set("address2", user.address2())
                .set("country", user.country())
                .set("zipcode", user.zipcode())
                .set("state", user.state())
                .set("city", user.city())
                .set("mobile_number", user.mobile());
        return parse(request.post("/api/createAccount", RequestOptions.create().setForm(form)));
    }

    @Step("API: get user detail for {email}")
    public ApiResult getUserByEmail(String email) {
        return parse(request.get("/api/getUserDetailByEmail",
                RequestOptions.create().setQueryParam("email", email)));
    }

    @Step("API: delete account {email}")
    public ApiResult deleteAccount(String email, String password) {
        return parse(request.delete("/api/deleteAccount", RequestOptions.create()
                .setForm(FormData.create().set("email", email).set("password", password))));
    }

    private static final int MAX_ATTEMPTS = 4;

    /**
     * The public demo site sometimes answers 502/503/504 ("under heavy load, queue full") before the request is
     * processed. Retry those with a short backoff so a busy third party does not fail the suite; every other
     * response, including 4xx, is returned untouched.
     */
    private static APIRequestContext retryingOnGatewayErrors(APIRequestContext delegate) {
        return (APIRequestContext) Proxy.newProxyInstance(
                APIRequestContext.class.getClassLoader(), new Class<?>[]{APIRequestContext.class},
                (proxy, method, args) -> {
                    Object result = null;
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                        try {
                            result = method.invoke(delegate, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        if (!(result instanceof APIResponse r) || !isTransient(r) || attempt == MAX_ATTEMPTS) {
                            return result;
                        }
                        Thread.sleep(2000L * attempt);
                    }
                    return result;
                });
    }

    /** Gateway errors, rate limiting, or a 200 whose body is not the usual {"responseCode": ...} JSON (busy-site pages, empty body). */
    private static boolean isTransient(APIResponse r) {
        int s = r.status();
        if (s == 502 || s == 503 || s == 504 || s == 429) {
            return true;
        }
        try {
            return !MAPPER.readTree(r.text()).has("responseCode");
        } catch (Exception e) {
            return true;
        }
    }

    private ApiResult parse(APIResponse response) {
        String text = response.text();
        JsonNode body;
        try {
            body = MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("Response is not JSON (HTTP " + response.status() + "): " + text, e);
        }
        if (!body.has("responseCode")) {
            throw new IllegalStateException("No responseCode in response (HTTP " + response.status() + "): " + text);
        }
        return new ApiResult(response.status(), body);
    }
}
