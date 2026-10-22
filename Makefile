.PHONY: help venv install sync serve build check code-test clean deploy

PY      ?= python3
VENV    ?= .venv
BIN     := $(VENV)/bin
MVN     ?= mvn

help: ## Show targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

venv: ## Create the Python virtualenv
	$(PY) -m venv $(VENV)

install: venv ## Install MkDocs toolchain
	$(BIN)/pip install -r requirements.txt

sync: ## Regenerate stubs, nav and roadmap from tools/master-plan.yml
	$(BIN)/python tools/handbook.py

serve: sync ## Live-preview the site on http://127.0.0.1:8000
	$(BIN)/mkdocs serve

build: sync ## Build the static site into ./site (strict: warnings fail)
	$(BIN)/mkdocs build --strict

check: ## Fail if nav/stubs/roadmap are out of date, then strict build
	$(BIN)/python tools/handbook.py --check
	$(BIN)/mkdocs build --strict

code-test: ## Compile and test every chapter/lab/FoodFleet module (JDK 25)
	cd code && $(MVN) -B -ntp verify

clean: ## Remove build output
	rm -rf site
	cd code && $(MVN) -q clean || true

deploy: build ## Publish to GitHub Pages (gh-pages branch)
	$(BIN)/mkdocs gh-deploy --force
