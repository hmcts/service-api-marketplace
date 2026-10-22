# Entra application registrations

## Operations

| Method | Operation | Path | Success |
|---|---|---|---|
| GET | listApplications | `/applications` | 200 |
| GET | getApplication | `/applications/{clientId}` | 200 |
| POST | createApplication | `/applications` | 201 |
| POST | grantRole | `/applications/{clientId}/roles` | 204 |
| DELETE | deleteApplication | `/applications/{clientId}` | 204 |

`{clientId}` is the Entra **Client ID** (`appId`) - the one a subscriber authenticates with, and
the one `createApplication` returns.

Every call carries a `requestingUserId` header, the caller's Entra object id, and an application
belongs to whoever registered it: somebody else's reads as 404 rather than 403. Registering also
writes a row, so the application is listable and deletable afterwards.

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
| `name` | string | yes | Letters, digits and spaces, 8 to 50 characters. The hyphenated `appname` and the Entra display name `amp-userNNN-<appname>` are derived from it |
| `roles` | string[] | no | Granted inline if given. Omit and use `grantRole`, which is what `test-app-registration.sh` does |

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
| 400 | `name` blank, or a role the API does not offer - the message lists the ones it does |
| 500 | Graph refused the role assignment; the message names the permission that is missing |
| 502 | Graph could not be reached, or a call did not succeed within its retries |
| 503 | `ENTRA_ONBOARDING_CLIENT_SECRET` is not configured |

**Anything that fails after the application exists takes it away again**, a role that cannot be
granted included.

## grantRole

Assigns one app role on the marketplace API's service principal, so the consumer's token starts
carrying it. Nothing in APIM changes - its `validate-jwt` policy already requires the role.

**Request**

| Field | Type | Required |
|---|---|---|
| `role` | string | yes |

**Response** - 204, no body.

**Failures**

| Status | When |
|---|---|
| 400 | The API offers no such role; the message lists the ones it does |
| 404 | No service principal for that Client ID - usually means it has not replicated yet |
| 500 | Graph refused; the message names `AppRoleAssignment.ReadWrite.All` |
| 502 | Graph could not be reached |

**Not retried here.** One attempt, and the failure is returned. A principal that is not assignable
yet looks the same as one that never will be, and only the caller knows how long it is prepared to
wait - `test-app-registration.sh` tries eight times, fifteen seconds apart.

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
# sign in first: nothing below works until this service holds a row for you
OID=11111111-2222-3333-4444-555555555555
curl -s -X POST localhost:8080/session -H 'Content-Type: application/json' \
  -d "{\"objectId\":\"$OID\",\"email\":\"you@hmcts.net\",\"name\":\"Your Name\"}" | jq
```

```bash
# list your own ... oldest first, never with a secret
curl -s localhost:8080/applications -H "requestingUserId: $OID" | jq '.[] | {clientId, appname, createdAt}'
```

```bash
# register one ... this is the only moment the secret exists
curl -s -X POST localhost:8080/applications \
  -H "requestingUserId: $OID" -H 'Content-Type: application/json' \
  -d '{"name":"Colin Test App"}' | jq
```

```bash
# grant it access to one API ... retry this, the principal is not assignable for a minute or two
curl -i -X POST localhost:8080/applications/<clientId>/roles \
  -H "requestingUserId: $OID" -H 'Content-Type: application/json' -d '{"shortCode":"slc"}'
```

```bash
# read the stored row back ... no secret, only its first three characters
curl -s localhost:8080/applications/<clientId> -H "requestingUserId: $OID" | jq
```

```bash
# remove the application ... be careful, this revokes its credentials
curl -i -X DELETE localhost:8080/applications/<clientId> -H "requestingUserId: $OID"
```

**Clean up after yourself.** These are real writes to the `hmctsextsbox` tenant, and what you
leave behind shows in the Entra portal under App registrations.

### The whole journey in one run

`test-app-registration.sh` does all of it against the real sandbox - register, grant the role, wait for a
token that actually carries it, call `slc` through APIM - and deletes the application again however
the run turns out:

```bash
export APIM_SUBSCRIPTION_KEY='<the slc product key - quoted>'
./test-app-registration.sh
```

`KEEP=1` leaves the application behind; `ROLE`, `NAME`, `CASE_URN`, `BASE_URL`, `GRANT_ATTEMPTS`
and `TOKEN_ATTEMPTS` override the defaults.

**Two waits, not one.** The script retries the grant while the principal becomes assignable, and
then retries the *token* until the role is actually in it. The assignment reaches the directory
before the token service sees it, so the first token back can carry `"roles": null` - which APIM
rejects two steps later as *"Your JWT token failed CNP validation"*, a long way from the cause. The gateway is only reachable from inside the HMCTS network, so the last step needs the
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

Decode it and check two claims. **`"roles": ["app.read"]`** - a token with `"roles": null` is
authenticated and authorised for nothing, which is exactly what the role assignment exists to fix.
**`aud`** - it is the resource app of the API you asked for, and a token for one API is refused by
another before any role is read.

## Configuration

| Setting | Default |
|---|---|
| `ENTRA_TENANT_ID` | `d44f885c-...`, the `hmctsextsbox` CIAM tenant |
| `ENTRA_ONBOARDING_CLIENT_ID` | `aba0879b-...`, `amp-role-assignment-test` |
| `ENTRA_ONBOARDING_CLIENT_SECRET` | *required* - without it every endpoint answers 503 |
| `ENTRA_RESOURCE_APP_ID_SLC` | The app id of `amp-api-slc` in this environment's tenant |
| `ENTRA_RESOURCE_APP_ID_RCC` | The app id of `amp-api-rcc`. Blank refuses a grant for that API with a 503 |
| `ENTRA_MAX_ATTEMPTS` | `20` - directory reads, which lag by seconds |
| `ENTRA_RETRY_DELAY_MILLIS` | `500` |
