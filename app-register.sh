#!/usr/bin/env bash
#
# The whole credential journey against the real sandbox, in one run:
#
#   register an application with a role  ->  get a token  ->  call slc through APIM
#
# The application is deleted again at the end, however the run turns out. KEEP=1 leaves it behind.
#
# Needs the service running (./gradlew bootRun) and a subscription key for the slc product:
#
#   export APIM_SUBSCRIPTION_KEY='<paste the slc product key - quoted>'
#   ./app-register.sh
#
# Optional: CASE_URN, ROLE, NAME, BASE_URL, KEEP=1 to leave the application behind.
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

# One call, unlike the demo's two: the role is part of the registration here, and the service retries
# the assignment while the new service principal becomes assignable. Entra rejects it as "Not a valid
# reference update" until then, which can take minutes rather than seconds - so this step is slow
# before it is successful, and a failure takes the half-made application away with it.
step "1. Register an application named $NAME with $ROLE (deleted again at the end unless KEEP=1)"
echo "   this can take a few minutes while the service principal becomes assignable"
REG_STATUS=$(curl -s -o /tmp/app-register-reg -w '%{http_code}' -X POST "$APPLICATIONS" \
  -H 'Content-Type: application/json' -d "{\"name\":\"$NAME\",\"roles\":[\"$ROLE\"]}")
if [ "$REG_STATUS" != "201" ]; then
  die "Registration failed (HTTP $REG_STATUS):
   $(jq -r '.detail // .' /tmp/app-register-reg 2>/dev/null || cat /tmp/app-register-reg)"
fi
REGISTRATION=$(cat /tmp/app-register-reg)
CLIENT_ID=$(jq -r .clientId <<<"$REGISTRATION")
CLIENT_SECRET=$(jq -r .clientSecret <<<"$REGISTRATION")
echo "   name     $NAME"
jq -r '"   clientId \(.clientId)\n   roles    \(.roles | join(", "))"' <<<"$REGISTRATION"

cleanup () {
  step "4. Delete the application"
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

step "2. Get a token for $API_CLIENT_ID"
# Retried: a secret seconds old is rejected as invalid_client until Entra has replicated it.
for attempt in $(seq 1 20); do
  TOKEN_RESPONSE=$(curl -fsS -X POST "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token" \
    -d "client_id=$CLIENT_ID" --data-urlencode "client_secret=$CLIENT_SECRET" \
    -d "scope=$API_CLIENT_ID/.default" -d "grant_type=client_credentials" || true)
  TOKEN=$(jq -r '.access_token // empty' <<<"$TOKEN_RESPONSE")
  [ -n "$TOKEN" ] && { [ "$attempt" -gt 1 ] && echo "   (took $attempt attempts while the secret replicated)"; break; }
  [ "$attempt" = 20 ] && die "   No token after 20 attempts: $(jq -r '.error_description // .' <<<"$TOKEN_RESPONSE" | head -1)"
  sleep 2
done
# roles is the one worth looking at: null here means authenticated and authorised for nothing.
jq -R 'split(".")[1] | @base64d | fromjson | {aud, roles, azp}' <<<"$TOKEN" | sed 's/^/   /'

step "3. Call slc through APIM"
echo "   GET $GATEWAY/amp/slc/case/$CASE_URN/courtschedule"
# The API requires a correlation id of its own, on top of the token and the subscription key.
: > /tmp/app-register-body
# -sS so curl says why it failed, and the rc captured rather than left to set -e: a transport
# failure here is worth reporting, not worth exiting on without a word.
CORRELATION_ID=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
STATUS=$(curl -sSL -o /tmp/app-register-body -w '%{http_code}' \
  "$GATEWAY/amp/slc/case/$CASE_URN/courtschedule" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Ocp-Apim-Subscription-Key: $APIM_SUBSCRIPTION_KEY" \
  -H "X-Correlation-ID: $CORRELATION_ID") || CURL_RC=$?
if [ -n "${CURL_RC:-}" ]; then
  die "   Could not reach $GATEWAY (curl exit $CURL_RC). Nothing was sent to APIM.
   The sandbox gateway is reachable from inside the HMCTS network - connect to the VPN and run
   it again. Steps 1-2 are unaffected."
fi
echo "   HTTP $STATUS"
if jq -e . /tmp/app-register-body >/dev/null 2>&1; then
  jq . /tmp/app-register-body | sed 's/^/   /'
else
  sed 's/^/   /' /tmp/app-register-body; echo
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
