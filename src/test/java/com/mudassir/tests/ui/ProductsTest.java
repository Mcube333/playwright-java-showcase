package com.mudassir.tests.ui;

import com.mudassir.framework.api.ApiResult;
import com.mudassir.framework.pages.ProductDetailsPage;
import com.mudassir.framework.pages.ProductsPage;
import com.mudassir.tests.base.BaseUiTest;
import io.qameta.allure.Feature;
import org.assertj.core.api.Assertions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Feature("Products & Search")
public class ProductsTest extends BaseUiTest {

    @Test(groups = {"smoke", "ui"}, description = "Products page lists every product the API returns")
    public void productsPageMatchesApiCatalogue() {
        ApiResult apiProducts = api().getAllProducts();
        int expected = apiProducts.body().path("products").size();

        ProductsPage products = new ProductsPage(page()).open();
        assertThat(products.title()).hasText("All Products");
        assertThat(products.productCards()).hasCount(expected);
    }

    @DataProvider(name = "searchTerms", parallel = true)
    public Object[][] searchTerms() {
        return new Object[][]{{"Top"}, {"Dress"}, {"Jeans"}};
    }

    @Test(dataProvider = "searchTerms", groups = {"regression", "ui"},
            description = "Search shows the same number of results as the search API")
    public void searchResultsMatchApi(String term) {
        int expected = api().searchProduct(term).body().path("products").size();
        Assertions.assertThat(expected).as("API results for '%s'", term).isPositive();

        ProductsPage products = new ProductsPage(page()).open().search(term);
        assertThat(products.title()).hasText("Searched Products");
        assertThat(products.productCards()).hasCount(expected);
    }

    @Test(groups = {"regression", "ui"}, description = "Product details page shows name, price and stock info")
    public void productDetailsShowsKeyInformation() {
        ProductsPage products = new ProductsPage(page()).open();
        String listedName = products.nameOf(0);
        String listedPrice = products.priceOf(0);

        ProductDetailsPage details = products.viewProduct(0);
        assertThat(details.name()).hasText(listedName);
        assertThat(details.price()).hasText(listedPrice);
        assertThat(details.info()).containsText("Category:");
        assertThat(details.info()).containsText("Availability:");
        assertThat(details.info()).containsText("Brand:");
    }
}
