#!/usr/bin/env bash
# The organisation approval flow end to end, offline: the real service and a real Postgres, with a stand-in that
# signs tokens the way Entra would. Proves the wiring (token checks, domain rule, held/active, admin approval,
# suspension) without a Microsoft account. The real thing is run-demo.sh.
#
#   ./local-check.sh        runs the scenario, prints each step, exits non-zero if any step is wrong
set -uo pipefail
cd "$(dirname "$0")"
# The service is built for Java 25; the java on the PATH may be older.
JAVA=$(/usr/libexec/java_home -v 25 2>/dev/null)/bin/java
[ -x "$JAVA" ] || JAVA=java
ROOT=$(cd ../.. && pwd)
SERVICE_PORT=8084
ENTRA_PORT=8095
DB=marketplace_orgcheck
API=http://localhost:$SERVICE_PORT
FAKE=http://localhost:$ENTRA_PORT
AUDIENCE=demo-client
failures=0
pids=()

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
cleanup() { for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null; done; }
trap cleanup EXIT

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
token() { curl -s "$FAKE/token?aud=$AUDIENCE&$1" | json "d['token']"; }
call() { # method path token [body] -> prints "<status> <body>"
  local out; out=$(curl -s -w '\n%{http_code}' -X "$1" "$API$2" -H "Authorization: Bearer $3" -H 'Content-Type: application/json' ${4:+-d "$4"})
  echo "$(echo "$out" | tail -1) $(echo "$out" | sed '$d')"
}
expect() { # description, actual status, wanted status
  if [ "$2" = "$3" ]; then printf '   ok   %s (HTTP %s)\n' "$1" "$2"; else printf '   WRONG %s: wanted HTTP %s, got %s\n' "$1" "$3" "$2"; failures=$((failures+1)); fi
}
field() { echo "$1" | cut -d' ' -f2- | json "d$2"; }
status() { echo "$1" | cut -d' ' -f1; }

say "Starting Postgres, the token stand-in and the service"
docker compose up -d db >/dev/null 2>&1 || { echo "Docker is not running"; exit 1; }
until docker exec amp-orgdemo-db pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
docker exec amp-orgdemo-db psql -U postgres -qc "drop database if exists $DB" -c "create database $DB" >/dev/null
node fake-entra.js $ENTRA_PORT >/dev/null 2>&1 & pids+=($!)
JAR=$(ls "$ROOT"/build/libs/*.jar 2>/dev/null | grep -v plain | head -1)
[ -n "$JAR" ] || (cd "$ROOT" && ./gradlew bootJar -x test -q >/dev/null 2>&1; JAR=$(ls "$ROOT"/build/libs/*.jar | grep -v plain | head -1))
JAR=$(ls "$ROOT"/build/libs/*.jar | grep -v plain | head -1)
POSTGRES_HOST=localhost POSTGRES_PORT=5435 POSTGRES_DATABASE=$DB POSTGRES_USER=postgres POSTGRES_PASS=postgres \
SERVER_PORT=$SERVICE_PORT JWT_SECRET=$(openssl rand -hex 32) \
ORGANISATION_APPROVAL=on ADMIN_EMAILS=admin@approved.example \
ENTRA_TOKEN_ISSUER=http://localhost:$ENTRA_PORT/issuer ENTRA_TOKEN_JWKS_URI=$FAKE/keys ENTRA_TOKEN_AUDIENCES=$AUDIENCE \
  "$JAVA" -jar "$JAR" >/tmp/orgcheck-service.log 2>&1 & pids+=($!)
for _ in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' $API/actuator/health 2>/dev/null)" = 200 ] && break; sleep 2; done

ADMIN=$(token "email=admin@approved.example&name=Ada+Admin&oid=00000000-0000-0000-0000-0000000000a1")
DEE=$(token "email=dee@approved.example&name=Dee+Mo&oid=00000000-0000-0000-0000-0000000000d1")
BOB=$(token "email=bob@unknown.example&name=Bob+Lee&oid=00000000-0000-0000-0000-0000000000b1")

say "1. Nothing is approved yet: everyone who signs in is held"
r=$(call GET /api/entra/me "$DEE"); expect "Dee signs in" "$(status "$r")" 200; echo "      status: $(field "$r" "['status']")"
r=$(call GET /api/entra/applications "$DEE"); expect "Dee is turned away" "$(status "$r")" 403; echo "      $(field "$r" "['error']")"

say "2. Only an admin may use the admin endpoints (anyone else gets 404)"
r=$(call GET /api/admin/users "$DEE"); expect "Dee is not an admin" "$(status "$r")" 404
r=$(call POST /api/admin/organisations "$ADMIN" '{"name":"Example Council"}'); expect "Ada creates an organisation" "$(status "$r")" 201
ORG=$(field "$r" "['id']")
r=$(call POST /api/admin/organisations/$ORG/domains "$ADMIN" '{"domain":"gmail.com"}'); expect "a shared mail domain is refused" "$(status "$r")" 400
r=$(call POST /api/admin/organisations/$ORG/domains "$ADMIN" '{"domain":"Approved.Example"}'); expect "Ada lists approved.example" "$(status "$r")" 201

say "3. Listing the domain made everyone held on it active, and the check is made on every call"
r=$(call GET /api/entra/me "$DEE"); echo "      Dee: $(field "$r" "['status']") in $(field "$r" "['organisation']")"
[ "$(field "$r" "['status']")" = ACTIVE ] && echo "   ok   Dee is active" || { echo "   WRONG Dee should be active"; failures=$((failures+1)); }
r=$(call GET /api/entra/applications "$DEE"); expect "Dee can now use the marketplace" "$(status "$r")" 200

say "4. A person on an unlisted domain stays held until an admin approves them"
r=$(call GET /api/entra/applications "$BOB"); expect "Bob is turned away" "$(status "$r")" 403
r=$(call GET "/api/admin/users?status=HELD" "$ADMIN"); echo "      waiting: $(field "$r" "['users'][0]['email']")"
BOBID=$(field "$r" "['users'][0]['id']")
r=$(call POST /api/admin/users/$BOBID/approve "$ADMIN" "{\"organisationId\":$ORG}"); expect "Ada approves Bob" "$(status "$r")" 200
r=$(call GET /api/entra/applications "$BOB"); expect "Bob can now use the marketplace" "$(status "$r")" 200

say "5. A leaver is suspended and refused on the next call"
DEEID=$(call GET "/api/admin/users?status=ACTIVE" "$ADMIN" | cut -d' ' -f2- | json "[u['id'] for u in d['users'] if u['email']=='dee@approved.example'][0]")
r=$(call POST /api/admin/users/$DEEID/suspend "$ADMIN"); expect "Ada suspends Dee" "$(status "$r")" 200
r=$(call GET /api/entra/applications "$DEE"); expect "Dee is refused" "$(status "$r")" 403; echo "      $(field "$r" "['error']")"

say "6. The token is checked every time"
expect "no token" "$(status "$(call GET /api/entra/me '')")" 401
expect "garbage token" "$(status "$(call GET /api/entra/me 'not.a.token')")" 401
expect "token for another client" "$(status "$(call GET /api/entra/me "$(curl -s "$FAKE/token?aud=someone-else&email=dee@approved.example&oid=x1" | json "d['token']")")")" 401
expect "token for another tenant" "$(status "$(call GET /api/entra/me "$(token "tid=11111111-1111-1111-1111-111111111111&email=dee@approved.example&oid=x2")")")" 401
expect "expired token" "$(status "$(call GET /api/entra/me "$(token "ttl=-60&email=dee@approved.example&oid=x3")")")" 401
expect "a sign-in with no email" "$(status "$(call GET /api/entra/me "$(token "oid=x4")")")" 403

echo
if [ "$failures" = 0 ]; then say "All steps behaved as the design says"; else say "$failures step(s) were wrong - see /tmp/orgcheck-service.log"; exit 1; fi
