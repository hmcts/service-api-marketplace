# APIM subscription keys

Four endpoints over Azure API Management subscriptions, so a subscription key can be issued,
read, listed and revoked without going near the Azure portal.

These are APIM only. Registering an Entra application is a separate concern and is not part of
this.

## Operations

| Method | Operation | Path | Success |
|---|---|---|---|
| GET | listKeys | `/apim/subscription-keys` | 200 |
| POST | createKey | `/apim/subscription-keys` | 201 |
| GET | getKeyValues | `/apim/subscription-keys/{name}/values` | 200 |
| DELETE | deleteKey | `/apim/subscription-keys/{name}` | 204 |

`{name}` is APIM's own subscription name, the one `createKey` returns - not the display name.

All four require a `requestingUserId` header, the caller's Entra object id, exactly as the rest
of the API does. A missing header is 400 and an object id with no `marketplace_user` row is 404.

## Who a subscription belongs to

Nothing about an APIM subscription is kept in our database, so ownership is carried by the name:
it must start with the caller's prefix, `amp-user001-`, which `POST /session` returns as
`userReference`. `listKeys` shows only the caller's own, and `getKeyValues` or `deleteKey` on a
name outside that prefix answers 404 rather than 403 - the instance is shared with SPS, and a 403
would confirm the subscription exists.

> There is no way to discover a `productId`. `GET /apim/products` was removed, so the value has
> to be supplied, for example `cp-crime-schedulingandlisting-courtschedule`.

## listKeys

The caller's own subscriptions. No key values: Azure does not return them from a list, and
asking per subscription would be one extra call each.

**Request** — the `requestingUserId` header.

**Response** — an array of:

| Field | Type | Notes |
|---|---|---|
| `name` | string | APIM's subscription name, and the id for the other three calls |
| `displayName` | string | Free text, as supplied at creation |
| `scope` | string | Azure's full resource path, ending `/products/{productId}` where the subscription is scoped to one |
| `productId` | string | Lifted out of `scope`. Absent where the subscription covers the whole service or all APIs rather than a product |
| `state` | string | `active`, `suspended`, `submitted`, `rejected`, `cancelled`, `expired` |

## createKey

**Request**

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | yes | Azure's rule: lower case letters, digits and hyphens, 1-80 characters, not starting or ending with a hyphen. Must also start with the caller's `amp-userNNN-` prefix, or it is 400 |
| `productId` | string | yes | The APIM product id, not a marketplace short code |
| `displayName` | string | yes | Free text, shown in the portal |

**Response** — the four fields from listKeys, plus:

| Field | Type | Notes |
|---|---|---|
| `primaryKey` | string | Returned here because creation is the natural moment to hand it over |
| `secondaryKey` | string | For rotation without downtime |

**Failures**

| Status | When |
|---|---|
| 400 | A field is missing, or `name` does not match Azure's rule |
| 409 | A subscription of that name already exists. Create does not replace: Azure's own call is a PUT and would upsert, re-pointing an existing key at a different product and reporting it as created, so the name is checked first |

## getKeyValues

The two key values for one subscription, asked for deliberately.

Separate from listKeys on purpose: reading a key is worth being able to find in an access log,
which it is not if every read of a subscription quietly returns its secrets too.

**Request** — none beyond `{name}` in the path.

**Response**

| Field | Type |
|---|---|
| `name` | string |
| `primaryKey` | string |
| `secondaryKey` | string |

**Failures**

| Status | When |
|---|---|
| 404 | No subscription of that name, **or** the configured instance does not exist. The message names the instance it looked in, so the two can be told apart |
| 500 | API Management refused the service identity - it has no role on the instance |
| 502 | API Management could not be reached |

## deleteKey

Revokes the subscription. Both keys stop working immediately, so anything still using them
starts getting 401 from APIM.

**Request** — none beyond `{name}` in the path.

**Response** — 204, no body.

**Failures**

| Status | When |
|---|---|
| 404 | No subscription of that name |

Deliberately not idempotent. Azure answers a delete of something absent with success, which on
a shared instance leaves you unable to tell a key you revoked from one that was never there, so
the name is checked first and a second delete answers 404.

## Running against the real sbox instance

The integration test uses WireMock, so it never proves anything about Azure. To exercise the
endpoints against `sps-api-mgmt-sbox` for real:

**1. Start the database.** The application will not come up without one.

```bash
docker compose up -d db
```

**2. Point the application at sbox and run it.** `DefaultAzureCredential` tries
`EnvironmentCredential` before anything else, so these three variables are the identity and the
az CLI is never consulted. The service principal needs a role on the instance - API Management
Service Contributor is enough - and it lives in the HMCTS tenant, so the Entra onboarding
application cannot be reused for this.

```bash
./gradlew --stop
AZURE_TENANT_ID=531ff96d-0ae9-462a-8d2d-bec7c0b42082 \
AZURE_CLIENT_ID=<service principal app id> \
AZURE_CLIENT_SECRET=<its secret> \
APIM_SUBSCRIPTION_ID=bd2864ed-4f3e-45ed-9c6a-8d179674bab1 \
APIM_RESOURCE_GROUP=rg-sps-platform-sbox \
APIM_SERVICE_NAME=sps-api-mgmt-sbox \
./gradlew bootRun
```

`./gradlew --stop` first because the daemon hands its own environment to the forked `bootRun`
JVM: a variable exported after the daemon started never arrives, and you get a false failure.

The startup log confirms what it is pointed at:

```
INFO  APIM tenant 531ff96d-..., subscription bd2864ed-...
```

A line of zeros there means the variables did not reach the application, and every call will
fail against a subscription that does not exist.

**4. Call the endpoints.**

```bash
# sign in first: the reply carries userReference, which is where your amp-userNNN- prefix comes from
OID=11111111-2222-3333-4444-555555555555
curl -s -X POST localhost:8080/session -H 'Content-Type: application/json' \
  -d "{\"objectId\":\"$OID\",\"email\":\"you@hmcts.net\",\"name\":\"Your Name\"}" | jq -r .userReference
```

```bash
# list your subscription keys
curl localhost:8080/apim/subscription-keys -H "requestingUserId: $OID" \
  | jq '.[] | {name, displayName, productId, state}'
```

```bash
# Get existing key values
curl localhost:8080/apim/subscription-keys/amp-user001-colin-local-test-1/values \
  -H "requestingUserId: $OID" | jq
```

```bash
# create new subscription key ... the name has to start with your own prefix
curl -X POST localhost:8080/apim/subscription-keys \
  -H "requestingUserId: $OID" -H 'Content-Type: application/json' \
  -d '{"name":"amp-user001-colin-local-test-1","productId":"cp-crime-schedulingandlisting-courtschedule","displayName":"Colins Local test"}' | jq
```

```bash
# Delete a subscription key ... be careful of course
curl -X DELETE localhost:8080/apim/subscription-keys/amp-user001-colin-local-test-1 \
  -H "requestingUserId: $OID"
```

`test-app-registration.sh` does the whole of this end to end if you would rather not do it by hand.

**Clean up after yourself.** `sps-api-mgmt-sbox` is shared with the SPS team, and anything created
here is a real subscription on their instance until it is deleted.

### What a 500 means here

```json
{"status":500,"error":"Not permitted to manage subscriptions on API Management."}
```

API Management answered and refused. Listing works today because read is permitted; create and
delete may not be, and that is a role assignment rather than anything in this code. A 502 is the
other thing entirely - API Management could not be reached at all.

## Configuration

Bound in `application.yaml` under `apim:`, so the four the service needs are visible in one place.

| Variable | Property | Default |
|---|---|---|
| `AZURE_TENANT_ID` | `apim.tenant-id` | all zeros |
| `APIM_SUBSCRIPTION_ID` | `apim.subscription-id` | all zeros |
| `APIM_RESOURCE_GROUP` | `apim.resource-group` | `rg-sps-platform-sbox` |
| `APIM_SERVICE_NAME` | `apim.service-name` | `sps-api-mgmt-sbox` |

The two GUIDs default to zeros rather than to real values, so the application starts without
them and the endpoints fail rather than the pod. `APIM_SUBSCRIPTION_ID` is the Azure
subscription the APIM instance lives in, not an APIM subscription. Leaving it unset gets you
`InvalidSubscriptionId` from ARM, reported as a 502.

The credential is separate from those four. `AZURE_TENANT_ID`, `AZURE_CLIENT_ID` and
`AZURE_CLIENT_SECRET` are read by `DefaultAzureCredential` itself rather than by anything in
`application.yaml`; in AKS none of them are set and the pod's workload identity is used instead.

Credentials come from `DefaultAzureCredential`: the signed-in `az` user locally, and the pod's
workload identity when deployed. No client secret.
