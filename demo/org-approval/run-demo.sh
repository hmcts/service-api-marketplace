#!/usr/bin/env bash
# The organisation approval flow against the REAL sandbox Entra tenant.
#
# Starts Postgres and the service (ORGANISATION_APPROVAL=on) configured to accept ID tokens issued by the sandbox
# External ID tenant to the prototype's sign-in app, then tells you how to run the prototype Kit against it. You
# sign in yourself in the browser: this script never creates an account or types a password.
#
#   ADMIN_EMAILS=you@hmcts.net ./run-demo.sh      who may keep the list and suspend people (optional for the demo itself)
#   APPROVED_DOMAINS='HMCTS=hmcts.net' ...         the pre-approved domains (that is the default)
#   ./run-demo.sh --reset                          also throws the demo data away first
#   ./run-demo.sh --down                           stops the service and Postgres
set -uo pipefail
cd "$(dirname "$0")"
# The service is built for Java 25; the java on the PATH may be older.
JAVA=$(/usr/libexec/java_home -v 25 2>/dev/null)/bin/java
[ -x "$JAVA" ] || JAVA=java
ROOT=$(cd ../.. && pwd)
PORT=8083
DB=marketplace_orgdemo
LOG=/tmp/orgdemo-service.log
PIDFILE=/tmp/orgdemo-service.pid
KIT_ENV=${KIT_ENV:-$HOME/IdeaProjects/hmcts-api-marketplace/prototype-kit/.env}

stop_service() { [ -f "$PIDFILE" ] && kill "$(cat "$PIDFILE")" 2>/dev/null; rm -f "$PIDFILE"; }

if [ "${1:-}" = "--down" ]; then
  stop_service; docker compose down >/dev/null 2>&1; echo "stopped (the data volume is kept; --reset clears it)"; exit 0
fi

ADMIN_EMAILS=${ADMIN_EMAILS:-zaheer.iqbal@hmcts.net}
APPROVED_DOMAINS=${APPROVED_DOMAINS:-HMCTS=hmcts.net}
[ -f "$KIT_ENV" ] || { echo "Cannot find the prototype's .env at $KIT_ENV (set KIT_ENV)"; exit 1; }
AUDIENCE=$(grep '^ENTRA_CLIENT_ID=' "$KIT_ENV" | cut -d= -f2)
[ -n "$AUDIENCE" ] || { echo "ENTRA_CLIENT_ID is not in $KIT_ENV"; exit 1; }

stop_service
docker compose up -d db >/dev/null 2>&1 || { echo "Docker is not running"; exit 1; }
until docker exec amp-orgdemo-db pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
[ "${1:-}" = "--reset" ] && docker exec amp-orgdemo-db psql -U postgres -qc "drop database if exists $DB" >/dev/null
docker exec amp-orgdemo-db psql -U postgres -tc "select 1 from pg_database where datname='$DB'" | grep -q 1 \
  || docker exec amp-orgdemo-db psql -U postgres -qc "create database $DB" >/dev/null

# Always rebuild: gradle skips it when nothing changed, and a stale jar would not match the code.
(cd "$ROOT" && ./gradlew bootJar -x test -q) || { echo "The build failed"; exit 1; }
JAR=$(ls "$ROOT"/build/libs/*.jar | grep -v plain | head -1)

POSTGRES_HOST=localhost POSTGRES_PORT=5435 POSTGRES_DATABASE=$DB POSTGRES_USER=postgres POSTGRES_PASS=postgres \
SERVER_PORT=$PORT JWT_SECRET=$(openssl rand -hex 32) \
ORGANISATION_APPROVAL=on ADMIN_EMAILS="$ADMIN_EMAILS" APPROVED_DOMAINS="$APPROVED_DOMAINS" ENTRA_TOKEN_AUDIENCES="$AUDIENCE" \
  nohup "$JAVA" -jar "$JAR" >"$LOG" 2>&1 &
echo $! > "$PIDFILE"
for _ in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$PORT/health 2>/dev/null)" = 200 ] && break; sleep 2; done
[ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$PORT/health)" = 200 ] || { echo "Service did not start - see $LOG"; exit 1; }

cat <<MSG

The marketplace service is up on http://localhost:$PORT
  validates ID tokens from the sandbox tenant (hmctsextsbox) issued to client $AUDIENCE
  pre-approved domains: $APPROVED_DOMAINS
  admins (keep the list, suspend people): $ADMIN_EMAILS     log: $LOG

Now run the prototype Kit against it (in a second terminal):

  cd $(cd "$ROOT/../hmcts-api-marketplace" && pwd) && git checkout demo/org-approval-signin
  BACKEND_URL=http://localhost:$PORT DATABASE_URL= npm run kit

Then in your browser:
  1. Open http://localhost:3100/auth/entra?prompt=login and sign in (or create an account) yourself.
  2. On a pre-approved domain the "Organisation check" panel says ACTIVE straight away: nobody approved you.
  3. On any other domain it says 403 DOMAIN_NOT_APPROVED, and nothing about you is saved.
  4. As an admin, the "Admin" page lists the approved domains. Add yours (or remove it) and the next call changes.
  5. Suspend a person there and their next call is refused; reinstate them and it works again.
MSG
