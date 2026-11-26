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
| `ADMIN_USERS_LISTED` | `GET /admin/users`, `GET /admin/users/lookup` | | |
| `ADMIN_USER_VIEWED` | `GET /admin/users/{userId}`, `GET /admin/users/lookup/{userId}` | user id or Entra object id | |

Reads that only list or show a user's own data are not audited. Admin reads of other users' data are, including refused attempts.

## How it works

1. A controller method carries `@LogAudit(action = ..., target = ..., api = ..., actor = ...)`. `target`, `api` and `actor` are SpEL expressions over the method's arguments and its return value (`#result`).
2. `AuditAspect` evaluates them after the method runs and keeps the values on the request.
3. `AuditInterceptor.afterCompletion` writes one line per request to the `AUDIT` logger, with the outcome taken from the HTTP status. It also runs when the request failed before or inside the controller.
4. Where it goes depends on `AUDIT_INGESTION_ENABLED`:
   - **Off** (the default, and always for local runs, tests and previews): the line is logged as JSON to stdout and the App Insights Java agent sends it to `AppTraces` in the `amp-marketplace-<env>` Log Analytics workspace.
   - **On**: `AuditIngestionConfig` moves the `AUDIT` logger off stdout. `AuditIngestionAppender`, behind an `AsyncAppender`, sends each entry through the Logs Ingestion API to a data collection rule, which writes it to the `AmpAudit_CL` table in the same workspace. The app signs in as `amp-<env>-mi`. An entry that still cannot be sent after the client's retries is logged to stdout at `ERROR` by `AUDIT_FALLBACK`, with its fields renamed `unsent_audit_*`, so it reaches `AppTraces` instead of being lost.

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

## The audit table

`AmpAudit_CL` (`_CL` marks a custom table) keeps audit for `audit_retention_days` while `AppTraces` keeps app logs for `log_analytics_retention_days`.

| Column | From |
|---|---|
| `TimeGenerated` | `audit_occurred_at` |
| `Action`, `Outcome`, `Status`, `ActorOid`, `Target`, `Endpoint`, `Api` | The matching `audit_*` field |
| `CorrelationId` | `X-Correlation-Id` |
| `Environment` | `ENVIRONMENT_NAME` |

To switch an environment over:

1. Ask PlatOps for *Monitoring Metrics Publisher* for `amp-<env>-mi` on `amp-marketplace-audit-<env>`. Jenkins can't grant this role, so `grant_audit_publisher` stays `false`.
2. In cnp-flux-config, mount the `audit-ingestion-endpoint` and `audit-ingestion-rule-id` secrets as `AUDIT_INGESTION_ENDPOINT` and `AUDIT_INGESTION_RULE_ID`, and set `AUDIT_INGESTION_ENABLED` to `true`. The app refuses to start when it is on without both.
3. Set `audit_in_own_table = true` in that environment's tfvars. The App Insights agent copies every log line to `AppTraces`, audit included, so this adds a rule to its config that drops lines carrying `audit_action`. A line that could not be sent carries `unsent_audit_*` instead, so it still reaches `AppTraces`.

Once every environment has switched, `AppTraces` total retention goes back to `log_analytics_retention_days`.

## Querying

Run these in the `amp-marketplace-<env>` Log Analytics workspace (Logs). Start with the `audit` definition that matches where the environment's audit lives, then add one of the queries below it.

Switched over to `AmpAudit_CL`:

```kusto
let audit = AmpAudit_CL
| project timestamp = TimeGenerated, action = Action, target = Target, outcome = Outcome,
          status = Status, actor = ActorOid, endpoint = Endpoint, api = Api, correlationId = CorrelationId;
```

Not yet switched over, or entries that fell back to stdout:

```kusto
let audit = AppTraces
| where Properties.LoggerName == "AUDIT"
| project timestamp = TimeGenerated,
          action = tostring(Properties.audit_action),
          target = tostring(Properties.audit_target),
          outcome = tostring(Properties.audit_outcome),
          status = toint(Properties.audit_status),
          actor = tostring(Properties.audit_actor_oid),
          endpoint = tostring(Properties.audit_endpoint),
          api = tostring(Properties.audit_api),
          correlationId = tostring(Properties["X-Correlation-Id"]);
```

In App Insights (Logs) use `traces`, `timestamp` and `customDimensions` in place of `AppTraces`, `TimeGenerated` and `Properties`.

| Question | Query |
|---|---|
| Everything in the last day | `audit \| where timestamp > ago(24h) \| order by timestamp desc` |
| What one user did | `audit \| where timestamp > ago(30d) and actor == "<object id>" \| order by timestamp desc` |
| Who acted on one item | `audit \| where timestamp > ago(30d) and target == "<client id, subscription name or reference>" \| order by timestamp desc` |
| Failures | `audit \| where timestamp > ago(7d) and outcome == "FAILURE" \| order by timestamp desc` |
| Activity per action per day | `audit \| where timestamp > ago(30d) \| summarize count() by bin(timestamp, 1d), action \| render columnchart` |
| Sign-ins and distinct users per day | `audit \| where timestamp > ago(30d) and action == "USER_SIGNED_IN" \| summarize signIns = count(), users = dcount(actor) by bin(timestamp, 1d)` |
| Who read a subscription key value | `audit \| where timestamp > ago(90d) and action == "CONSUMER_SUBSCRIPTION_KEY_VALUE_READ" \| order by timestamp desc` |
| Admin access to other users' data, including refusals | `audit \| where timestamp > ago(30d) and action startswith "ADMIN_" \| order by timestamp desc` |
| Users with repeated failures | `audit \| where timestamp > ago(1d) and outcome == "FAILURE" \| summarize failures = count(), actions = make_set(action) by actor \| where failures >= 5` |
| Keys and applications created or deleted per API | `audit \| where timestamp > ago(30d) and action matches regex "_(CREATED\|DELETED)$" \| summarize count() by action, api` |

Everything else in the request behind one audit entry:

```kusto
let id = "<correlation id>";
let ops = AppTraces | where TimeGenerated > ago(7d) and tostring(Properties["X-Correlation-Id"]) == id | distinct OperationId;
union AppTraces, AppRequests, AppDependencies, AppExceptions
| where TimeGenerated > ago(7d) and OperationId in (ops)
| project TimeGenerated, Type, Message, Name, ResultCode
| order by TimeGenerated asc
```

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
| 7 | Jenkins permission to create the audit table, data collection rule and role assignment | cnp-jenkins-config `terraform-infra-approvals/service-api-marketplace.json` |
| 8 | `AmpAudit_CL`, its data collection rule, *Monitoring Metrics Publisher* on the rule for `amp-<env>-mi` (behind `grant_audit_publisher`), and the `audit-ingestion-*` secrets | `infrastructure/audit.tf` |
| 9 | Switch per environment: `AUDIT_INGESTION_ENABLED` and the `audit-ingestion-*` secret mounts | cnp-flux-config `apps/amp/amp-marketplace/<env>.yaml` |

## Tests

| Test | Covers |
|---|---|
| `AuditInterceptorTest` | Fields, outcome, actor fallback, MDC clean-up, a failed write not failing the request |
| `AuditAspectTest` | Expressions over arguments and the result, capture when the method throws, a bad expression not breaking the response |
| `AuditIngestionAppenderTest` | Field-to-column mapping, missing fields left out, a failed upload written to stdout at `ERROR` |
| `AuditIngestionConfigTest` | The `AUDIT` logger moved off stdout and back, queued entries flushed on shutdown, refusing to start without the rule details |
| `AuditIntegrationTest` | Full requests: a successful sign-in line with the correlation id, and a failure line for an unknown application |
