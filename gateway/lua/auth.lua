-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Fable 5), 2026-10-11, issue #151 (gateway half).
-- Scope: access-phase bearer check per the team's decisions (Leong Wei
-- Zhi, 2026-10-11, options Q&A, recorded in docs/gateway.md):
--   G5  anonymous requests (no Bearer, incl. OPTIONS preflights) pass
--       through untouched — the services' Spring Security decides what
--       needs auth; a Bearer token present is verified (HS256 sig + exp)
--       and checked against the Redis denylist
--   G6  Redis unreachable => fail open (sig+exp still enforced)
--   G10 a denylisted jti is 401 "Token has been revoked"
-- 401 bodies mirror the services' problem+json shape
-- (SecurityProblemResponses: type/title/status/detail/instance) and the
-- same WWW-Authenticate header, plus the CORS origin header so the
-- browser can read a gateway-origin error.
-- PR #176 Copilot review: the user-service public auth routes are
-- skipped (except logout), mirroring JwtAuthenticationFilter's
-- UNFILTERED/LOGOUT matchers — apiFetch attaches any stored token to
-- every call, so without the skip a stale token in the browser would
-- block login/sign-up at the gateway, the exact recovery case the
-- filter's skip exists for (issue #91 decision).
-- Reviewed by: Leong Wei Zhi (via pull request).

local cjson = require "cjson.safe"
local jwt = require "resty.jwt"
local validators = require "resty.jwt-validators"
local redis = require "resty.redis"

local DENYLIST_PREFIX = "denylist:"
local REDIS_HOST = "redis"
local REDIS_PORT = 6379
local REDIS_TIMEOUT_MS = 100 -- keep the hot path tight; errors fail open
local KEEPALIVE_IDLE_MS = 10000
local KEEPALIVE_POOL = 100

local function reject(detail)
    ngx.status = ngx.HTTP_UNAUTHORIZED
    ngx.header["Content-Type"] = "application/problem+json"
    ngx.header["WWW-Authenticate"] = 'Bearer error="invalid_token"'
    -- without this the browser can't read a cross-origin error body and
    -- the SPA sees a network failure instead of the 401
    ngx.header["Access-Control-Allow-Origin"] = WEB_ALLOWED_ORIGIN
    ngx.say(cjson.encode({
        type = "about:blank",
        title = "Unauthorized",
        status = 401,
        detail = detail,
        instance = ngx.var.request_uri,
    }))
    return ngx.exit(ngx.HTTP_UNAUTHORIZED)
end

-- preflights never carry credentials; the services answer them (CORS)
if ngx.req.get_method() == "OPTIONS" then
    return
end

-- user-service's public auth routes never read the Authorization
-- header (JwtAuthenticationFilter skips them so a stale browser token
-- can't block login or sign-up) — the gateway honors the same
-- exception, or an expired token stored by the SPA would 401 the very
-- login that replaces it. Logout is the carve-out from the carve-out:
-- it needs its token verified, like the filter's LOGOUT matcher.
-- request_uri, not uri: the locations' prefix-stripping rewrite runs
-- before this access phase, so uri is already /auth/... here
local path = ngx.var.request_uri:match("^[^?]*")
if path:find("^/api/user/auth/") and path ~= "/api/user/auth/logout" then
    return
end

-- no Bearer token: forward untouched (G5). Non-Bearer Authorization
-- schemes also pass — mirroring JwtAuthenticationFilter, which forwards
-- them unauthenticated for the route rules to answer
local header = ngx.var.http_authorization
if not header then
    return
end
local token = header:match("^[Bb][Ee][Aa][Rr][Ee][Rr]%s+(%S+)%s*$")
if not token then
    return
end

local jwt_obj = jwt:load_jwt(token)
-- the platform convention is HS256 exactly (same check as the Java
-- verifiers), which also shuts the alg-confusion door
if not jwt_obj.valid or jwt_obj.header.alg ~= "HS256" then
    return reject("Invalid token")
end

local verified = jwt:verify_jwt_obj(JWT_SECRET, jwt_obj, {
    exp = validators.required(validators.is_not_expired()),
})
if not verified.verified then
    local reason = verified.reason or ""
    if reason:find("expired", 1, true) then
        return reject("Token has expired")
    end
    return reject("Invalid token")
end

-- a token without a jti has nothing to look up; sig+exp already passed
local jti = verified.payload and verified.payload.jti
if type(jti) ~= "string" or jti == "" then
    return
end

local red = redis:new()
red:set_timeouts(REDIS_TIMEOUT_MS, REDIS_TIMEOUT_MS, REDIS_TIMEOUT_MS)

local ok, err = red:connect(REDIS_HOST, REDIS_PORT)
if not ok then
    -- fail open (G6): revocation is unavailable, validity still enforced
    ngx.log(ngx.ERR, "denylist redis connect failed, failing open: ", err)
    return
end

local denied, qerr = red:exists(DENYLIST_PREFIX .. jti)
if qerr then
    ngx.log(ngx.ERR, "denylist lookup failed, failing open: ", qerr)
    red:close()
    return
end

-- return the connection to the worker's pool instead of closing
red:set_keepalive(KEEPALIVE_IDLE_MS, KEEPALIVE_POOL)

if denied == 1 then
    return reject("Token has been revoked")
end
