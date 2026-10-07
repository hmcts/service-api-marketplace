#!/usr/bin/env bash
# Runs the Entra Client ID/Secret and APIM Subscription Key flow end to end, on your machine, offline.
#
#   ./demo/run-demo.sh            build, start the stack, run the flow, leave the stack running
#   ./demo/run-demo.sh --flow     run the flow again against a stack that is already up
#   ./demo/run-demo.sh --up       build and start the stack only (e.g. to point the frontend at it)
#   ./demo/run-demo.sh --down     stop the stack and delete its data
#
# Needs: docker, curl, python3. See demo/README.md for what is real and what is a stand-in.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f demo/docker-compose.yml"
API=http://localhost:8080
STUB=http://localhost:8099

bold() { printf '\n\033[1m%s\033[0m\n' "$*"; }
note() { printf '   \033[2m%s\033[0m\n' "$*"; }

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }

wait_for_api() {
  printf 'Waiting for the API'
  for _ in $(seq 1 60); do
    # Any answer at all means it is up; a bare login with no body is refused, which is fine.
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 3 -X POST "$API/api/login" || true)
    if [ "$code" != "000" ] && [ "$code" != "" ]; then printf ' - up\n'; return 0; fi
    printf '.'; sleep 2
  done
  printf '\nThe API did not come up. Its log:\n'; $COMPOSE logs --tail 30 api; exit 1
}

up() {
  if [ ! -f build/libs/apim-marketplace.jar ]; then
    bold "Building the service (./gradlew bootJar)"
    ./gradlew bootJar -x test -q
  fi
  bold "Starting Postgres, the Entra/APIM stand-in and the service"
  $COMPOSE up -d
  wait_for_api
}

call() { # method path [token] [body]
  local method=$1 path=$2 token=${3:-} body=${4:-}
  local args=(-s -X "$method" "$API$path" -H 'Content-Type: application/json' -w '\n%{http_code}')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}"
}

flow() {
  curl -s -X DELETE "$STUB/__admin/requests" >/dev/null   # so the journal at the end is just this run

  bold "1. Create an account (and their user in Entra) and sign in"
  local email="demo+$RANDOM@example.com"
  local out token
  out=$(call POST /api/register "" "{\"firstName\":\"Dee\",\"lastName\":\"Mo\",\"email\":\"$email\",\"organisation\":\"Demo Org\",\"role\":\"consumer\",\"password\":\"Demo-password-1\"}")
  token=$(echo "$out" | sed '$d' | json "d['token']")
  note "registered $email (HTTP $(echo "$out" | tail -1)); the service signed a bearer token for them"
  note "and created their user in Entra through Graph, keeping its object id:"
  docker exec amp-demo-db-1 psql -U postgres -d marketplace -At -c \
    "select '      '||email||'  ->  Entra object id '||coalesce(entra_object_id,'(none)') from marketplace_user where email='$email'"

  bold "2. Register an application: Entra gives it a Client ID and Client Secret"
  out=$(call POST /api/applications "$token" '{"name":"Demo app","environment":"sandbox","description":"Created by run-demo.sh"}')
  local created app_id
  created=$(echo "$out" | sed '$d')
  app_id=$(echo "$created" | json "d['application']['id']")
  echo "$created" | json "'   Client ID:     '+d['application']['clientId']"
  echo "$created" | json "'   Client Secret: '+d['apiKey']+'   (shown this once; only a hash is kept)'"
  note "HTTP $(echo "$out" | tail -1)"

  bold "3. Connect it to an API: APIM gives it a Subscription Key for that API"
  call POST "/api/applications/$app_id/connected-apis" "$token" '{"id":"hearing-results","name":"Hearing Results API"}' | tail -1 | sed 's/^/   connect: HTTP /'; echo; echo
  call GET "/api/applications/$app_id" "$token" | sed '$d' | json "'\n'.join('   Subscription Key for %s: %s' % (s['apiId'], s['subscriptionKey']) for s in d['apiSubscriptions'])"

  bold "4. Connect an API that has no APIM Product behind it: turned away, nothing is created"
  out=$(call POST "/api/applications/$app_id/connected-apis" "$token" '{"id":"fees-and-financial-transactions","name":"Fees API"}')
  note "HTTP $(echo "$out" | tail -1): $(echo "$out" | sed '$d' | json "d.get('error') or d.get('message')")"

  bold "5. Add a second client secret, then revoke the first (revoked in Entra too)"
  out=$(call POST "/api/applications/$app_id/api-keys" "$token")
  echo "$out" | sed '$d' | json "'   second Client Secret: '+d['apiKey']"
  local first
  first=$(call GET "/api/applications/$app_id" "$token" | sed '$d' | json "sorted(d['apiKeys'], key=lambda k: k['createdAt'])[0]['id']")
  call DELETE "/api/applications/$app_id/api-keys/$first" "$token" | tail -1 | sed 's/^/   revoke first secret: HTTP /'; echo

  bold "6. Disconnect the API (its Subscription Key is deleted in APIM), then delete the application (and the Entra app)"
  call DELETE "/api/applications/$app_id/connected-apis/hearing-results" "$token" | tail -1 | sed 's/^/   disconnect: HTTP /'; echo
  call DELETE "/api/applications/$app_id" "$token" | tail -1 | sed 's/^/   delete application: HTTP /'; echo

  bold "What the service actually asked Entra, Graph and APIM for (from the stand-in's request log)"
  curl -s "$STUB/__admin/requests" | python3 -c "
import json, sys
reqs = json.load(sys.stdin)['requests']
for r in sorted(reqs, key=lambda r: r['request']['loggedDate']):
    q = r['request']
    url = q['url'].split('?')[0]
    print('   %-6s %-90s -> %s' % (q['method'], url[:90], r['response']['status']))
print('   (%d requests; the service never contacted Microsoft)' % len(reqs))"
}

case "${1:-}" in
  --down) bold "Stopping the stack"; $COMPOSE down -v ;;
  --flow) wait_for_api; flow ;;
  --up)   up ;;
  "")     up; flow
          bold "The stack is still running"
          note "API: $API   stand-in's request log: $STUB/__admin/requests   stop it with: ./demo/run-demo.sh --down" ;;
  *)      echo "usage: $0 [--up|--flow|--down]"; exit 2 ;;
esac
