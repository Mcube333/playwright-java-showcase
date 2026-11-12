package com.mudassir.tests.ui;

import com.mudassir.framework.pages.ContactUsPage;
import com.mudassir.framework.pages.HomePage;
import com.mudassir.tests.base.BaseUiTest;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Feature("Contact & Subscription")
public class ContactAndSubscriptionTest extends BaseUiTest {

    @Test(groups = {"regression", "ui"}, description = "Contact form with file upload and JS confirm dialog submits")
    public void contactFormWithAttachmentSubmits() throws IOException {
        Path attachment = Files.createTempFile("qa-attachment-", ".txt");
        Files.writeString(attachment, "Automated test attachment");

        ContactUsPage contact = new ContactUsPage(page()).open()
                .submit("QA Demo", "qa.demo@example.com", "Automation test",
                        "Submitted by the Playwright showcase suite.", attachment);

        assertThat(contact.successMessage())
                .containsText("Success! Your details have been submitted successfully.");
    }

    @Test(groups = {"regression", "ui"}, description = "Footer newsletter subscription shows success")
    public void footerSubscriptionShowsSuccess() {
        HomePage home = new HomePage(page()).open().subscribe("qa.demo@example.com");
        assertThat(home.subscribeSuccess()).containsText("You have been successfully subscribed!");
    }
}
