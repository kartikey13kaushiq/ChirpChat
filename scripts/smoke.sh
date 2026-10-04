#!/usr/bin/env bash
# End-to-end check through the gateway: register two users, create a private channel, add a member
# (chat-service resolves the username via auth-service), post with a mention, read it back.
set -euo pipefail
BASE="${1:-http://localhost:8080}"
SUFFIX="$(date +%s)$RANDOM"
fail() { echo "FAIL: $*" >&2; exit 1; }
json() { curl -fsS -H 'Content-Type: application/json' "$@"; }

for u in alice bob; do
  json -X POST "$BASE/auth/register" -d "{\"username\":\"$u$SUFFIX\",\"displayName\":\"$u\",\"password\":\"correct-horse-battery\"}" >/dev/null
done
ALICE=$(json -X POST "$BASE/auth/token" -d "{\"username\":\"alice$SUFFIX\",\"password\":\"correct-horse-battery\"}" | jq -r .access_token)
BOB=$(json -X POST "$BASE/auth/token" -d "{\"username\":\"bob$SUFFIX\",\"password\":\"correct-horse-battery\"}" | jq -r .access_token)
[ -n "$ALICE" ] && [ "$ALICE" != null ] || fail "no access token"

curl -fsS "$BASE/.well-known/jwks.json" | jq -e '.keys[0].kty == "RSA" and (.keys[0] | has("d") | not)' >/dev/null \
  || fail "JWKS should publish only a public RSA key"

CHANNEL=$(json -X POST "$BASE/api/channels" -H "Authorization: Bearer $ALICE" \
  -d "{\"name\":\"launch-$SUFFIX\",\"topic\":\"Go-live\",\"isPrivate\":true}" | jq -r .id)
status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/channels/$CHANNEL" -H "Authorization: Bearer $BOB")
[ "$status" = 404 ] || fail "outsider saw a private channel ($status)"

json -X POST "$BASE/api/channels/$CHANNEL/members" -H "Authorization: Bearer $ALICE" \
  -d "{\"username\":\"bob$SUFFIX\"}" | jq -e ".username == \"bob$SUFFIX\"" >/dev/null || fail "add member via directory"

json -X POST "$BASE/api/channels/$CHANNEL/messages" -H "Authorization: Bearer $ALICE" \
  -d "{\"body\":\"@bob$SUFFIX ship it\"}" | jq -e ".mentions == [\"bob$SUFFIX\"]" >/dev/null || fail "mention"

json "$BASE/api/channels" -H "Authorization: Bearer $BOB" | jq -e '.[0].unreadCount == 1' >/dev/null || fail "unread count"
json "$BASE/api/channels/$CHANNEL/messages" -H "Authorization: Bearer $BOB" \
  | jq -e '.messages[0].body | endswith("ship it")' >/dev/null || fail "history"

status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/channels" -H "Authorization: Bearer not-a-token")
[ "$status" = 401 ] || fail "bad token accepted ($status)"
echo "smoke test passed"
