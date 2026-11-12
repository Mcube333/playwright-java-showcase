# Test runner image: JDK, Maven and Playwright browsers pinned to the same version as the pom.
FROM mcr.microsoft.com/playwright/java:v1.49.0-noble

# Browsers are already in the image; stop Playwright from downloading them again at runtime
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /tests

# Dependencies first so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
COPY scripts ./scripts
RUN mvn -B -q test-compile

# Default: smoke suite on chromium. Override by passing Maven args to "docker run".
ENTRYPOINT ["mvn", "-B", "test"]
CMD ["-Dsuite=smoke", "-Dbrowser=chromium"]
