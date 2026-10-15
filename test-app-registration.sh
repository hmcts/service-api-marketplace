#!/usr/bin/env bash
#
# The whole credential journey against the real sandbox, in one run:
#
#   register  ->  grant a role  ->  get a token that carries it  ->  call slc through APIM
#
# The application is deleted again at the end, however the run turns out. KEEP=1 leaves it behind.
#
# Needs the service running (./gradlew bootRun) and a subscription key for the slc product:
#
#   export APIM_SUBSCRIPTION_KEY='<paste the slc product key - quoted>'
#   ./test-app-registration.sh
#
# Optional: CASE_URN, ROLE, NAME, BASE_URL, GRANT_ATTEMPTS, TOKEN_ATTEMPTS, KEEP=1 to keep it.
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
APPLICATIONS=$BASE_URL/applications-new
TENANT=${ENTRA_TENANT_ID:-d44f885c-4fac-47bf-afde-d7d861ec4d7b}
API_CLIENT_ID=${ENTRA_API_CLIENT_ID:-30288840-e345-4543-99ee-f9253d789339}
GATEWAY=${APIM_GATEWAY:-https://sps-api-mgmt.sandbox.api.hmcts.net}
ROLE=${ROLE:-app.read}
# Named after whoever ran it: killing the script skips the trap, so orphans happen, and an orphan
# nobody can be traced to is one nobody will delete.
NAME=${NAME:-api-marketplace-$(whoami)-$(date +%H%M%S)}
CASE_URN=${CASE_URN:-28DI3451856}

step () { printf '\n\033[1m%s\033[0m\n' "$*"; }
die  () { printf '\n%s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null || die "jq is needed. brew install jq"
[ -n "${APIM_SUBSCRIPTION_KEY:-}" ] || die "Set APIM_SUBSCRIPTION_KEY - the slc product's subscription key."
curl -fsS -o /dev/null "$APPLICATIONS" 2>/dev/null \
  || die "Nothing answering at $APPLICATIONS. Start it with ./gradlew bootRun (and the onboarding secret exported)."

step "1. Register an application named $NAME (deleted again at the end unless KEEP=1)"
REG_STATUS=$(curl -s -o /tmp/test-app-registration-reg -w '%{http_code}' -X POST "$APPLICATIONS" \
  -H 'Content-Type: application/json' -d "{\"name\":\"$NAME\"}")
if [ "$REG_STATUS" != "201" ]; then
  die "Registration failed (HTTP $REG_STATUS):
   $(jq -r '.detail // .' /tmp/test-app-registration-reg 2>/dev/null || cat /tmp/test-app-registration-reg)"
fi
REGISTRATION=$(cat /tmp/test-app-registration-reg)
CLIENT_ID=$(jq -r .clientId <<<"$REGISTRATION")
CLIENT_SECRET=$(jq -r .clientSecret <<<"$REGISTRATION")
echo "   name     $NAME"
jq -r '"   clientId \(.clientId)"' <<<"$REGISTRATION"

cleanup () {
  step "5. Delete the application"
  if [ "${KEEP:-0}" = "1" ]; then
    printf '   KEEP=1, so %s (%s) is left in the directory. Remove it with:\n     curl -X DELETE %s/%s\n' \
      "$NAME" "$CLIENT_ID" "$APPLICATIONS" "$CLIENT_ID"
  else
    curl -fsS -o /dev/null -X DELETE "$APPLICATIONS/$CLIENT_ID" 2>/dev/null \
      && printf '   deleted %s (%s)\n' "$NAME" "$CLIENT_ID" \
      || printf '   could not delete %s (%s) - remove it by hand\n' "$NAME" "$CLIENT_ID"
  fi
}
trap cleanup EXIT

# Separate from the registration, because a service principal seconds old is not assignable and
# Entra says so as "Not a valid reference update" for minutes. Retried here rather than inside the
# create call, so a lag that only needs waiting out cannot roll the whole registration back.
step "2. Grant $ROLE"
GRANT_ATTEMPTS=${GRANT_ATTEMPTS:-8}
for attempt in $(seq 1 "$GRANT_ATTEMPTS"); do
  GRANT_STATUS=$(curl -s -o /tmp/test-app-registration-grant -w '%{http_code}' -X POST \
    "$APPLICATIONS/$CLIENT_ID/roles" \
    -H 'Content-Type: application/json' -d "{\"role\":\"$ROLE\"}")
  [ "$GRANT_STATUS" = "204" ] && { echo "   granted on attempt $attempt"; break; }
  [ "$attempt" = "$GRANT_ATTEMPTS" ] && die "   Could not grant $ROLE after $GRANT_ATTEMPTS attempts (HTTP $GRANT_STATUS):
   $(jq -r '.detail // .' /tmp/test-app-registration-grant 2>/dev/null || cat /tmp/test-app-registration-grant)"
  echo "   attempt $attempt: HTTP $GRANT_STATUS - the principal is not assignable yet, waiting"
  sleep 15
done

claims () { jq -R 'split(".")[1] | @base64d | fromjson' <<<"$1"; }

step "3. Get a token for $API_CLIENT_ID carrying $ROLE"
# Two separate waits, both Entra catching up with itself. A secret seconds old is rejected as
# invalid_client until it has replicated. Worse, once the secret works the token can come back
# without the role: the assignment is already in the directory - the GET shows it - but the token
# service has not seen it, and a token with "roles": null is refused by APIM's validate-jwt two
# steps later, a long way from the cause. Entra issues a fresh token per request rather than
# replaying a cached one, so asking again is what fixes it.
TOKEN_ATTEMPTS=${TOKEN_ATTEMPTS:-30}
for attempt in $(seq 1 "$TOKEN_ATTEMPTS"); do
  # Not -f: a 401 carries the JSON saying which of the two waits this is, and -f throws it away.
  TOKEN_RESPONSE=$(curl -sS -X POST "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token" \
    -d "client_id=$CLIENT_ID" --data-urlencode "client_secret=$CLIENT_SECRET" \
    -d "scope=$API_CLIENT_ID/.default" -d "grant_type=client_credentials" || true)
  TOKEN=$(jq -r '.access_token // empty' <<<"$TOKEN_RESPONSE")

  if [ -z "$TOKEN" ]; then
    WAITING_FOR="no token yet: $(jq -r '.error // "no response"' <<<"$TOKEN_RESPONSE")"
  elif claims "$TOKEN" | jq -e --arg r "$ROLE" '.roles // [] | index($r)' >/dev/null; then
    [ "$attempt" -gt 1 ] && echo "   (took $attempt attempts)"
    break
  else
    WAITING_FOR="token has no $ROLE yet - the directory has the assignment, the token service does not"
    TOKEN=""
  fi

  [ "$attempt" = "$TOKEN_ATTEMPTS" ] && die "   Gave up after $TOKEN_ATTEMPTS attempts: $WAITING_FOR"
  echo "   attempt $attempt: $WAITING_FOR"
  sleep 2
done
claims "$TOKEN" | jq '{aud, roles, azp}' | sed 's/^/   /'

step "4. Call slc through APIM"
echo "   GET $GATEWAY/amp/slc/case/$CASE_URN/courtschedule"
# The API requires a correlation id of its own, on top of the token and the subscription key.
: > /tmp/test-app-registration-body
# -sS so curl says why it failed, and the rc captured rather than left to set -e: a transport
# failure here is worth reporting, not worth exiting on without a word.
CORRELATION_ID=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
STATUS=$(curl -sSL -o /tmp/test-app-registration-body -w '%{http_code}' \
  "$GATEWAY/amp/slc/case/$CASE_URN/courtschedule" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Ocp-Apim-Subscription-Key: $APIM_SUBSCRIPTION_KEY" \
  -H "X-Correlation-ID: $CORRELATION_ID") || CURL_RC=$?
if [ -n "${CURL_RC:-}" ]; then
  die "   Could not reach $GATEWAY (curl exit $CURL_RC). Nothing was sent to APIM.
   The sandbox gateway is reachable from inside the HMCTS network - connect to the VPN and run
   it again. Steps 1-3 are unaffected."
fi
echo "   HTTP $STATUS"
if jq -e . /tmp/test-app-registration-body >/dev/null 2>&1; then
  jq . /tmp/test-app-registration-body | sed 's/^/   /'
else
  sed 's/^/   /' /tmp/test-app-registration-body; echo
fi

case "$STATUS" in
  401) echo "
   401 means the gateway refused it. 'Missing CNP Authorization header' is a header problem;
   'failed CNP validation' means the token's audience or roles did not satisfy the policy." ;;
  200) echo "
   The whole chain worked: registration, role, token, gateway, backend." ;;
  *)   echo "
   Past the gateway - validate-jwt and the subscription key both passed, and this is the
   backend's own answer. A 404 here usually just means that case URN does not exist." ;;
esac
