#!/usr/bin/env bash
#
# Credentials you already have, through the gateway:
#
#   client id + secret -> token carrying the role -> call the API through APIM
#
# The half of test-app-registration.sh that does not create anything, so an application issued
# earlier can be checked again without registering another one.
#
#   ./test-api-call.sh <sbox|demo> <slc|rcc> <clientId> <clientSecret> [subscriptionKey]
#
#   ./test-api-call.sh demo rcc 1a2b... s3cr3t 9f8e...
#
# The environment comes first because it names the Entra tenant, the gateway and the resource
# app the token is scoped on, which only make sense as a set. Nothing here needs the marketplace
# at all - Entra and the gateway are the only calls that leave, and both want the HMCTS VPN.
#
# The gateway wants the subscription key as well as the token. Supply it as the fifth argument
# or in APIM_SUBSCRIPTION_KEY; or give SUBSCRIPTION and REQUESTING_USER_ID and it is read from a
# marketplace at http://localhost:8080, forwarded or local (BASE_URL overrides).
#
# DIRECT=1 calls Try It Now on its own ingress instead, skipping APIM, so no subscription key is
# needed. What that proves is narrower: the gateway's own checks are the ones being skipped, so
# use it to tell a backend fault apart from a credential one rather than as the passing run.
#
# Optional: CASE_URN, COURTHOUSE_ID, REQUEST_PATH, ROLE, API_PATH, TOKEN_ATTEMPTS, BASE_URL,
# APIM_GATEWAY, ENTRA_TENANT_ID, ENTRA_RESOURCE_APP_ID.
set -euo pipefail

usage () {
  printf '\nUsage: ./test-api-call.sh <sbox|demo> <slc|rcc> <clientId> <clientSecret> [subscriptionKey]\n' >&2
  exit 1
}

# Sandbox has an Entra tenant of its own and demo shares hmctsextstg, so the marketplace, the
# tenant and the gateway have to move together: a sandbox token at the demo gateway is refused
# as though the role were missing, a long way from the cause.
# The resource apps are here rather than read from the marketplace so that this script keeps
# working when the marketplace does not - it is the one you reach for when something is broken.
# They must match entra.resource-app-ids in the environment's cnp-flux-config overlay: a stale
# id here asks for an audience the role was never granted on, and the token arrives without it.
ENVIRONMENT=${1:-}
case "$ENVIRONMENT" in
  sbox) TENANT_DEFAULT=d44f885c-4fac-47bf-afde-d7d861ec4d7b
        GATEWAY_DEFAULT=https://sps-api-mgmt.sandbox.api.hmcts.net
        SLC_APP_ID=c4c8974c-2800-4fe6-964f-dcd4c0820507
        RCC_APP_ID=97c20454-55c1-45b0-88cd-268db572f615
        BACKEND=https://amp-try-it-now.sandbox.platform.hmcts.net ;;
  demo) TENANT_DEFAULT=db827888-77df-4707-b3d9-24a3dfe32889
        GATEWAY_DEFAULT=https://sps-api-mgmt.demo.api.hmcts.net
        SLC_APP_ID=02b04e09-4f7b-4724-963b-a4c10a6825f2
        RCC_APP_ID=640c325d-5a0e-48ce-a616-710d0785f2cb
        BACKEND=https://amp-try-it-now.demo.platform.hmcts.net ;;
  *)    usage ;;
esac

BASE_URL=${BASE_URL:-http://localhost:8080}
TENANT=${ENTRA_TENANT_ID:-$TENANT_DEFAULT}
GATEWAY=${APIM_GATEWAY:-$GATEWAY_DEFAULT}
TOKEN_ATTEMPTS=${TOKEN_ATTEMPTS:-10}
HINT=""

SHORT_CODE=${2:-}
CLIENT_ID=${3:-}
CLIENT_SECRET=${4:-}
APIM_SUBSCRIPTION_KEY=${5:-${APIM_SUBSCRIPTION_KEY:-}}
[ -n "$SHORT_CODE" ] && [ -n "$CLIENT_ID" ] && [ -n "$CLIENT_SECRET" ] || usage
DIRECT=${DIRECT:-0}

step () { printf '\n\033[1m%s\033[0m\n' "$*"; }
die  () { printf '\n%s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null || die "jq is needed. brew install jq"

ROLE=${ROLE:-app.read}

# APIM publishes Try It Now under amp/try/<code>; the backend's own ingress serves it at the
# bare code, with no product in front of it to prefix.
if [ "$DIRECT" = "1" ]; then
  GATEWAY=${APIM_GATEWAY:-$BACKEND}
  PREFIX=""
else
  PREFIX="amp/try/"
fi

# The operation, per API. A key is only valid for an API in its own product and the marketplace
# subscribes to the Try It Now products. The case urn and courthouse id are canned scenarios,
# not real records - GET $GATEWAY/$API_PATH/scenarios lists what each API has.
CASE_URN=${CASE_URN:-TIN-ALLOCATED-01}
COURTHOUSE_ID=${COURTHOUSE_ID:-57323172-1083-454d-8ab7-2455a8b993e7}
case "$SHORT_CODE" in
  slc) RESOURCE_APP_ID=${ENTRA_RESOURCE_APP_ID:-$SLC_APP_ID}
       API_PATH=${API_PATH:-$PREFIX$SHORT_CODE}
       REQUEST_PATH=${REQUEST_PATH:-case/$CASE_URN/courtschedule} ;;
  rcc) RESOURCE_APP_ID=${ENTRA_RESOURCE_APP_ID:-$RCC_APP_ID}
       API_PATH=${API_PATH:-$PREFIX$SHORT_CODE}
       REQUEST_PATH=${REQUEST_PATH:-courthouses/$COURTHOUSE_ID} ;;
  *)   RESOURCE_APP_ID=${ENTRA_RESOURCE_APP_ID:-}
       [ -n "$RESOURCE_APP_ID" ] && [ -n "${API_PATH:-}" ] && [ -n "${REQUEST_PATH:-}" ] \
         || die "No known paths for $SHORT_CODE. Set ENTRA_RESOURCE_APP_ID, API_PATH and
   REQUEST_PATH, for example API_PATH=amp/try/xyz REQUEST_PATH=courthouses/<id>." ;;
esac

echo "Running against $ENVIRONMENT: tenant $TENANT, gateway $GATEWAY, audience $RESOURCE_APP_ID"

# Reading the key back from the marketplace beats pasting one in, and proves the subscription is
# still there rather than only that the string still works. Nothing checks it when the call goes
# straight to the backend, so it is not asked for then.
if [ "$DIRECT" != "1" ] && [ -z "$APIM_SUBSCRIPTION_KEY" ]; then
  [ -n "${SUBSCRIPTION:-}" ] && [ -n "${REQUESTING_USER_ID:-}" ] \
    || die "No subscription key. Pass it as the third argument, or set SUBSCRIPTION and
   REQUESTING_USER_ID to read it from $BASE_URL."
  step "Read the subscription key for $SUBSCRIPTION"
  KEY_STATUS=$(curl -s -o /tmp/test-api-call-key -w '%{http_code}' \
    "$BASE_URL/apim/subscription-keys/$SUBSCRIPTION/values" \
    -H "requestingUserId: $REQUESTING_USER_ID")
  [ "$KEY_STATUS" = "200" ] || die "   Could not read it (HTTP $KEY_STATUS):
   $(jq -r '.error // .detail // .' /tmp/test-api-call-key 2>/dev/null || cat /tmp/test-api-call-key)"
  APIM_SUBSCRIPTION_KEY=$(jq -r .primaryKey /tmp/test-api-call-key)
  printf '   %s...\n' "${APIM_SUBSCRIPTION_KEY:0:4}"
fi

claims () { jq -R 'split(".")[1] | @base64d | fromjson' <<<"$1"; }

step "1. Get a token for $RESOURCE_APP_ID carrying $ROLE"
# Fewer attempts than the registration script: an established application needs no waiting. The
# retry is here for one issued moments ago, where the secret or the role assignment may not have
# replicated yet, and a token with "roles": null is refused by validate-jwt two steps later.
for attempt in $(seq 1 "$TOKEN_ATTEMPTS"); do
  # Not -f: a 401 carries the JSON saying which of the two waits this is, and -f throws it away.
  TOKEN_RESPONSE=$(curl -sS -X POST "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token" \
    -d "client_id=$CLIENT_ID" --data-urlencode "client_secret=$CLIENT_SECRET" \
    -d "scope=$RESOURCE_APP_ID/.default" -d "grant_type=client_credentials" || true)
  TOKEN=$(jq -r '.access_token // empty' <<<"$TOKEN_RESPONSE")

  if [ -z "$TOKEN" ]; then
    WAITING_FOR="no token: $(jq -r '.error // "no response"' <<<"$TOKEN_RESPONSE")"
    HINT=""
  elif claims "$TOKEN" | jq -e --arg r "$ROLE" '.roles // [] | index($r)' >/dev/null; then
    [ "$attempt" -gt 1 ] && echo "   (took $attempt attempts)"
    break
  else
    WAITING_FOR="token has no $ROLE - either it was never granted, or Entra has not caught up"
    HINT="
   A role granted minutes ago takes a while to reach the token service, but one granted long ago
   will never appear if the grant never happened. Check with:
     curl -i -X POST $BASE_URL/applications/$CLIENT_ID/roles -H \"requestingUserId: <your oid>\" \\
       -H 'Content-Type: application/json' -d '{\"shortCode\":\"$SHORT_CODE\"}'"
    TOKEN=""
  fi

  [ "$attempt" = "$TOKEN_ATTEMPTS" ] && die "   Gave up after $TOKEN_ATTEMPTS attempts: $WAITING_FOR$HINT
   The audience is $RESOURCE_APP_ID and the token came from tenant $TENANT. An
   'unauthorized_client' or AADSTS500011 means the two disagree - check the first argument
   names the deployment this application was registered in."
  echo "   attempt $attempt: $WAITING_FOR"
  sleep 2
done
claims "$TOKEN" | jq '{aud, roles, azp}' | sed 's/^/   /'

step "2. Call $SHORT_CODE through APIM"
echo "   GET $GATEWAY/$API_PATH/$REQUEST_PATH"
: > /tmp/test-api-call-body
# The API requires a correlation id of its own, on top of the token and the subscription key.
CORRELATION_ID=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
# -sS so curl says why it failed, and the rc captured rather than left to set -e: a transport
# failure here is worth reporting, not worth exiting on without a word.
STATUS=$(curl -sSL -o /tmp/test-api-call-body -w '%{http_code}' \
  "$GATEWAY/$API_PATH/$REQUEST_PATH" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Ocp-Apim-Subscription-Key: $APIM_SUBSCRIPTION_KEY" \
  -H "X-Correlation-ID: $CORRELATION_ID") || CURL_RC=$?
if [ -n "${CURL_RC:-}" ]; then
  die "   Could not reach $GATEWAY (curl exit $CURL_RC). Nothing was sent to APIM.
   The gateway is reachable from inside the HMCTS network - connect to the VPN and run it
   again. The token in step 1 is unaffected."
fi
echo "   HTTP $STATUS"
if jq -e . /tmp/test-api-call-body >/dev/null 2>&1; then
  jq . /tmp/test-api-call-body | sed 's/^/   /'
else
  sed 's/^/   /' /tmp/test-api-call-body; echo
fi

case "$STATUS" in
  401) echo "
   401 means the gateway refused it. 'Missing CNP Authorization header' is a header problem;
   'failed CNP validation' means the token's audience or roles did not satisfy the policy;
   'invalid subscription key' usually means API_PATH names an API outside the product the key
   was issued for, rather than the key itself being wrong." ;;
  200) echo "
   Credentials, subscription key and gateway all good." ;;
  *)   if jq -e '.activityId' /tmp/test-api-call-body >/dev/null 2>&1; then
         echo "
   statusCode/message/activityId is API Management's own error shape, not the API's, so this
   did not come from the backend: the gateway accepted the token and the key and then could
   not route. Check the API's serviceUrl resolves from the gateway's network."
       else
         echo "
   Past the gateway - validate-jwt and the subscription key both passed, and this is the
   backend's own answer. A 404 here usually just means that scenario does not exist."
       fi ;;
esac
