# Entra application registrations

## Operations

| Method | Operation | Path | Success |
|---|---|---|---|
| GET | listApplications | `/applications-new` | 200 |
| GET | getApplication | `/applications-new/{clientId}` | 200 |
| POST | createApplication | `/applications-new` | 201 |
| DELETE | deleteApplication | `/applications-new/{clientId}` | 204 |

`{clientId}` is the Entra **Client ID** (`appId`) - the one a subscriber authenticates with, and
the one `createApplication` returns.

## listApplications

Every application the onboarding identity owns, oldest first.

**Request** — none.

**Response** — an array of:

| Field | Type | Notes |
|---|---|---|
| `objectId` | string | Graph's directory object id |
| `clientId` | string | The Client ID a subscriber authenticates with |
| `displayName` | string | |
| `created` | string | ISO 8601 |


## getApplication

One application, with the roles it actually holds - what it can call.

**Request** — none beyond `{clientId}` in the path.

**Response** — the four fields from listApplications, plus:

| Field | Type | Notes |
|---|---|---|
| `roles` | string[] | Role names, resolved from the ids Graph returns |

**Failures**

| Status | When |
|---|---|
| 404 | No application with that Client ID |

## createApplication

Registers the application and grants its roles in one act.

**Request**

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | yes | Free text, the Entra display name |
| `roles` | string[] | yes | **At least one.** Role names the API offers, e.g. `slc.read` |

**Response**

| Field | Type | Notes |
|---|---|---|
| `objectId` | string | |
| `clientId` | string | |
| `clientSecret` | string | **The only time this exists.** Entra will not show it again |
| `keyId` | string | Identifies the secret, for rotation |
| `roles` | string[] | What was granted |

**Failures**

| Status | When |
|---|---|
| 400 | `name` blank, `roles` empty, or a role the API does not offer - the message lists the ones it does |
| 500 | Graph refused the role assignment; the message names the permission that is missing |
| 502 | Graph could not be reached, or a call did not succeed within its retries |
| 503 | `ENTRA_ONBOARDING_CLIENT_SECRET` is not configured |

**A new service principal is not immediately assignable.** Entra rejects an app role assignment
against one seconds old as *"Not a valid reference update"*, and can keep doing so for minutes -
far longer than the seconds a read replica takes to catch up. So the assignment is retried on its
own schedule (`ENTRA_ASSIGNMENT_*`, 10 attempts 15s apart) and **a create can legitimately take a
couple of minutes**. A 403 is not retried: that is a missing permission, and waiting never fixes it.

**Anything that fails after the application exists takes it away again**, a role that cannot be
granted included.

## deleteApplication

Removes the application, and with it the service principal, the credentials and the role
assignments. This is the only way a granted role goes away.

**Request** — none beyond `{clientId}` in the path.

**Response** — 204, no body.

**Failures**

| Status | When |
|---|---|
| 404 | No application with that Client ID |

## The three Graph calls behind a registration

```java
graph.applications().post(application)                           // id (object id) + appId (client id)
graph.servicePrincipals().post(servicePrincipal)                 // the identity in the tenant
graph.applications().byApplicationId(id).addPassword().post(..)  // the secret, returned once
```


## Running against the real sbox tenant

The tests use WireMock, so they prove nothing about Entra. To exercise the endpoints for real:

**1. Start the database.** The application will not come up without one. Just the `db` service -
nothing is built, it pulls `postgres:18-alpine`.

```bash
docker compose up -d db
```

**2. Get the onboarding secret.** Unlike the APIM endpoints, this does **not** use your az login -
it authenticates as `amp-role-assignment-test` with a client secret.

**3. Run the application with `bootRun`**, not `docker compose up` - that builds the service image
and is a much longer way round for this.

```bash
ENTRA_ONBOARDING_CLIENT_SECRET='<paste it - quoted, the value contains ~>' \
./gradlew bootRun
```

Everything else already defaults to sbox. The startup log confirms what it is pointed at:

```
INFO  Entra tenant d44f885c-..., onboarding client aba0879b-..., API 30288840-...
WARN  ENTRA_ONBOARDING_CLIENT_SECRET is not set: the application registration endpoints will fail.
```

That warning means the secret did not reach the application, and every call will answer 503.

**4. Call the endpoints.**

```bash
# list what is already there ... oldest first, never with a secret
curl -s localhost:8080/applications-new | jq '.[] | {clientId, displayName, created}'
```

```bash
# register one with a role ... this is the only moment the secret exists
curl -s -X POST localhost:8080/applications-new -H 'Content-Type: application/json' \
  -d '{"name":"colin-test","roles":["slc.read"]}' | jq
```

```bash
# what roles does it hold?
curl -s localhost:8080/applications-new/<clientId> | jq
```

```bash
# remove the application ... be careful, this revokes its credentials
curl -i -X DELETE localhost:8080/applications-new/<clientId>
```

**Clean up after yourself.** These are real writes to the `hmctsextsbox` tenant, and what you
leave behind shows in the Entra portal under App registrations.

### The whole journey in one run

`app-register.sh` does all of it against the real sandbox - register with a role, get a token, call
`slc` through APIM - and deletes the application again however the run turns out:

```bash
export APIM_SUBSCRIPTION_KEY='<the slc product key - quoted>'
./app-register.sh
```

`KEEP=1` leaves the application behind; `ROLE`, `NAME`, `CASE_URN` and `BASE_URL` override the
defaults. The gateway is only reachable from inside the HMCTS network, so the last step needs the
VPN - the first two work without it.

### Proving the role actually did something

Take the credentials and ask for a token:

```bash
curl -s -X POST "https://login.microsoftonline.com/d44f885c-4fac-47bf-afde-d7d861ec4d7b/oauth2/v2.0/token" \
  -d "client_id=$CLIENT_ID" \
  --data-urlencode "client_secret=$CLIENT_SECRET" \
  -d "scope=30288840-e345-4543-99ee-f9253d789339/.default" \
  -d "grant_type=client_credentials"
```

`--data-urlencode` on the secret, not `-d`: Entra secrets contain `~`, `.` and sometimes `+` or `=`,
and an unencoded one is silently mangled into `AADSTS7000215: Invalid client secret provided`.

Decode it. **`"roles": ["slc.read"]` is the thing worth checking** - a token with `"roles": null` is
authenticated and authorised for nothing, which is exactly what the role assignment exists to fix.

## Configuration

| Setting | Default |
|---|---|
| `ENTRA_TENANT_ID` | `d44f885c-...`, the `hmctsextsbox` CIAM tenant |
| `ENTRA_ONBOARDING_CLIENT_ID` | `aba0879b-...`, `amp-role-assignment-test` |
| `ENTRA_ONBOARDING_CLIENT_SECRET` | *required* - without it every endpoint answers 503 |
| `ENTRA_API_CLIENT_ID` | `30288840-...`, `hmcts-api-marketplace`, which defines `slc.read` and `app.read` |
| `ENTRA_MAX_ATTEMPTS` | `20` - directory reads, which lag by seconds |
| `ENTRA_RETRY_DELAY_MILLIS` | `500` |
| `ENTRA_ASSIGNMENT_ATTEMPTS` | `10` - role assignment, which can be refused for minutes |
| `ENTRA_ASSIGNMENT_DELAY_MILLIS` | `15000` |
