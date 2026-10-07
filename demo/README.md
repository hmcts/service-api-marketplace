# Demo: Entra Client ID/Secret and APIM Subscription Key, on your laptop

A self-contained, offline stack that runs the real service through the whole application journey:

1. create an account and sign in
2. register an application, and get a **Client ID and Client Secret** (Microsoft Entra)
3. connect it to an API, and get a **Subscription Key** for that API (Azure API Management)
4. add and revoke client secrets, disconnect the API, delete the application, and see each of those
   taken away in Entra and APIM too

## What is real and what is a stand-in

| | In this demo | In sandbox |
|---|---|---|
| The service, its code, its database schema and migrations | **real** | real |
| Postgres | real (Postgres 16 in Docker) | Azure Postgres Flexible Server |
| The requests to Entra, Graph and Azure Resource Manager | **real code**, same requests in the same order | same |
| Entra, Microsoft Graph and Azure APIM themselves | **a WireMock stand-in** | Microsoft's |
| Sign-in to the marketplace | the service's own email and password, signed as a bearer token | the same |

There is no Entra you can run in Docker: it is a Microsoft cloud service. The stand-in answers the
handful of calls the service makes, with fresh made-up Client IDs, secrets and keys each time. It proves
the **flow** - what is asked for, in what order, what is kept, what is cleaned up when something fails. It
does **not** prove Microsoft would accept the requests; that needs the real credentials (see "Going real").

Signing in *as a user* through Entra is not part of this. The marketplace signs its users in itself.

## Run it

You need Docker, `curl` and `python3`.

```bash
./demo/run-demo.sh
```

It builds the jar, starts three containers, runs the journey, and prints what the service asked
Entra, Graph and APIM for. The stack stays up afterwards:

| | |
|---|---|
| API | http://localhost:8080 |
| Stand-in's request log | http://localhost:8099/__admin/requests |
| Postgres | `localhost:5433` (user `postgres`, password `postgres`, database `marketplace`) |

```bash
./demo/run-demo.sh --flow   # run the journey again
./demo/run-demo.sh --up     # just start the stack (to use the frontend, below)
./demo/run-demo.sh --down   # stop it and delete its data
```

Postgres is on 5433, not 5432, so it does not clash with one you already run.

## See it in a browser

The frontend prototype (`hmcts/hmcts-api-marketplace`) can use this stack. Check out the
`feature/local-backend-demo` branch, start the stack with `./demo/run-demo.sh --up`, then:

```bash
npm install && npm run kit          # http://localhost:3100
```

Open http://localhost:3100/register/, and in the browser console, once:

```js
localStorage.setItem('hmctsMarketplaceApiBase', 'http://localhost:8080')
```

Reload. Create an account, then **Manage applications > Add new application**. The confirmation page shows
the Client Secret Entra issued; the application's page shows the Client ID, and each API you add shows its
Subscription Key. Removing the API or deleting the application removes them in Entra and APIM.

Only `localhost` is accepted for that override, so nothing can use it to send a sign-in elsewhere. Undo it
with `localStorage.removeItem('hmctsMarketplaceApiBase')`.

The dev Kit does not serve `/account/applications/new/check-answers/` or `.../confirmation/` with a trailing
slash (the exported site does). If you land on "Page not found" there, remove the final `/`.

## How it is switched on

One setting, `APPLICATION_CREDENTIALS`:

| Value | Behaviour |
|---|---|
| `local` (the default) | The service makes up the Client ID and secrets itself. No Entra, no APIM. Nothing changes for anyone who does not set it. |
| `entra` | Real Entra Client ID and secrets, and a real APIM Subscription Key for each connected API. |

Anything else stops the service starting, rather than quietly issuing made-up credentials.

`docker-compose.yml` sets `entra` and points the service at the stand-in with four settings that are
**only** for this kind of demo or a test, and must be left unset everywhere real:
`ENTRA_LOGIN_BASE_URL`, `ENTRA_GRAPH_BASE_URL`, `APIM_LOGIN_BASE_URL`, `APIM_ARM_BASE_URL`.

## What happens when something fails

The order is always: check what can be refused, then ask Entra and APIM, then write the database last -
because only the database can be rolled back.

| Step | If it fails |
|---|---|
| Create application | The Entra application is deleted again |
| New client secret | The Entra secret is revoked again |
| Connect an API | An API with no APIM Product is refused before anything is created; a key that could not be saved is deleted in APIM |
| Revoke a secret | Revoked in Entra first; if Entra refuses, it stays active here too |
| Disconnect an API / delete an application | The Subscription Key and Entra application are deleted first; if that fails, the row stays so it can be tried again. Deleting something already gone is not a failure |

## Against the real thing: `real-sandbox-check.sh`

The stack above is a stand-in. To run the same service against **Microsoft's real Graph (the External ID tenant)
and the real sandbox APIM**, once, end to end:

```bash
az login
export ENTRA_ONBOARDING_CLIENT_ID=... ENTRA_ONBOARDING_CLIENT_SECRET=...   # may create applications in the tenant
export I_UNDERSTAND_THIS_CREATES_REAL_SANDBOX_RESOURCES=yes
./demo/real-sandbox-check.sh
```

It creates **one** throwaway Entra application and **one** APIM subscription (on `apim-marketplace-sandbox`), exercises
every operation, deletes both, and checks they are gone. It refuses to run without the last line above, and never
prints a secret or key in full.

The one shortcut: the service's APIM credential is a service principal that does not exist yet, so a small local shim
hands the service **your own `az` token** for that one token request. The service's code is untouched and every request
still goes to Microsoft. What it cannot show is a service principal being allowed to do this.

What running it for real showed (four runs, 7 October 2026):

- Register, add a secret, connect an API, revoke, disconnect and delete all work against the real services.
- Graph is **slower and stranger than the stand-in**. Straight after an application is created, its next calls
  are refused for a few seconds: the service principal with a `403 Authorization_RequestDenied`, `addPassword` and
  `removePassword` with `4xx` (`No password credential found with keyId ...`). The retries absorb every one.
- Graph's *reads* lag its writes (a count of an application's secrets read 0, 1 and 2 in a row), and the APIM
  management API went on listing a subscription for **several minutes** after the service had deleted it. So the
  script does not count secrets, and reports a still-listed subscription as lag to re-check, not as a failure.
- Nothing was left behind: every application and subscription was gone when checked.

## Going real, permanently

Everything above is exercised by unit tests, by the stand-in stack and by the real check. Running it for real as a
service needs:

| What | Where it comes from | State |
|---|---|---|
| Entra client ID and secret (`ENTRA_ONBOARDING_CLIENT_ID` / `_SECRET`) | **Generated by `hmcts/external-entra-id`** for `amp-client-onboarding` into `kvspsextidsbox`: `entra-app-amp-client-onboarding-client-id` and `-secret`. Not created by hand. | Exist. Our identity cannot read them until [external-entra-id#18](https://github.com/hmcts/external-entra-id/pull/18) is merged and applied, and the Flux entry mounts them. |
| APIM credential (`APIM_CLIENT_ID` / `_SECRET`) | A service principal that can manage subscriptions on the APIM instance, stored as `marketplace-APIM-CLIENT-ID` and `-SECRET` in `apim-sbox` | Does not exist. Needs CloudOps. |
| `JWT_SECRET` (sign-in) | `marketplace-JWT-SECRET` in `apim-sbox`, created by hand | Does not exist. |
| `APIM_PRODUCT_MAP` | Which APIM Product backs which API | Not decided. |
| The mounts | Entries in `cnp-flux-config` `apps/apim/apim-marketplace/sbox.yaml` | Not written. |
| `APPLICATION_CREDENTIALS=entra` | The switch | Last, once the rest is in place. |

Two things to know about the Entra secret. It rotates at 75% of its validity (about day 274) with a hard cutover, and
the service reads it once at startup, so a rotation needs a pod restart until the service re-reads it on an auth
failure. And `amp-client-onboarding` cannot assign app roles by design: client authorisation is enforced in APIM,
by subscription key and Product scoping, not by Entra roles.

## Files

```
demo/docker-compose.yml          the three containers
demo/run-demo.sh                 build, start, run the journey, show the request log
demo/wiremock/mappings/*.json    one file per call the service makes to Entra, Graph or APIM
```
