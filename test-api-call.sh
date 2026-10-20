#!/usr/bin/env bash
#
# Credentials you already have, through the gateway:
#
#   client id + secret -> token carrying the role -> call slc through APIM
#
# The half of test-app-registration.sh that does not create anything, so an application issued
# earlier can be checked again without registering another one.
#
#   ./test-api-call.sh <clientId> <clientSecret> [subscriptionKey]
#
# The gateway wants the subscription key as well as the token. Supply it as the third argument or
# in APIM_SUBSCRIPTION_KEY; or give SUBSCRIPTION and REQUESTING_USER_ID and it is read from the
# running marketplace instead.
#
# Optional: CASE_URN, ROLE, TOKEN_ATTEMPTS, BASE_URL, APIM_GATEWAY, ENTRA_TENANT_ID,
# ENTRA_API_CLIENT_ID.
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
TENANT=${ENTRA_TENANT_ID:-d44f885c-4fac-47bf-afde-d7d861ec4d7b}
API_CLIENT_ID=${ENTRA_API_CLIENT_ID:-30288840-e345-4543-99ee-f9253d789339}
GATEWAY=${APIM_GATEWAY:-https://sps-api-mgmt.sandbox.api.hmcts.net}
ROLE=${ROLE:-app.read}
CASE_URN=${CASE_URN:-28DI3451856}
TOKEN_ATTEMPTS=${TOKEN_ATTEMPTS:-10}
HINT=""

step () { printf '\n\033[1m%s\033[0m\n' "$*"; }
die  () { printf '\n%s\n' "$*" >&2; exit 1; }
usage () { die "Usage: ./test-api-call.sh <clientId> <clientSecret> [subscriptionKey]"; }

command -v jq >/dev/null || die "jq is needed. brew install jq"
[ $# -ge 2 ] || usage
CLIENT_ID=$1
CLIENT_SECRET=$2
APIM_SUBSCRIPTION_KEY=${3:-${APIM_SUBSCRIPTION_KEY:-}}

# Reading the key back from the marketplace beats pasting one in, and proves the subscription is
# still there rather than only that the string still works.
if [ -z "$APIM_SUBSCRIPTION_KEY" ]; then
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

step "1. Get a token for $API_CLIENT_ID carrying $ROLE"
# Fewer attempts than the registration script: an established application needs no waiting. The
# retry is here for one issued moments ago, where the secret or the role assignment may not have
# replicated yet, and a token with "roles": null is refused by validate-jwt two steps later.
for attempt in $(seq 1 "$TOKEN_ATTEMPTS"); do
  # Not -f: a 401 carries the JSON saying which of the two waits this is, and -f throws it away.
  TOKEN_RESPONSE=$(curl -sS -X POST "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token" \
    -d "client_id=$CLIENT_ID" --data-urlencode "client_secret=$CLIENT_SECRET" \
    -d "scope=$API_CLIENT_ID/.default" -d "grant_type=client_credentials" || true)
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
       -H 'Content-Type: application/json' -d '{\"role\":\"$ROLE\"}'"
    TOKEN=""
  fi

  [ "$attempt" = "$TOKEN_ATTEMPTS" ] && die "   Gave up after $TOKEN_ATTEMPTS attempts: $WAITING_FOR$HINT"
  echo "   attempt $attempt: $WAITING_FOR"
  sleep 2
done
claims "$TOKEN" | jq '{aud, roles, azp}' | sed 's/^/   /'

step "2. Call slc through APIM"
echo "   GET $GATEWAY/amp/slc/case/$CASE_URN/courtschedule"
: > /tmp/test-api-call-body
# The API requires a correlation id of its own, on top of the token and the subscription key.
CORRELATION_ID=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
# -sS so curl says why it failed, and the rc captured rather than left to set -e: a transport
# failure here is worth reporting, not worth exiting on without a word.
STATUS=$(curl -sSL -o /tmp/test-api-call-body -w '%{http_code}' \
  "$GATEWAY/amp/slc/case/$CASE_URN/courtschedule" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Ocp-Apim-Subscription-Key: $APIM_SUBSCRIPTION_KEY" \
  -H "X-Correlation-ID: $CORRELATION_ID") || CURL_RC=$?
if [ -n "${CURL_RC:-}" ]; then
  die "   Could not reach $GATEWAY (curl exit $CURL_RC). Nothing was sent to APIM.
   The sandbox gateway is reachable from inside the HMCTS network - connect to the VPN and run
   it again. The token in step 1 is unaffected."
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
   'failed CNP validation' means the token's audience or roles did not satisfy the policy." ;;
  200) echo "
   Credentials, subscription key and gateway all good." ;;
  *)   echo "
   Past the gateway - validate-jwt and the subscription key both passed, and this is the
   backend's own answer. A 404 here usually just means that case URN does not exist." ;;
esac
