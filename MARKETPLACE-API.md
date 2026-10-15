# Marketplace API

Fourteen operations across eleven paths. The machine-readable version is
[`infrastructure/apim/specs/api-marketplace.openapi.yml`](infrastructure/apim/specs/api-marketplace.openapi.yml);
this is the same thing at a glance.

A signed-in person registers an application and receives a Client ID, a Client Secret and one APIM
subscription key per API, which together get them through the gateway.

## Service

| Method | Path | Returns |
|---|---|---|
| GET | `/` | 200 |
| GET | `/api/health` | 200 |

## Session

| Method | Path | Request | Returns |
|---|---|---|---|
| POST | `/session` | `objectId`, `email`, `name` | 200 400 |

The only write with no `requestingUserId`, because it is what establishes the user. The web app
signs the person in against Entra, parses the token and posts the claims; this upserts on the Entra
object id and returns the long id and `userReference`.

## Applications

Every one carries `requestingUserId` — the caller's Entra object id, in the header.

| Method | Path | Returns | |
|---|---|---|---|
| GET | `/applications` | 200 404 | The list, from stored data alone |
| POST | `/applications` | 201 400 404 409 502 503 | 409 is a duplicate appname |
| GET | `/applications/{clientId}` | 200 404 | |
| DELETE | `/applications/{clientId}` | 204 404 502 | |
| POST | `/applications/{clientId}/roles` | 204 400 404 500 502 | One call per API |
| GET | `/applications/{clientId}/keys/{api}` | 200 404 502 | Reads the key from APIM |

**Roles are a second call.** A service principal seconds old is not yet assignable - Entra refuses
with *"Not a valid reference update"* and can keep refusing for minutes. Folding the grant into
create meant one request that could block for minutes and then roll a good registration back over a
lag that only needed waiting out. Split, create returns as fast as Entra allows and the waiting
belongs to the caller, who is also the only one who can show progress for it.

**`clientSecret` appears in exactly one response**, the 201 from create. Entra returns it once and
has no call that reads it back, so a lost secret means regenerating rather than looking it up. A
subscription key is the opposite: always recoverable from APIM, which is what the `keys/{api}` call
does.

**404, not 403, for someone else's application.** A 403 confirms it exists and belongs to somebody.

## APIM

`requestingUserId` as well. A subscription belongs to whoever created it, and ownership is carried
by the name rather than a row of ours: it has to start with the caller's prefix, `amp-user001-`.
Listing shows only their own, and naming someone else's reads as 404 rather than 403 - the instance
is shared with SPS, and a 403 would confirm the subscription exists.

| Method | Path | Returns | |
|---|---|---|---|
| GET | `/apim/products` | 200 502 | What the create form offers |
| GET | `/apim/subscription-keys` | 200 500 502 | Never key values |
| POST | `/apim/subscription-keys` | 201 400 409 500 502 | |
| GET | `/apim/subscription-keys/{name}/values` | 200 404 500 502 | |
| DELETE | `/apim/subscription-keys/{name}` | 204 404 500 502 | |

**Create answers 409 on a clash.** Azure's own call is a PUT: it would silently re-point an existing
subscription at a different product and report it as created.

**Delete is deliberately not idempotent.** Azure answers a delete of something absent with success,
which on an instance shared with the SPS team leaves you unable to tell a key you revoked from one
that was never there.

**`/values` is separate from the list** so that reading a key appears in an access log, which it does
not if every list quietly returns secrets.

## What the status codes mean

| | |
|---|---|
| 400 | A field is missing or breaks its rule |
| 404 | Not found, or not yours |
| 409 | The name is taken |
| 500 | **The upstream answered and refused.** A missing role assignment or Graph permission. Not retryable |
| 502 | **The upstream could not be reached.** Retryable; nothing was left half-created |
| 503 | This environment is not configured. Should never reach a real user |

The 500 and 502 distinction is the useful one: 500 means someone needs to grant a permission, 502
means try again.

## Not built yet

- `/applications` and `/session` are the design. Today the same work is reachable at
  `/applications-new` and `PUT /users/entra/{entraObjectId}`, which go when these land.
- `/apim/products` was removed with the legacy endpoints and needs restoring; without it there is no
  way to discover a `productId`, which creating a subscription key requires.
