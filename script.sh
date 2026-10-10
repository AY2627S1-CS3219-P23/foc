#!/usr/bin/env bash
set -euo pipefail
OWNER_SETUP_TOKEN=local-dev-owner-setup-token
USERS=http://localhost:8087
SUPPLIERS=http://localhost:8086
MAILPIT=http://localhost:8025
PASS='Password123'            # 10-50 chars, upper+lower+digit

j() { curl -s -w '\n%{http_code}\n' -H 'Content-Type: application/json' "$@"; }

### 1. Owner via the setup endpoint (P1.6) — 201; rerun-safe (400 if it exists)
j -X POST "$USERS/auth/setup-owner" \
  -H "X-Setup-Token: ${OWNER_SETUP_TOKEN:?export it from .env first}" \
  -d '{"email":"e0000001@u.nus.edu","username":"demo_owner","password":"'"$PASS"'"}'

### 2. Wrong setup token → 403 (controlled + secured)
j -X POST "$USERS/auth/setup-owner" -H "X-Setup-Token: wrong" \
  -d '{"email":"e0000002@u.nus.edu","username":"x","password":"'"$PASS"'"}'

### 3. Sign-up with OTP (P1.3, F1.1) — 202, then verify → 201
j -X POST "$USERS/auth/signup" \
  -d '{"email":"e0000003@u.nus.edu","username":"demo_user","password":"'"$PASS"'"}'
sleep 1
CODE=$(curl -s "$MAILPIT/api/v1/messages" | grep -oE '[0-9]{6}' | head -1)
echo "OTP from Mailpit: $CODE"
j -X POST "$USERS/auth/signup/verify" \
  -d '{"email":"e0000003@u.nus.edu","code":"'"$CODE"'"}'

### 4. Bad password policy → 400 with exact reasons (F1.1.5-6)
j -X POST "$USERS/auth/signup" \
  -d '{"email":"e0000004@u.nus.edu","username":"demo_weak","password":"short"}'

### 5. Login → JWTs (P1.3)
token() { curl -s -H 'Content-Type: application/json' -X POST "$USERS/auth/login" \
  -d '{"usernameOrEmail":"'"$1"'","password":"'"$PASS"'"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'; }
OWNER_T=$(token demo_owner)
USER_T=$(token demo_user)
echo "decoded payload:"; echo "$USER_T" | cut -d. -f2 | base64 -d 2>/dev/null; echo

### 6. Owner promotes the first admin (P1.6) — needs the user id from step 3's 201
UID3=$(curl -s -H "Authorization: Bearer $USER_T" "$USERS/users/me" | sed -E 's/.*"id":([0-9]+).*/\1/')
j -X PATCH "$USERS/users/$UID3" -H "Authorization: Bearer $OWNER_T" -d '{"role":"ADMIN"}'
ADMIN_T=$(token demo_user)     # fresh token now carries role: ADMIN

### 7. RBAC on user-service (P1.3): admin list — 200 as admin, 403 as plain user, 401 bare
curl -s -o /dev/null -w 'admin list as ADMIN: %{http_code}\n' -H "Authorization: Bearer $ADMIN_T" "$USERS/users"
curl -s -o /dev/null -w 'admin list, no token: %{http_code}\n' "$USERS/users"

### 8. Protected fields (P1.5): role in the signup body is ignored
j -X POST "$USERS/auth/signup" \
  -d '{"email":"e0000005@u.nus.edu","username":"demo_sneaky","password":"'"$PASS"'","role":"ADMIN"}'
# → 202; after verify the account is role USER

### 9. Supplier reads (P2.2, after #143: Bearer required)
curl -s -o /dev/null -w 'list page 0:        %{http_code}\n' -H "Authorization: Bearer $USER_T" \
  "$SUPPLIERS/suppliers?page=0&size=5"
curl -s -o /dev/null -w 'search+filter:      %{http_code}\n' -H "Authorization: Bearer $USER_T" \
  "$SUPPLIERS/suppliers?search=coffee&category=Coffee&sort=name,desc"
curl -s -o /dev/null -w 'nearest to me:      %{http_code}\n' -H "Authorization: Bearer $USER_T" \
  "$SUPPLIERS/suppliers?lat=1.2966&lng=103.7764"
curl -s -o /dev/null -w 'empty search (200): %{http_code}\n' -H "Authorization: Bearer $USER_T" \
  "$SUPPLIERS/suppliers?search=zzz-no-match"
curl -s -o /dev/null -w 'no token (401):     %{http_code}\n' "$SUPPLIERS/suppliers"

### 10. Supplier writes (P2.3/P2.4, once implemented): ADMIN 201 vs USER 403
j -X POST "$SUPPLIERS/suppliers" -H "Authorization: Bearer $ADMIN_T" \
  -d '{"name":"Demo Kopi","building":"COM1","categories":["Coffee"],"openingTime":"09:00","closingTime":"18:00"}'
j -X POST "$SUPPLIERS/suppliers" -H "Authorization: Bearer $USER_T" \
  -d '{"name":"Should Fail"}'    # → 403 problem+json
j -X POST "$SUPPLIERS/suppliers" -H "Authorization: Bearer $ADMIN_T" \
  -d '{"building":"no name"}'    # → 400, detail names the missing field
