# Organisation approval demo

Shows the organisation approval design (`ORGANISATION_APPROVAL=on`): a person signs in with Entra, the service
validates their token itself, and the **email domain** decides, against a list of pre-approved domains. There is no
manual approval. A person on a listed domain is let in automatically; anyone else is refused (403
`DOMAIN_NOT_APPROVED`) and nothing about them is saved. The list comes from configuration (`APPROVED_DOMAINS`,
for example `HMCTS=hmcts.net,justice.gov.uk;Example Council=example.gov.uk`) and can be kept up through the admin
endpoints; an admin can also suspend a leaver. The check is made on every call.

| | |
|---|---|
| `local-check.sh` | The whole flow offline: the real service and Postgres, with a stand-in that signs tokens the way Entra would. every step, exits non-zero if one is wrong. Needs Docker, Node and Java 25+. |
| `run-demo.sh` | The same service against the **real sandbox Entra tenant**. You sign in yourself in a browser through the prototype Kit (branch `demo/org-approval-signin` in `hmcts-api-marketplace`). |
| `fake-entra.js` | The token stand-in `local-check.sh` uses. |
| `docker-compose.yml` | Postgres on host port 5435. |

```bash
./local-check.sh                                  # offline proof
./run-demo.sh                                     # real Entra; prints the Kit steps (APPROVED_DOMAINS, ADMIN_EMAILS optional)
./run-demo.sh --down                              # stop
```

## What it does and does not do

- Tokens are checked for signature, issuer, audience, tenant and expiry; nothing the caller sends alongside the token
  says who they are. See `EntraTokenVerifier`.
- A person is saved once, keyed by their Entra object id (`oid`). An address that already belongs to a different
  account here is never linked (409).
- The demo sends the Entra **ID token**, issued to the prototype's sign-in app, as the bearer token. A real
  deployment should have the frontend send an access token for this service's own API instead (design: open points).
- It does not create Entra users and does not call Microsoft Graph for user details.
- Admins are the addresses in `ADMIN_EMAILS`; anyone else gets 404 from the admin endpoints. An admin needs no approved
  domain, so the first domain can be listed.
