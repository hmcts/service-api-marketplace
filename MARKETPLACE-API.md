# Marketplace API

Eighteen operations across fourteen paths. The machine-readable version is
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

## Users

| Method | Path | Returns | |
|---|---|---|---|
| GET | `/users` | 200 400 404 | Everyone who has signed in, oldest first |
| GET | `/users/{userId}` | 200 400 404 | Row id or Entra object id, whichever you have |

Deliberately not user-scoped: a marketplace user can see the others. A user exists only by having
signed in, and no environment but sbox allows a database connection, so without this there is no
way to answer "is this person registered" - which is the first question whenever a call comes back
*Sign in again*. No secrets: the same fields `/session` already returns.

`{userId}` takes either identifier because debugging starts from whichever one you happen to be
holding. All digits is the row id; anything else is an Entra object id.

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

## Production requests

| Method | Path | Returns | |
|---|---|---|---|
| POST | `/production-requests` | 201 400 404 | Records the request |
| GET | `/production-requests` | 200 400 404 | The caller's own, newest first |

**Nothing is issued here.** Client ids, secrets, subscription keys and publisher ids are still
created by hand offline; the Data Access Panel decides. This records what was asked for.

**400, not 404, for a client id that is not yours.** Unknown and someone else's answer the same
way, so the response never says whose an application is. The reference is minted here and keeps
the `PCR-` prefix.

## The API catalogue

| Method | Path | Returns | |
|---|---|---|---|
| GET | `/products` | 200 | One entry per API, by short code |

**No `requestingUserId`** — reference data with nothing of anyone's in it, and the web needs it to
render a picker.

**The short code is the key.** It is the APIM path segment after `amp/`, the bracketed suffix on
every APIM display name, and the last segment of a subscription name. `title`, `catalogueName`,
`productId`, `tryItNowProductId`, `gatewayPath` and `role` all hang off it.

**`productId` is held, not derived.** Stripping `api-` from the catalogue name gives the right
product for only half the catalogue - `slc`, `hrds` and `rcc` all disagree with it, and a wrong
product id is an APIM *Product not found* that surfaces as a 502 about an unreachable upstream.

Seeded with `slc` and `rcc`; the rest go in as each is confirmed against the instance.

## APIM

`requestingUserId` as well. A subscription belongs to whoever created it, and ownership is carried
by the name rather than a row of ours: it starts with the caller's prefix, `amp-user001-`. Listing
shows only their own, and naming someone else's reads as 404 rather than 403 - the instance is
shared with SPS, and a 403 would confirm the subscription exists.

Creating takes a `clientId` and a short code, one API per call so a UI looping over several can
show progress and report the ones that failed. The name is derived as
`amp-userNNN-<appname>-<shortCode>` rather than supplied, which is what keeps it inside the
caller's prefix, and the product comes from the catalogue. Revoking is the same endpoint in
reverse: the subscription goes from APIM and the row claiming the application has it goes too.

| Method | Path | Returns | |
|---|---|---|---|
| GET | `/apim/subscription-keys` | 200 500 502 | Never key values |
| POST | `/apim/subscription-keys` | 201 400 404 409 500 502 | Subscribes one application to one API |
| GET | `/apim/subscription-keys/{name}/values` | 200 404 500 502 | |
| DELETE | `/apim/subscription-keys/{name}` | 204 404 500 502 | Takes the `application_api` row with it |

**Subscribing twice answers 409.** Azure's own call is a PUT: it would silently re-point an
existing subscription at a different product and report it as created.

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

- `/apim/products` is not built; without it there is no way to discover a `productId`, which
  creating a subscription key requires.
- `/applications/{clientId}/keys/{api}` is specified and not built. Subscribing writes an
  `application_api` row, so the record of which APIs an application has subscribed to exists, but
  nothing reads it back over the API.
