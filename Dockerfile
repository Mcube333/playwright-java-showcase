# Test runner image: JDK, Maven and Playwright browsers pinned to the same version as the pom.
FROM mcr.microsoft.com/playwright/java:v1.49.0-noble

# Browsers are already in the image; stop Playwright from downloading them again at runtime
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /tests

# Dependencies first so this layer is cached until pom.xml changes
COPY pom.xml .
# Maven Central rate-limits shared CI runner IPs (HTTP 429), so retry with backoff instead of failing the build
RUN for i in 1 2 3 4 5; do mvn -B -q dependency:go-offline && exit 0; echo "dependency download failed (attempt $i), retrying"; sleep $((i * 20)); done; exit 1

COPY src ./src
COPY scripts ./scripts
RUN for i in 1 2 3; do mvn -B -q test-compile && exit 0; sleep $((i * 20)); done; exit 1

# Default: smoke suite on chromium. Override by passing Maven args to "docker run".
ENTRYPOINT ["mvn", "-B", "test"]
CMD ["-Dsuite=smoke", "-Dbrowser=chromium"]
