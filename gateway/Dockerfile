# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5), 2026-10-11, issue #151 (gateway half).
# Scope: OpenResty image for the API gateway (team decisions, Leong Wei
# Zhi, 2026-10-11, options Q&A: OpenResty/Lua gateway verifying JWTs and
# checking the Redis denylist in-process). Minor-pinned like the other
# images; the -fat variant ships opm, which installs lua-resty-jwt
# (lua-resty-redis is part of the OpenResty bundle).
# PR #176 review (@Sinnez1): the opm package pinned too — an unpinned
# get resolves whatever is latest at build time, which could change
# verification behaviour (or the reason strings auth.lua matches)
# unannounced.
# Reviewed by: Leong Wei Zhi (via pull request).

FROM openresty/openresty:1.31.1.1-alpine-fat

# JWT verification library (HS256); pulls its lua-resty-openssl dependency
RUN /usr/local/openresty/bin/opm get cdbattags/lua-resty-jwt=0.3.2

COPY nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
COPY lua /usr/local/openresty/nginx/lua
