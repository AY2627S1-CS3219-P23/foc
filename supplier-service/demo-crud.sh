#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Sonnet 5), 2026-09-29.
# Scope: D2 demo script for issue #104 — proves the Supplier Service's
# CRUD APIs work end-to-end (auth, RBAC, create/read/update/delete)
# purely via curl, with no browser/UI involved, per the D2 rubric's
# "demonstrate... through its APIs without the UI being present or
# running." Requires user-service + supplier-service already running
# (e.g. `docker compose up -d user-service supplier-service` from the
# repo root) and one pre-existing ADMIN account — creating an admin is
# a one-time DB-level bootstrap (no public promote-to-admin API exists
# yet), so it's deliberately not scripted here; see the comment below.
#
# Usage: bash demo-crud.sh
# Override accounts: ADMIN_USERNAME=... ADMIN_PASSWORD=... USER_USERNAME=... USER_PASSWORD=... bash demo-crud.sh

set -euo pipefail

USER_SERVICE="${USER_SERVICE:-http://localhost:8087}"
SUPPLIER_SERVICE="${SUPPLIER_SERVICE:-http://localhost:8086}"
ADMIN_USERNAME="${ADMIN_USERNAME:-crudtest104}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Password1234}"
USER_USERNAME="${USER_USERNAME:-regularuser}"
USER_PASSWORD="${USER_PASSWORD:-Password1234}"

# --- one-time setup note (not run by this script) -----------------
# If ADMIN_USERNAME doesn't exist yet, create it once:
#   curl -X POST $USER_SERVICE/auth/signup -H "Content-Type: application/json" \
#     -d '{"email":"eXXXXXXX@u.nus.edu","username":"crudtest104","password":"Password1234"}'
#   docker exec <user-db-container> psql -U user -d user \
#     -c "UPDATE users SET role='ADMIN' WHERE username='crudtest104';"
# --------------------------------------------------------------------

section() { echo; echo "=================================================="; echo "$1"; echo "=================================================="; }

extract() { sed -n "s/.*\"$1\":\"\{0,1\}\([^,\"}]*\)\"\{0,1\}.*/\1/p"; }

section "0. Log in as both accounts"
ADMIN_TOKEN=$(curl -s -X POST "$USER_SERVICE/auth/login" -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" | extract accessToken)
USER_TOKEN=$(curl -s -X POST "$USER_SERVICE/auth/login" -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\":\"$USER_USERNAME\",\"password\":\"$USER_PASSWORD\"}" | extract accessToken)

if [ -z "$ADMIN_TOKEN" ] || [ -z "$USER_TOKEN" ]; then
  echo "Could not log in — check the accounts exist (see the setup note in this script) and both services are running."
  exit 1
fi
echo "Got both tokens."

section "1. No token at all -> 401"
curl -s -w "\nHTTP %{http_code}\n" "$SUPPLIER_SERVICE/suppliers"

section "2. Regular USER can GET all suppliers -> 200"
curl -s -w "\nHTTP %{http_code}\n" "$SUPPLIER_SERVICE/suppliers?size=2" \
  -H "Authorization: Bearer $USER_TOKEN"

section "3. Regular USER CANNOT create a new supplier -> 403 (role gate)"
curl -s -w "\nHTTP %{http_code}\n" -X POST "$SUPPLIER_SERVICE/suppliers" \
  -H "Authorization: Bearer $USER_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Should Be Rejected","location":"Nowhere","categories":[],"openingTime":"09:00","closingTime":"18:00","latitude":1.3,"longitude":103.8}'

section "4. ADMIN creates a new supplier -> 201"
CREATE_RESPONSE=$(curl -s -X POST "$SUPPLIER_SERVICE/suppliers" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Demo Cafe","location":"Demo Block, Level 1","categories":["Food","Coffee"],"openingTime":"08:00","closingTime":"20:00","description":"Created by demo-crud.sh","latitude":1.2966,"longitude":103.7764}')
echo "$CREATE_RESPONSE"
SUPPLIER_ID=$(echo "$CREATE_RESPONSE" | extract id)
echo "Created supplier id=$SUPPLIER_ID"

section "5. Search endpoint returns matching suppliers, including the one just created -> 200"
curl -s -w "\nHTTP %{http_code}\n" "$SUPPLIER_SERVICE/suppliers?search=Demo" \
  -H "Authorization: Bearer $USER_TOKEN"

section "6. ADMIN updates new supplier created in point 4 -> 200"
curl -s -w "\nHTTP %{http_code}\n" -X PUT "$SUPPLIER_SERVICE/suppliers/$SUPPLIER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Demo Cafe (renamed) ","location":"Demo Block, Level 2","categories":["Shopping"],"openingTime":"09:00","closingTime":"21:00","description":"Updated by demo-crud.sh","latitude":1.30,"longitude":103.80}'

section "7. Regular USER cannot delete the new supplier -> 403 (role gate again)"
curl -s -w "\nHTTP %{http_code}\n" -X DELETE "$SUPPLIER_SERVICE/suppliers/$SUPPLIER_ID" \
  -H "Authorization: Bearer $USER_TOKEN"

section "8. ADMIN deletes the new supplier -> 204"
curl -s -w "\nHTTP %{http_code}\n" -X DELETE "$SUPPLIER_SERVICE/suppliers/$SUPPLIER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

section "9. ADMIN tries to update the deleted supplier -> 404 on update"
curl -s -w "\nHTTP %{http_code}\n" -X PUT "$SUPPLIER_SERVICE/suppliers/$SUPPLIER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Ghost","location":"Nowhere","categories":[],"openingTime":"09:00","closingTime":"18:00","latitude":1.3,"longitude":103.8}'

section "10. ADMIN tries to create new supplier with missing required name field -> 400"
curl -s -w "\nHTTP %{http_code}\n" -X POST "$SUPPLIER_SERVICE/suppliers" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"location":"Block A","categories":[],"openingTime":"09:00","closingTime":"18:00","latitude":1.3,"longitude":103.8}'

section "Done"
echo "Supplier Service exercised end-to-end via its REST API only — no browser/UI involved."
