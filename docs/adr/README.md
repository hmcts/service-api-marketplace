# Architecture decision records

Decisions for the HMCTS API Marketplace backend, and for cross-cutting marketplace decisions this
service owns (such as developer identity). `web-api-marketplace` links here rather than keeping a copy.

| ADR | Status | Decision |
|---|---|---|
| [0001](0001-developer-accounts-and-email-verification.md) | Accepted | Developer accounts live in this service (its own credential store), verified and reset by email through GOV.UK Notify, with `web-api-marketplace` as a backend-for-frontend |

## Conventions

- One ADR per decision, numbered sequentially: `NNNN-short-title.md`.
- Never rewritten once Accepted. A later ADR supersedes it instead.
- Every ADR records the rationale and trade-offs, not just the outcome.
- Open questions that don't change the decision can stay open after acceptance. Record each answer
  in the ADR as it lands.
