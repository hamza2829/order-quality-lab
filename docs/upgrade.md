# Refactoring and dependency upgrade example

The baseline commit was `7a1fb02b90842e369d719fc1ada0bd915eb8aa5a`. All four delivery workflows passed before this change:

| Check | Baseline evidence |
|---|---|
| Build and unit tests | [Successful run](https://github.com/hamza2829/order-quality-lab/actions/runs/41852920361) |
| Integration and contracts | [Successful run](https://github.com/hamza2829/order-quality-lab/actions/runs/41852920171) |
| End to end | [Successful run](https://github.com/hamza2829/order-quality-lab/actions/runs/41852920328) |
| Performance | [Successful run](https://github.com/hamza2829/order-quality-lab/actions/runs/41852920451) |

The separate upgrade commit changes the shared springdoc version from 2.9.0 to 2.9.1. Both services use the same property. Spring Boot stays on 3.5.16 and Java stays on 17.

The same small commit extracts the controller's HTTP status selection into `httpStatus(Order)`. This replaces a nested conditional expression with named, readable logic. The 201, 409 and 503 outcomes are unchanged. Existing integration tests exercise all three outcomes. Integration and end to end tests also request the generated OpenAPI document from both services.

The final README links this commit and the workflow results after it. Its measured tables and the JSON files in `docs/evidence/` come from those CI artifacts. Performance runs are short independent measurements, so the tables do not claim a causal performance improvement from this upgrade.
