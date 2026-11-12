package com.mudassir.tests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.mudassir.framework.api.ApiResult;
import com.mudassir.tests.base.BaseApiTest;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Feature("API - Products")
public class ProductsApiTest extends BaseApiTest {

    @Test(groups = {"smoke", "api"}, description = "Products list returns products with a complete schema")
    public void productsListHasCompleteSchema() {
        ApiResult result = api().getAllProducts();

        assertThat(result.responseCode()).isEqualTo(200);
        JsonNode products = result.body().path("products");
        assertThat(products.isArray()).isTrue();
        assertThat(products.size()).isPositive();

        for (JsonNode product : products) {
            assertThat(product.hasNonNull("id")).as("id in %s", product).isTrue();
            assertThat(product.path("name").asText()).as("name in %s", product).isNotBlank();
            assertThat(product.path("price").asText()).as("price in %s", product).startsWith("Rs.");
            assertThat(product.path("brand").asText()).as("brand in %s", product).isNotBlank();
            assertThat(product.path("category").path("category").asText()).as("category in %s", product).isNotBlank();
        }
    }

    @Test(groups = {"regression", "api"}, description = "Product ids are unique")
    public void productIdsAreUnique() {
        JsonNode products = api().getAllProducts().body().path("products");
        long distinct = java.util.stream.StreamSupport.stream(products.spliterator(), false)
                .map(p -> p.path("id").asInt())
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(products.size());
    }

    @Test(groups = {"regression", "api"}, description = "POST to products list is rejected with 405")
    public void postToProductsListIsNotAllowed() {
        ApiResult result = api().postToProductsList();

        assertThat(result.responseCode()).isEqualTo(405);
        assertThat(result.message()).isEqualTo("This request method is not supported.");
    }

    @Test(groups = {"regression", "api"}, description = "Brands list returns brands")
    public void brandsListReturnsBrands() {
        ApiResult result = api().getAllBrands();

        assertThat(result.responseCode()).isEqualTo(200);
        assertThat(result.body().path("brands").size()).isPositive();
    }

    @Test(groups = {"regression", "api"}, description = "Search returns products for a valid term")
    public void searchReturnsProducts() {
        ApiResult result = api().searchProduct("tshirt");

        assertThat(result.responseCode()).isEqualTo(200);
        assertThat(result.body().path("products").size()).isPositive();
    }

    @Test(groups = {"regression", "api"}, description = "Search without the required parameter returns 400")
    public void searchWithoutParamIsBadRequest() {
        ApiResult result = api().searchProductWithoutParam();

        assertThat(result.responseCode()).isEqualTo(400);
        assertThat(result.message()).contains("search_product parameter is missing");
    }
}
