# APIM subscription keys

Four endpoints over Azure API Management subscriptions, so a subscription key can be issued,
read, listed and revoked without going near the Azure portal.

These are APIM only. Registering an Entra application is a separate concern and is not part of
this.

## Operations

| Method | Operation | Path | Success |
|---|---|---|---|
| GET | listProducts | `/apim/products` | 200 |
| GET | listKeys | `/apim/subscription-keys` | 200 |
| POST | createKey | `/apim/subscription-keys` | 201 |
| GET | getKeyValues | `/apim/subscription-keys/{name}/values` | 200 |
| DELETE | deleteKey | `/apim/subscription-keys/{name}` | 204 |

`{name}` is APIM's own subscription name, the one `createKey` returns - not the display name.

## listProducts

The products a key can be created against. Without this there is no way to discover a
`productId`, which `createKey` requires.

**Request** — none.

**Response** — an array of:

| Field | Type | Notes |
|---|---|---|
| `productId` | string | Pass this to `createKey` |
| `displayName` | string | |
| `description` | string | Absent where the product has none |
| `state` | string | `published` or `notPublished` |

## listKeys

Every subscription on the instance. No key values: Azure does not return them from a list, and
asking per subscription would be one extra call each.

**Request** — none.

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
| `name` | string | yes | Azure's rule: lower case letters, digits and hyphens, 1-80 characters, not starting or ending with a hyphen |
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

**2. Sign in.** `DefaultAzureCredential` falls back to the az CLI, so your own account is the
identity - not the pod's. Being able to do something here does not mean the deployed service can.

```bash
az login
az account set --subscription bd2864ed-4f3e-45ed-9c6a-8d179674bab1
```

**3. Point the application at sbox and run it.**

```bash
AZURE_TENANT_ID=531ff96d-0ae9-462a-8d2d-bec7c0b42082 \
APIM_SUBSCRIPTION_ID=bd2864ed-4f3e-45ed-9c6a-8d179674bab1 \
APIM_RESOURCE_GROUP=rg-sps-platform-sbox \
APIM_SERVICE_NAME=sps-api-mgmt-sbox \
./gradlew bootRun
```

The startup log confirms what it is pointed at:

```
INFO  APIM tenant 531ff96d-..., subscription bd2864ed-...
```

A line of zeros there means the variables did not reach the application, and every call will
fail against a subscription that does not exist.

**4. Call the endpoints.**

```bash
# list the products a key can be created against ... productId is what createKey needs
curl localhost:8080/apim/products | jq '.[] | {productId, displayName, state}'
```

```bash
# list all subscription keys
curl localhost:8080/apim/subscription-keys | jq '.[] | {name, displayName, productId, state}'
```

```bash
# Get existing key values
curl localhost:8080/apim/subscription-keys/6a6224805ddd171a701a022c/values | jq
```

```bash
# create new subscription key ... maybe get productId from an existing key
curl -X POST localhost:8080/apim/subscription-keys -H 'Content-Type: application/json' \
  -d '{"name":"colin-local-test-1","productId":"cp-crime-schedulingandlisting","displayName":"Colins Local test"}' | jq
```

```bash
# Delete a subscription key ... be careful of course
curl -X DELETE localhost:8080/apim/subscription-keys/colin-local-test-1
```

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
| `APIM_TENANT_ID` | `apim.tenant-id` | all zeros |
| `APIM_SUBSCRIPTION_ID` | `apim.subscription-id` | all zeros |
| `APIM_RESOURCE_GROUP` | `apim.resource-group` | `rg-sps-platform-sbox` |
| `APIM_SERVICE_NAME` | `apim.service-name` | `sps-api-mgmt-sbox` |

The two GUIDs default to zeros rather than to real values, so the application starts without
them and the endpoints fail rather than the pod. `APIM_SUBSCRIPTION_ID` is the Azure
subscription the APIM instance lives in, not an APIM subscription.

Credentials come from `DefaultAzureCredential`: the signed-in `az` user locally, and the pod's
workload identity when deployed. No client secret.
