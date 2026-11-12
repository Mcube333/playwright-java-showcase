SUITE ?= smoke
BROWSER ?= chromium

.PHONY: test docker-build docker-test docker-remote report clean

test:            ## run locally with Maven
	mvn -B test -Dsuite=$(SUITE) -Dbrowser=$(BROWSER)

docker-build:    ## build the test runner image
	docker compose build tests

docker-test:     ## run the suite inside Docker
	SUITE=$(SUITE) BROWSER=$(BROWSER) docker compose run --rm tests

docker-remote:   ## tests container + separate Playwright browser server container
	SUITE=$(SUITE) BROWSER=$(BROWSER) docker compose --profile remote up --build \
		--abort-on-container-exit --exit-code-from tests-remote

report:          ## open the Allure report from the last run
	mvn allure:serve

clean:
	rm -rf target
	docker compose --profile remote down --remove-orphans
