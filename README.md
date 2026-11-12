# Playwright + Java Test Automation Showcase

End-to-end UI and API automation for [AutomationExercise](https://automationexercise.com), a public
e-commerce practice site. Built with **Java 17, Playwright, TestNG, AssertJ, Allure and GitHub Actions**.

This repo shows the same engineering patterns I used to build a production automation framework
(98 E2E tests) at a furniture-rental e-commerce company. The original is proprietary, so this is a
clean-room rebuild on a public site.

## What it covers

| Area | Tests |
|---|---|
| Login / Signup | valid login + logout, wrong password, duplicate email, full UI registration + account deletion |
| Products & Search | UI catalogue count vs API, search results vs search API (data-driven), product details |
| Cart | line totals = price x quantity, quantity from details page, remove item |
| Checkout & Payment | logged-in order end to end with address verification, guest login prompt |
| Contact & Subscription | file upload + JS confirm dialog, newsletter subscription |
| API | product schema, unique ids, 405 / 400 / 404 negative cases, full account lifecycle, duplicate account |

## Design decisions

- **Thread-isolated sessions.** Each test thread gets its own Playwright, Browser, Context and Page via
  `ThreadLocal` (`PlaywrightFactory`). Tests call `page()` instead of holding a field, so parallel methods
  never share state. Suites run `parallel="methods"`.
- **Condition-based waits only.** No `Thread.sleep` in tests (the only backoff is a bounded retry when the demo site returns 502/503/504). Playwright auto-waiting locators and web-first
  assertions (`assertThat(locator).hasText(...)`) handle timing.
- **API for setup, UI for verification.** Tests that need a logged-in user create it through the API in
  milliseconds, run the UI flow, then delete it through the API. This keeps UI tests fast and independent.
- **Cross-layer checks.** The UI product count and search results are compared against the API, which
  catches mismatches between what the backend returns and what the page renders.
- **Third-party noise blocked.** Ad and consent hosts are aborted at the network layer
  (`context.route`), which removes the most common cause of flakiness on this site.
- **Business status codes.** The API always returns HTTP 200 and puts the real status in a
  `responseCode` field. `ApiResult` exposes it so tests assert on what actually matters.
- **Failure evidence.** On failure: full-page screenshot + URL attached to Allure, and a Playwright trace
  saved to `target/traces`. Passing tests discard their traces.
- **Automatic retry.** `RetryTransformer` attaches a retry analyzer to every test (count from config).
- **Local or remote browsers.** `-Dbrowser.ws=...` switches from launching browsers to connecting to a
  remote Playwright server (Docker), with a retry while the server starts.
- **Multi-environment config.** `config/{env}.properties`, any key overridable with `-Dkey=value`.
- **PII guard in CI.** `scripts/pii-check.sh` fails the build if real-looking emails, phone numbers or
  card numbers appear in source. Test data is synthetic (`@example.com`, zeroed phone numbers).

## Project structure

```
src/main/java/com/mudassir/framework
  config/   ConfigManager            env + system property overrides
  driver/   PlaywrightFactory        ThreadLocal sessions, ad blocking, tracing
  pages/    10 page objects          fluent, @Step-annotated for Allure
  api/      AutomationExerciseApi    Playwright APIRequestContext client
  data/     TestUser, UserFactory    unique synthetic users per test
src/test/java/com/mudassir/tests
  base/       BaseUiTest, BaseApiTest
  listeners/  retry + failure evidence
  ui/         UI tests
  api/        API tests
src/test/resources/suites   regression.xml, smoke.xml, api.xml
```

## Running

Prerequisites: JDK 17+, Maven 3.9+.

```bash
# one-time: download the browser
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"

mvn test                                  # full regression (headless chromium)
mvn test -Dsuite=smoke                    # smoke only
mvn test -Dsuite=api                      # API only, no browser
mvn test -Dbrowser=firefox                # another browser
mvn test -Denv=local                      # headed + slow-mo for debugging
mvn allure:serve                          # open the Allure report
npx playwright show-trace target/traces/<test>.zip   # inspect a failed run
```

## Docker

The test runner image pins JDK, Maven and Playwright browsers to the same version as `pom.xml`,
so runs are identical on any laptop or CI agent.

```bash
make docker-test                              # smoke suite inside Docker
make docker-test SUITE=regression BROWSER=firefox
make docker-remote                            # tests container + Playwright server container
```

**Remote mode** is a grid-style setup: tests run in one container and connect over WebSocket to
browsers in a separate Playwright server container (`-Dbrowser.ws=ws://playwright-server:3000/`).
The same switch works with any remote Playwright server, so browsers can be scaled separately from test runners.

Allure results and traces are mounted back to `target/` on the host.

## CI

GitHub Actions (`.github/workflows/ci.yml`):
1. **Checks:** PII leak scan and compile on every push and PR.
2. **Cross-browser matrix:** Chromium, Firefox and WebKit in parallel, inside the official Playwright
   container (no browser install step). Smoke on push/PR, full regression daily, any suite on demand.
3. **Docker job:** builds the image, runs the API suite in it, then runs smoke against the
   Playwright server container through Docker Compose.
4. **Allure report:** results from all browsers merged into one report and published to **GitHub Pages**
   on every run on `main`.
5. Traces uploaded for failed browsers; superseded runs on the same branch are cancelled.

To enable the report: repo Settings > Pages > Source: **GitHub Actions**.

## Author

Mohammed Mudassir Maniyar, SDET
[LinkedIn](https://linkedin.com/in/mohammed-mudassir03)
