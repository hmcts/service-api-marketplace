#!/usr/bin/env bash
#
# The whole credential journey against the real sandbox, in one run:
#
#   sign in -> register -> grant a role -> subscribe -> get a token with the role -> call slc
#
# The user and the application are read back from the database on the way through: an application
# Entra has but we hold no row for is one nobody can list or delete.
#
# The application and the subscription are both removed at the end, however the run turns out.
# KEEP=1 leaves them behind.
#
# Needs the service running (./gradlew bootRun) with the onboarding secret exported, and the
# credentials it uses to reach APIM:
#
#   ./test-app-registration.sh
#
# Optional: CASE_URN, ROLE, NAME, PRODUCT_ID, BASE_URL, GRANT_ATTEMPTS, TOKEN_ATTEMPTS,
# GATEWAY_ATTEMPTS, KEEP=1,
# REQUESTING_USER_ID to run as somebody else.
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
APPLICATIONS=$BASE_URL/applications
USERS=$BASE_URL/users
SUBSCRIPTION_KEYS=$BASE_URL/apim/subscription-keys
SESSION=$BASE_URL/session
# The same object id every run, so the script keeps reusing one marketplace_user row.
REQUESTING_USER_ID=${REQUESTING_USER_ID:-11111111-2222-3333-4444-555555555555}
TENANT=${ENTRA_TENANT_ID:-d44f885c-4fac-47bf-afde-d7d861ec4d7b}
API_CLIENT_ID=${ENTRA_API_CLIENT_ID:-30288840-e345-4543-99ee-f9253d789339}
GATEWAY=${APIM_GATEWAY:-https://sps-api-mgmt.sandbox.api.hmcts.net}
ROLE=${ROLE:-app.read}
# Named after whoever ran it: killing the script skips the trap, so orphans happen, and an orphan
# nobody can be traced to is one nobody will delete.
NAME=${NAME:-Test App $(whoami | tr -cd '[:alnum:]') $(date +%H%M%S)}
CASE_URN=${CASE_URN:-28DI3451856}
PRODUCT_ID=${PRODUCT_ID:-cp-crime-schedulingandlisting}
SUBSCRIBED=0

step () { printf '\n\033[1m%s\033[0m\n' "$*"; }
die  () { printf '\n%s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null || die "jq is needed. brew install jq"
# /api/health rather than one of the endpoints below: those want a caller, and "nothing is
# running" should not come back looking like a rejected request.
curl -fsS -o /dev/null "$BASE_URL/api/health" 2>/dev/null \
  || die "Nothing answering at $BASE_URL. Start it with ./gradlew bootRun (and the onboarding secret exported)."

step "1. Sign in as $REQUESTING_USER_ID"
SESSION_STATUS=$(curl -s -o /tmp/test-app-registration-session -w '%{http_code}' -X POST "$SESSION" \
  -H 'Content-Type: application/json' \
  -d "{\"objectId\":\"$REQUESTING_USER_ID\",\"email\":\"$(whoami)@hmcts.net\",\"name\":\"Script Runner\"}")
if [ "$SESSION_STATUS" != "200" ]; then
  die "   Could not sign in (HTTP $SESSION_STATUS):
   $(jq -r '.detail // .error // .' /tmp/test-app-registration-session 2>/dev/null || cat /tmp/test-app-registration-session)"
fi
USER_REFERENCE=$(jq -r .userReference /tmp/test-app-registration-session)
USER_ID=$(jq -r .id /tmp/test-app-registration-session)
# Everything this user owns in Entra and APIM is named from here, and the subscription endpoints
# read ownership straight off the name rather than out of a table.
echo "   $USER_REFERENCE (id $USER_ID), so subscriptions are named amp-$USER_REFERENCE-*"

step "2. Read the user back"
USER_STATUS=$(curl -s -o /tmp/test-app-registration-user -w '%{http_code}' "$USERS/$USER_ID" \
  -H "requestingUserId: $REQUESTING_USER_ID")
if [ "$USER_STATUS" != "200" ]; then
  die "   Could not read the user (HTTP $USER_STATUS):
   $(jq -r '.error // .detail // .' /tmp/test-app-registration-user 2>/dev/null || cat /tmp/test-app-registration-user)"
fi
jq -r '"   \(.email), \(.status), last signed in \(.lastLoginAt)"' /tmp/test-app-registration-user

step "3. Register an application named $NAME (deleted again at the end unless KEEP=1)"
REG_STATUS=$(curl -s -o /tmp/test-app-registration-reg -w '%{http_code}' -X POST "$APPLICATIONS" \
  -H "requestingUserId: $REQUESTING_USER_ID" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$NAME\"}")
if [ "$REG_STATUS" != "201" ]; then
  die "Registration failed (HTTP $REG_STATUS):
   $(jq -r '.detail // .' /tmp/test-app-registration-reg 2>/dev/null || cat /tmp/test-app-registration-reg)"
fi
REGISTRATION=$(cat /tmp/test-app-registration-reg)
CLIENT_ID=$(jq -r .clientId <<<"$REGISTRATION")
CLIENT_SECRET=$(jq -r .clientSecret <<<"$REGISTRATION")
APPNAME=$(jq -r .appname <<<"$REGISTRATION")
SUBSCRIPTION=${SUBSCRIPTION:-amp-$USER_REFERENCE-$APPNAME}
jq -r '"   clientId \(.clientId), appname \(.appname), secret \(.secretHint)..."' <<<"$REGISTRATION"

cleanup () {
  step "10. Remove the subscription and the application"
  if [ "${KEEP:-0}" = "1" ]; then
    printf '   KEEP=1, so these are left behind. Remove them with:\n'
    if [ "$SUBSCRIBED" = "1" ]; then
      printf '     curl -X DELETE %s/%s -H "requestingUserId: %s"\n' \
        "$SUBSCRIPTION_KEYS" "$SUBSCRIPTION" "$REQUESTING_USER_ID"
    fi
    printf '     curl -X DELETE %s/%s\n' "$APPLICATIONS" "$CLIENT_ID"
    return 0
  fi
  if [ "$SUBSCRIBED" = "1" ]; then
    curl -fsS -o /dev/null -X DELETE "$SUBSCRIPTION_KEYS/$SUBSCRIPTION" \
      -H "requestingUserId: $REQUESTING_USER_ID" 2>/dev/null \
      && printf '   revoked subscription %s\n' "$SUBSCRIPTION" \
      || printf '   could not revoke subscription %s - remove it by hand\n' "$SUBSCRIPTION"
  fi
  curl -fsS -o /dev/null -X DELETE "$APPLICATIONS/$CLIENT_ID" \
    -H "requestingUserId: $REQUESTING_USER_ID" 2>/dev/null \
    && printf '   deleted %s (%s)\n' "$NAME" "$CLIENT_ID" \
    || printf '   could not delete %s (%s) - remove it by hand\n' "$NAME" "$CLIENT_ID"
  return 0
}
trap cleanup EXIT

step "4. Read the application back"
APP_STATUS=$(curl -s -o /tmp/test-app-registration-app -w '%{http_code}' "$APPLICATIONS/$CLIENT_ID" \
  -H "requestingUserId: $REQUESTING_USER_ID")
if [ "$APP_STATUS" != "200" ]; then
  die "   Entra has the application but we hold no row for it (HTTP $APP_STATUS):
   $(jq -r '.error // .detail // .' /tmp/test-app-registration-app 2>/dev/null || cat /tmp/test-app-registration-app)"
fi
jq -r '"   id \(.id), \(.name), created \(.createdAt), secret expires \(.secretExpiresAt)"' \
  /tmp/test-app-registration-app
# The secret is returned by create and never again, so a read that carries one is a bug.
if [ "$(jq -r '.clientSecret // "absent"' /tmp/test-app-registration-app)" != "absent" ]; then
  die "   The read-back carried a clientSecret. It is only ever returned by create."
fi

step "5. Subscribe to $PRODUCT_ID"
KEY_STATUS=$(curl -s -o /tmp/test-app-registration-key -w '%{http_code}' -X POST "$SUBSCRIPTION_KEYS" \
  -H "requestingUserId: $REQUESTING_USER_ID" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$SUBSCRIPTION\",\"productId\":\"$PRODUCT_ID\",\"displayName\":\"$NAME\"}")
if [ "$KEY_STATUS" != "201" ]; then
  die "   Could not subscribe (HTTP $KEY_STATUS):
   $(jq -r '.detail // .' /tmp/test-app-registration-key 2>/dev/null || cat /tmp/test-app-registration-key)"
fi
SUBSCRIBED=1
APIM_SUBSCRIPTION_KEY=$(jq -r .primaryKey /tmp/test-app-registration-key)
printf '   %s, primary key %s...\n' "$SUBSCRIPTION" "${APIM_SUBSCRIPTION_KEY:0:4}"

step "6. Read the subscription back"
READ_STATUS=$(curl -s -o /tmp/test-app-registration-readback -w '%{http_code}' \
  "$SUBSCRIPTION_KEYS/$SUBSCRIPTION/values" -H "requestingUserId: $REQUESTING_USER_ID")
if [ "$READ_STATUS" != "200" ]; then
  die "   APIM took the subscription but will not read it back (HTTP $READ_STATUS):
   $(jq -r '.error // .detail // .' /tmp/test-app-registration-readback 2>/dev/null \
     || cat /tmp/test-app-registration-readback)"
fi
if [ "$(jq -r .primaryKey /tmp/test-app-registration-readback)" != "$APIM_SUBSCRIPTION_KEY" ]; then
  die "   The key read back is not the one create returned."
fi
# Only proves API Management has it. The gateway keeps its own cache and may still refuse the key
# for a while, which is why step 9 retries rather than failing on the first 401.
echo "   API Management has it and the key matches"

# Separate from the registration, because a service principal seconds old is not assignable and
# Entra says so as "Not a valid reference update" for minutes. Retried here rather than inside the
# create call, so a lag that only needs waiting out cannot roll the whole registration back.
step "7. Grant $ROLE"
GRANT_ATTEMPTS=${GRANT_ATTEMPTS:-8}
for attempt in $(seq 1 "$GRANT_ATTEMPTS"); do
  GRANT_STATUS=$(curl -s -o /tmp/test-app-registration-grant -w '%{http_code}' -X POST \
    "$APPLICATIONS/$CLIENT_ID/roles" \
    -H "requestingUserId: $REQUESTING_USER_ID" -H 'Content-Type: application/json' \
    -d "{\"role\":\"$ROLE\"}")
  [ "$GRANT_STATUS" = "204" ] && { echo "   granted on attempt $attempt"; break; }
  [ "$attempt" = "$GRANT_ATTEMPTS" ] && die "   Could not grant $ROLE after $GRANT_ATTEMPTS attempts (HTTP $GRANT_STATUS):
   $(jq -r '.detail // .' /tmp/test-app-registration-grant 2>/dev/null || cat /tmp/test-app-registration-grant)"
  echo "   attempt $attempt: HTTP $GRANT_STATUS - the principal is not assignable yet, waiting"
  sleep 15
done

# A subscription of its own rather than a key pasted in by hand: the gateway wants the key as
# well as the token, and borrowing someone else's proves nothing about this application.
claims () { jq -R 'split(".")[1] | @base64d | fromjson' <<<"$1"; }

step "8. Get a token for $API_CLIENT_ID carrying $ROLE"
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

step "9. Call slc through APIM"
echo "   GET $GATEWAY/amp/slc/case/$CASE_URN/courtschedule"
# The API requires a correlation id of its own, on top of the token and the subscription key.
: > /tmp/test-app-registration-body
# -sS so curl says why it failed, and the rc captured rather than left to set -e: a transport
# failure here is worth reporting, not worth exiting on without a word.
# The gateway caches subscriptions separately from API Management, so a key it has not picked up
# yet is refused as "invalid subscription key" - the same 401 a wrong key gets. Retried only on
# that wording: a token or policy 401 will never come good and should fail at once.
GATEWAY_ATTEMPTS=${GATEWAY_ATTEMPTS:-10}
for attempt in $(seq 1 "$GATEWAY_ATTEMPTS"); do
  CORRELATION_ID=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
  unset CURL_RC
  STATUS=$(curl -sSL -o /tmp/test-app-registration-body -w '%{http_code}' \
    "$GATEWAY/amp/slc/case/$CASE_URN/courtschedule" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Ocp-Apim-Subscription-Key: $APIM_SUBSCRIPTION_KEY" \
    -H "X-Correlation-ID: $CORRELATION_ID") || CURL_RC=$?
  if [ -n "${CURL_RC:-}" ]; then
    die "   Could not reach $GATEWAY (curl exit $CURL_RC). Nothing was sent to APIM.
   The sandbox gateway is reachable from inside the HMCTS network - connect to the VPN and run
   it again. Steps 1-8 are unaffected."
  fi
  grep -qi "invalid subscription key" /tmp/test-app-registration-body || break
  [ "$attempt" = "$GATEWAY_ATTEMPTS" ] && break
  echo "   attempt $attempt: the gateway has not picked the key up yet, waiting"
  sleep 6
done
echo "   HTTP $STATUS"
if jq -e . /tmp/test-app-registration-body >/dev/null 2>&1; then
  jq . /tmp/test-app-registration-body | sed 's/^/   /'
else
  sed 's/^/   /' /tmp/test-app-registration-body; echo
fi

case "$STATUS" in
  401) echo "
   401 means the gateway refused it. 'Missing CNP Authorization header' is a header problem;
   'failed CNP validation' means the token's audience or roles did not satisfy the policy;
   'invalid subscription key' after $GATEWAY_ATTEMPTS attempts is not a timing problem - step 6
   proved API Management has the key, so check the product actually publishes this API." ;;
  200) echo "
   The whole chain worked: registration, role, token, gateway, backend." ;;
  *)   echo "
   Past the gateway - validate-jwt and the subscription key both passed, and this is the
   backend's own answer. A 404 here usually just means that case URN does not exist." ;;
esac
