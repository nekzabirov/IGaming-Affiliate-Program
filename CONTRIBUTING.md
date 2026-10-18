# Contributing

Thanks for helping. Issues and pull requests are welcome.

## Before you start

For anything bigger than a small fix, open an issue first so we can agree on the approach. Changes to the event
contract (`docs/wiki/Events.md`), env variables, REST paths, error codes or migrations affect running deployments
and need a clear reason.

## Build and test

Requirements: JDK 21, Node 22, Docker.

```bash
./gradlew test                              # needs Docker: Testcontainers starts PostgreSQL and RabbitMQ
cd frontend && npm ci && npm run build      # type-check and build the SPA
docker compose up --build                   # the full stack, if your change touches it
```

Everything must pass. Add or update tests for every behaviour you change; tests are named as sentences and
integration tests use real databases, not mocks.

## Pull requests

- One logical change per pull request, with a description of what and why.
- Follow the conventions in [AGENTS.md](AGENTS.md): plain SQL, nano-unit money, no remote calls inside a
  transaction, stable error codes and log `event` keys, comments that explain why.
- Never edit an existing migration; add a new one.
- Fill in the checklist of the pull request template.

## Documentation rule

> **Documentation rule.** Every change that affects behaviour, configuration, API, build or deployment MUST
> update `README.md` and the relevant pages in `docs/wiki/` in the same commit / pull request. A change is not
> done until the docs match the code.

`docs/wiki/` is the source of the GitHub Wiki; it is published from there, so edit the files, not the wiki.

## License

By contributing you agree that your contributions are licensed under the Apache License 2.0 (see LICENSE).
