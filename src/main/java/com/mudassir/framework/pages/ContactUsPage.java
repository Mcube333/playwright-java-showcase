package com.mudassir.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

import java.nio.file.Path;

public class ContactUsPage extends BasePage {

    public ContactUsPage(Page page) {
        super(page);
    }

    @Step("Open contact us page")
    public ContactUsPage open() {
        navigate(page, "/contact_us");
        return this;
    }

    @Step("Submit contact form with attachment")
    public ContactUsPage submit(String name, String email, String subject, String message, Path attachment) {
        page.locator("input[data-qa='name']").fill(name);
        page.locator("input[data-qa='email']").fill(email);
        page.locator("input[data-qa='subject']").fill(subject);
        page.locator("textarea[data-qa='message']").fill(message);
        page.locator("input[name='upload_file']").setInputFiles(attachment);
        page.onceDialog(dialog -> dialog.accept()); // site asks a JS confirm before submitting
        page.locator("input[data-qa='submit-button']").click();
        return this;
    }

    public Locator successMessage() {
        return page.locator("div.status.alert-success");
    }
}
