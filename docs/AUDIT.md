# Audit

The service writes one audit line for every consumer action that creates, changes, reveals or removes something: who did it, what to, and whether it worked. The design decisions are on Confluence: [Audit design for the API Marketplace](https://hmcts.atlassian.net/wiki/spaces/AMP/pages/389775861/Audit+design+for+the+API+Marketplace).

## What is audited

| Action | Endpoint | Target | API |
|---|---|---|---|
| `USER_SIGNED_IN` | `POST /session` | Entra object id | |
| `CONSUMER_APPLICATION_CREATED` | `POST /applications` | client id | |
| `CONSUMER_APPLICATION_ROLES_GRANTED` | `POST /applications/{clientId}/roles` | client id | short code |
| `CONSUMER_APPLICATION_DELETED` | `DELETE /applications/{clientId}` | client id | |
| `CONSUMER_SUBSCRIPTION_KEY_CREATED` | `POST /apim/subscription-keys` | subscription name | short code |
| `CONSUMER_SUBSCRIPTION_KEY_VALUE_READ` | `GET /apim/subscription-keys/{name}/values` | subscription name | |
| `CONSUMER_SUBSCRIPTION_KEY_DELETED` | `DELETE /apim/subscription-keys/{name}` | subscription name | |
| `CONSUMER_PRODUCTION_REQUEST_SUBMITTED` | `POST /production-requests` | request reference | |
| `CONSUMER_PRODUCTION_REQUEST_WITHDRAWN` | `DELETE /production-requests/{reference}` | request reference | |

Reads that only list or show a user's own data are not audited.

## How it works

1. A controller method carries `@LogAudit(action = ..., target = ..., api = ..., actor = ...)`. `target`, `api` and `actor` are SpEL expressions over the method's arguments and its return value (`#result`).
2. `AuditAspect` evaluates them after the method runs and keeps the values on the request.
3. `AuditInterceptor.afterCompletion` writes one line per request to the `AUDIT` logger, with the outcome taken from the HTTP status. It also runs when the request failed before or inside the controller.
4. The line is logged as JSON. The App Insights Java agent sends it to the `AppTraces` table in the `amp-marketplace-<env>` Log Analytics workspace.

An audit failure is logged as an error and never changes the response.

## The audit line

The message reads `<ACTION> <target> <OUTCOME>`. The fields are added as MDC properties and removed once the line is written:

| Field | Value |
|---|---|
| `audit_action` | The `AuditAction` |
| `audit_target` | What was acted on (see the table above) |
| `audit_outcome` | `SUCCESS` for a 2xx status, otherwise `FAILURE` |
| `audit_status` | The HTTP status |
| `audit_actor_oid` | Entra object id of the user: `actor` when set, otherwise the `requestingUserId` header |
| `audit_endpoint` | Method and route template, e.g. `POST /apim/subscription-keys` |
| `audit_api` | API short code, where relevant |
| `audit_occurred_at` | UTC timestamp |

Fields with no value are left out. Keys, secrets, tokens and request bodies are never logged.

## Adding an audited endpoint

1. Add a value to `AuditAction`.
2. Annotate the controller method, for example:

   ```java
   @LogAudit(action = AuditAction.CONSUMER_APPLICATION_DELETED, target = "#clientId")
   ```

3. Add a case to `AuditIntegrationTest` that asserts the line and its fields.

## Querying

```kusto
AppTraces
| where Properties.LoggerName == "AUDIT"
| project TimeGenerated,
          action = tostring(Properties.audit_action),
          target = tostring(Properties.audit_target),
          outcome = tostring(Properties.audit_outcome),
          actor = tostring(Properties.audit_actor_oid)
| order by TimeGenerated desc
```

Add `| where actor == "<object id>"` for one user's actions, or `| where action == "CONSUMER_SUBSCRIPTION_KEY_CREATED"` for one action.

## Where it is configured

In the order it was set up:

| Step | What | Where |
|---|---|---|
| 1 | JSON log format, including the `audit_*` fields | `src/main/resources/logback.xml` |
| 2 | Jenkins permission to create the workspace and its table | cnp-jenkins-config `terraform-infra-approvals/service-api-marketplace.json` |
| 3 | Log Analytics workspace, App Insights and the `AppTraces` retention | `infrastructure/app-insights.tf` |
| 4 | Audit retention: 2191 days (six years) in production, 30 days elsewhere | `audit_retention_days` in `infrastructure/variables.tf` and `infrastructure/prod.tfvars` |
| 5 | Agent configuration (connection string, role name, sampling) | The `app-insights-config` Key Vault secret, written by `infrastructure/app-insights.tf` |
| 6 | Agent switched on per environment: `APPLICATIONINSIGHTS_ENABLED`, `APPLICATIONINSIGHTS_CONFIGURATION_FILE` and the `app-insights-config` secret mount | cnp-flux-config `apps/amp/amp-marketplace/<env>.yaml` |

## Tests

| Test | Covers |
|---|---|
| `AuditInterceptorTest` | Fields, outcome, actor fallback, MDC clean-up, a failed write not failing the request |
| `AuditAspectTest` | Expressions over arguments and the result, capture when the method throws, a bad expression not breaking the response |
| `AuditIntegrationTest` | Full requests: a successful sign-in line with the correlation id, and a failure line for an unknown application |
