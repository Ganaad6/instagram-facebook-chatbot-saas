#!/usr/bin/env bash
# Builds and starts the full production stack (Postgres, app, Caddy HTTPS) on this machine
# with throwaway secrets, checks it end to end over https://localhost, then tears it down.
# Needs Docker with Compose v2 and free ports 80/443.  Usage: scripts/smoke-test.sh [--keep]
set -euo pipefail
cd "$(dirname "$0")/.."

PROJECT=chatbot_smoke
# Kept in the project dir, not /tmp: snap-packaged Docker has a private /tmp and can't see it.
# *.env is gitignored.
ENV_FILE=$(mktemp -p "$PWD" smoke-XXXXXX.env)
COOKIES=$(mktemp)
KEEP=${1:-}
BASE=https://localhost

cleanup() {
  if [ "$KEEP" = "--keep" ]; then
    # Compose needs the env file to stop the stack later, so it stays
    echo "Still running at $BASE. Stop it with:"
    echo "  docker compose -p $PROJECT --env-file $ENV_FILE down -v && rm $ENV_FILE"
  else
    docker compose -p "$PROJECT" --env-file "$ENV_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
    rm -f "$ENV_FILE"
  fi
  rm -f "$COOKIES"
}
trap cleanup EXIT

rand() { head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c "$1"; }
cat > "$ENV_FILE" <<ENV
DOMAIN=localhost
BASE_URL=$BASE
SPRING_PROFILES_ACTIVE=prod
POSTGRES_SUPERUSER_PASSWORD=$(rand 24)
APP_DB_PASSWORD=$(rand 24)
ENCRYPTION_SECRET_KEY=$(rand 32)
OAUTH_STATE_SECRET=$(rand 32)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=$(rand 24)
CORS_ALLOWED_ORIGINS=$BASE
META_APP_ID=smoke-app-id
META_APP_SECRET=smoke-app-secret
WEBHOOK_VERIFY_TOKEN=$(rand 24)
QPAY_API_URL=https://merchant.qpay.mn/v2
LEGAL_OPERATOR_NAME=Smoke Test LLC
LEGAL_CONTACT_EMAIL=privacy@smoke.test
ENV

step() { printf '\n==> %s\n' "$*"; }
fail() { echo "FAIL: $*" >&2; docker compose -p "$PROJECT" --env-file "$ENV_FILE" logs --tail=80 app >&2 || true; exit 1; }
expect() { # expect <status> <description> <curl args...>
  local want=$1 what=$2; shift 2
  local got
  got=$(curl -sk -o /dev/null -w '%{http_code}' "$@") || true
  [ "$got" = "$want" ] || fail "$what: expected $want, got $got"
  echo "ok  $what ($got)"
}

step "Building and starting the stack"
docker compose -p "$PROJECT" --env-file "$ENV_FILE" up -d --build

step "Waiting for the app to be healthy behind Caddy"
for i in $(seq 1 90); do
  if curl -sk "$BASE/actuator/health" | grep -q '"UP"'; then break; fi
  [ "$i" = 90 ] && fail "app did not become healthy"
  sleep 2
done
echo "ok  healthy"

step "Pages and security"
expect 200 "dashboard login page" "$BASE/login"
curl -skI "$BASE/login" | grep -qi "^content-security-policy:.*frame-ancestors 'none'" || fail "missing CSP header"
curl -skI "$BASE/login" | grep -qi "^strict-transport-security:" || fail "missing HSTS header"
echo "ok  CSP and HSTS headers"
expect 308 "http redirects to https" -o /dev/null "http://localhost/login"
expect 401 "API needs sign-in" "$BASE/api/auth/me"
expect 200 "privacy policy page" "$BASE/privacy"
curl -sk "$BASE/api/public/legal" | grep -q 'privacy@smoke.test' || fail "legal info missing contact email"
echo "ok  legal info"
expect 400 "unsigned Meta data-deletion callback is rejected" -d 'signed_request=forged.payload' "$BASE/webhook/meta/data-deletion"
expect 401 "admin needs credentials" "$BASE/api/admin/businesses"

step "Sign up a shop through the dashboard API"
curl -sk -c "$COOKIES" -b "$COOKIES" -o /dev/null "$BASE/api/auth/csrf"
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIES")
[ -n "$XSRF" ] || fail "no XSRF-TOKEN cookie"
grep -q "XSRF-TOKEN" "$COOKIES" && awk '$6 == "XSRF-TOKEN" && $4 != "TRUE" { exit 1 }' "$COOKIES" || fail "CSRF cookie is not Secure"
expect 201 "signup" -c "$COOKIES" -b "$COOKIES" -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"businessName":"Smoke shop","name":"Owner","email":"owner@smoke.test","password":"smoke-test-password"}' "$BASE/api/auth/signup"
awk '$6 == "SESSION" && $4 != "TRUE" { exit 1 }' "$COOKIES" || fail "session cookie is not Secure"
ME=$(curl -sk -b "$COOKIES" "$BASE/api/auth/me")
BID=$(echo "$ME" | sed -n 's/.*"business":{"id":\([0-9]*\).*/\1/p')
[ -n "$BID" ] || fail "could not read business id from /me: $ME"
echo "ok  signed in to business $BID"

step "Catalog with a photo"
expect 403 "write without CSRF header is refused" -b "$COOKIES" -H 'Content-Type: application/json' \
  -d '{"name":"Flowers"}' "$BASE/api/businesses/$BID/categories"
expect 201 "create category" -b "$COOKIES" -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"name":"Flowers"}' "$BASE/api/businesses/$BID/categories"
PNG=$(mktemp --suffix=.png)
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\rIDATx\xdac\xfc\xcf\xc0P\x0f\x00\x04\x85\x01\x80\x84\xa9\x8c!\x00\x00\x00\x00IEND\xaeB`\x82' > "$PNG"
UPLOAD=$(curl -sk -b "$COOKIES" -H "X-XSRF-TOKEN: $XSRF" -F "file=@$PNG;type=image/png" "$BASE/api/businesses/$BID/media")
rm -f "$PNG"
MEDIA_URL=$(echo "$UPLOAD" | sed -n 's/.*"url":"\([^"]*\)".*/\1/p')
[ -n "$MEDIA_URL" ] || fail "upload failed: $UPLOAD"
expect 200 "photo is public (Meta fetches it)" "$MEDIA_URL"

step "Admin API and Meta webhook handshake"
ADMIN_PASSWORD=$(sed -n 's/^ADMIN_PASSWORD=//p' "$ENV_FILE")
VERIFY=$(sed -n 's/^WEBHOOK_VERIFY_TOKEN=//p' "$ENV_FILE")
expect 200 "admin lists businesses" -u "admin:$ADMIN_PASSWORD" "$BASE/api/admin/businesses"
CHALLENGE=$(curl -sk "$BASE/webhook?hub.mode=subscribe&hub.verify_token=$VERIFY&hub.challenge=smoke123")
[ "$CHALLENGE" = "smoke123" ] || fail "webhook verification returned '$CHALLENGE'"
echo "ok  webhook verification"
expect 403 "unsigned webhook is rejected" -H 'Content-Type: application/json' -d '{"object":"page","entry":[]}' "$BASE/webhook"

step "Restart keeps the session (stored in Postgres)"
docker compose -p "$PROJECT" --env-file "$ENV_FILE" restart app >/dev/null
for i in $(seq 1 60); do curl -sk "$BASE/actuator/health" | grep -q '"UP"' && break; sleep 2; done
expect 200 "still signed in after restart" -b "$COOKIES" "$BASE/api/auth/me"

printf '\nSMOKE TEST PASSED\n'
