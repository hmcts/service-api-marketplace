#!/usr/bin/env bash
# Runs THIS service against the REAL Microsoft Graph (the External ID tenant) and the REAL Azure Resource
# Manager (the shared sandbox APIM), once, end to end. The opposite of the offline demo.
#
# It creates ONE throwaway Entra application and ONE APIM subscription, then deletes both. That is a real change
# to shared sandbox services under your identity, so it refuses to run unless you say you mean it:
#
#   az login                                   # an account allowed to manage subscriptions on the sandbox APIM
#   export ENTRA_ONBOARDING_CLIENT_ID=...      # an identity allowed to create applications in the External ID tenant
#   export ENTRA_ONBOARDING_CLIENT_SECRET=...  #   (Application.ReadWrite.OwnedBy)
#   export I_UNDERSTAND_THIS_CREATES_REAL_SANDBOX_RESOURCES=yes
#   ./demo/real-sandbox-check.sh
#
# Secrets and keys are never printed in full. Needs: docker (for Postgres), az, curl, python3, Java 25.
#
# One honest shortcut. The service reads its APIM credential from a service principal that does not exist yet
# (APIM_CLIENT_ID / APIM_CLIENT_SECRET). So the one thing replaced here is where the APIM *token* comes from:
# a tiny local shim hands the service your own `az` token. The service's code is untouched, and every request
# still goes to Microsoft's real endpoints. What this does not exercise is a service principal being allowed to
# do it - that needs the credential to exist.
set -uo pipefail
cd "$(dirname "$0")/.."

[ "${I_UNDERSTAND_THIS_CREATES_REAL_SANDBOX_RESOURCES:-}" = "yes" ] || {
  echo "This creates a real Entra application and a real APIM subscription (and deletes them). Read the header, then set"
  echo "I_UNDERSTAND_THIS_CREATES_REAL_SANDBOX_RESOURCES=yes to run it."; exit 2; }
for v in ENTRA_ONBOARDING_CLIENT_ID ENTRA_ONBOARDING_CLIENT_SECRET; do
  [ -n "${!v:-}" ] || { echo "$v is not set"; exit 1; }
done
az account show >/dev/null 2>&1 || { echo "run 'az login' first"; exit 1; }

JAVA=${JAVA:-java}
PORT=${PORT:-8081}; SHIM_PORT=${SHIM_PORT:-8097}; API=http://localhost:$PORT
TENANT=${ENTRA_TENANT_ID:-d44f885c-4fac-47bf-afde-d7d861ec4d7b}
SUB=${APIM_SUBSCRIPTION_ID:-bd2864ed-4f3e-45ed-9c6a-8d179674bab1}
RG=${APIM_RESOURCE_GROUP:-rg-sps-platform-sbox}
SVC=${APIM_SERVICE_NAME:-sps-api-mgmt-sbox}
PRODUCT=${PRODUCT:-apim-marketplace-sandbox}
NAME="amp-real-check-$(date +%H%M%S)"
LOG=/tmp/real-sandbox-check.log
ARM="https://management.azure.com/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.ApiManagement/service/$SVC"

mask() { python3 -c "import sys; s=sys.stdin.read().strip(); print(s[:4]+'...('+str(len(s))+' chars)' if len(s)>8 else '(short)')"; }
say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
graph_token() { curl -s -X POST "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token" \
  -d "grant_type=client_credentials&client_id=$ENTRA_ONBOARDING_CLIENT_ID&client_secret=$ENTRA_ONBOARDING_CLIENT_SECRET&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default" | json "d['access_token']"; }

APP_ID=""; TOKEN=""; SHIM_PID=""; SVC_PID=""
cleanup() {
  say "Clean-up"
  if [ -n "$APP_ID" ] && [ -n "$TOKEN" ]; then
    curl -s -o /dev/null -w '  deleting what is left (the application and anything still issued): HTTP %{http_code}\n' \
      -X DELETE "$API/api/applications/$APP_ID" -H "Authorization: Bearer $TOKEN"
  fi
  [ -n "$SVC_PID" ] && kill "$SVC_PID" 2>/dev/null
  [ -n "$SHIM_PID" ] && kill "$SHIM_PID" 2>/dev/null
  echo "  service and token shim stopped. Log: $LOG"
}
trap cleanup EXIT

say "1. Postgres, a build of the service, and a shim that hands it YOUR Azure token for the APIM token request"
docker compose -f demo/docker-compose.yml up -d db >/dev/null 2>&1
for _ in $(seq 1 30); do docker exec amp-demo-db-1 pg_isready -U postgres -d marketplace >/dev/null 2>&1 && break; sleep 1; done
docker exec amp-demo-db-1 psql -U postgres -d marketplace -qc "drop schema public cascade; create schema public;" >/dev/null 2>&1
[ -f build/libs/apim-marketplace.jar ] || ./gradlew bootJar -x test -q
python3 - "$SHIM_PORT" <<'PY' &
import http.server, json, subprocess, sys
class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length') or 0))
        t = subprocess.check_output(['az','account','get-access-token','--resource','https://management.azure.com','--query','accessToken','-o','tsv']).decode().strip()
        b = json.dumps({'access_token': t, 'token_type': 'Bearer', 'expires_in': 3000}).encode()
        self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
    def log_message(self,*a): pass
http.server.HTTPServer(('127.0.0.1', int(sys.argv[1])), H).serve_forever()
PY
SHIM_PID=$!; sleep 1

say "2. Start the service against the REAL Graph and the REAL Azure Resource Manager"
SERVER_PORT=$PORT POSTGRES_HOST=localhost POSTGRES_PORT=5433 POSTGRES_DATABASE=marketplace POSTGRES_USER=postgres POSTGRES_PASS=postgres \
JWT_SECRET=real-check-only-jwt-secret-0123456789abcdef APPLICATION_CREDENTIALS=entra \
ENTRA_TENANT_ID=$TENANT APIM_SUBSCRIPTION_ID=$SUB APIM_RESOURCE_GROUP=$RG APIM_SERVICE_NAME=$SVC \
APIM_CLIENT_ID=shim APIM_CLIENT_SECRET=shim APIM_LOGIN_BASE_URL=http://127.0.0.1:$SHIM_PORT \
APIM_PRODUCT_MAP="hearing-results:$PRODUCT" \
  "$JAVA" -jar build/libs/apim-marketplace.jar > "$LOG" 2>&1 &
SVC_PID=$!
for _ in $(seq 1 60); do c=$(curl -s -o /dev/null -w '%{http_code}' -m 2 -X POST "$API/api/login" || true); [ "$c" != "000" ] && break; sleep 2; done
echo "  up on $API. Graph, login and Azure Resource Manager addresses are left at Microsoft's real defaults"

say "3. A throwaway account on the local service"
OUT=$(curl -s -X POST "$API/api/register" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Real\",\"lastName\":\"Check\",\"email\":\"real.check+$RANDOM@example.com\",\"organisation\":\"Check\",\"role\":\"consumer\",\"password\":\"Real-check-1\"}")
TOKEN=$(echo "$OUT" | json "d['token']") || { echo "  register failed"; exit 1; }
echo "  ok"

say "4. Register an application: real Graph creates it, its service principal and a secret"
OUT=$(curl -s -w '\n%{http_code}' -X POST "$API/api/applications" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$NAME\",\"environment\":\"sandbox\"}")
CODE=$(echo "$OUT" | tail -1); BODY=$(echo "$OUT" | sed '$d')
echo "  HTTP $CODE"
[ "$CODE" = "201" ] || { echo "  $BODY" | cut -c1-300; grep -E "ERROR" "$LOG" | tail -5 | cut -c1-400; exit 1; }
APP_ID=$(echo "$BODY" | json "d['application']['id']"); CLIENT_ID=$(echo "$BODY" | json "d['application']['clientId']")
echo "  Client ID (real Entra):     $CLIENT_ID"
echo "  Client Secret (real Entra): $(echo "$BODY" | json "d['apiKey']" | mask)"

say "5. Connect an API: real APIM creates a subscription on product '$PRODUCT'"
OUT=$(curl -s -w '\n%{http_code}' -X POST "$API/api/applications/$APP_ID/connected-apis" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":"hearing-results","name":"Hearing Results API"}')
echo "  HTTP $(echo "$OUT" | tail -1)"
[ "$(echo "$OUT" | tail -1)" = "201" ] || { grep -E "ERROR" "$LOG" | tail -3 | cut -c1-400; exit 1; }
KEY=$(curl -s "$API/api/applications/$APP_ID" -H "Authorization: Bearer $TOKEN" | json "d['apiSubscriptions'][0]['subscriptionKey']")
echo "  Subscription Key (real APIM): $(echo "$KEY" | mask)"
SUBNAME=$(docker exec amp-demo-db-1 psql -U postgres -d marketplace -At -c "select subscription_name from application_api_key order by id desc limit 1")
echo "  APIM subscription: $SUBNAME"

say "6. Add a second secret, then revoke the first (real addPassword and removePassword)"
echo "  new secret: HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/applications/$APP_ID/api-keys" -H "Authorization: Bearer $TOKEN")"
FIRST=$(curl -s "$API/api/applications/$APP_ID" -H "Authorization: Bearer $TOKEN" | json "sorted(d['apiKeys'], key=lambda k: k['createdAt'])[0]['id']")
echo "  revoke: HTTP $(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API/api/applications/$APP_ID/api-keys/$FIRST" -H "Authorization: Bearer $TOKEN")"
echo "  Graph accepted the revoke: $(grep -c 'Removed client secret' "$LOG") time(s) in the log"
echo "  (Graph's reads lag its writes, so counting secrets straight afterwards is unreliable and is not attempted)"

say "7. An API with no APIM Product is refused before anything is created"
echo "  HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/applications/$APP_ID/connected-apis" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"id":"no-such-api","name":"None"}')"

say "8. Disconnect and delete: both real resources must go"
echo "  disconnect: HTTP $(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API/api/applications/$APP_ID/connected-apis/hearing-results" -H "Authorization: Bearer $TOKEN")"
echo "  delete application: HTTP $(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API/api/applications/$APP_ID" -H "Authorization: Bearer $TOKEN")"
APP_ID=""
GT=$(graph_token)
for i in $(seq 1 30); do
  GC=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $GT" "https://graph.microsoft.com/v1.0/applications(appId='$CLIENT_ID')")
  [ "$GC" = 404 ] && { echo "  Graph: the application is gone (404) after ~$((i*2))s"; break; }; sleep 2
done
[ "$GC" = 404 ] || echo "  Graph: still answering after 60s (HTTP $GC) - re-check later; it should disappear"
for i in $(seq 1 60); do
  az rest --method get --url "$ARM/subscriptions/$SUBNAME?api-version=2022-08-01" -o none 2>&1 | grep -qi "not found" && { echo "  APIM: the subscription is gone (404) after ~$((i*2))s"; GONE=1; break; }; sleep 2
done
[ "${GONE:-}" = 1 ] || { echo "  APIM: the management API still lists it after 2 minutes. It lags for minutes after a delete"
  echo "        (the service's own log says it was deleted). Re-check shortly:"
  echo "        az rest --method get --url \"$ARM/subscriptions/$SUBNAME?api-version=2022-08-01\""; }
echo; echo "  delete calls the service made:"; grep -E "Deleted APIM subscription|Deleted Entra application" "$LOG" | sed -E 's/^[0-9T:.-]+ +INFO +\[[^]]*\] +/    /' | cut -c1-160
