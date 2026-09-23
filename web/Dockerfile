# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5), 2026-09-21, issue #108.
# Scope: multi-stage build — Vite static build served by nginx.
# Reviewed by: Leong Wei Zhi (via pull request).

# 22.22 is the minimum required by react-router v8 (engines field);
# keep in sync with package.json engines and .nvmrc.
FROM node:22.22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# Vite inlines VITE_* values into the bundle at build time, so they are
# passed as build args (browser-visible URLs on host-published ports).
# Changing any of them requires an image rebuild.
ARG VITE_USER_SERVICE_URL
ARG VITE_SUPPLIER_SERVICE_URL
ARG VITE_ORDER_SERVICE_URL
ARG VITE_CREDIT_SERVICE_URL
ARG VITE_NOTIFICATION_SERVICE_URL
ENV VITE_USER_SERVICE_URL=$VITE_USER_SERVICE_URL \
    VITE_SUPPLIER_SERVICE_URL=$VITE_SUPPLIER_SERVICE_URL \
    VITE_ORDER_SERVICE_URL=$VITE_ORDER_SERVICE_URL \
    VITE_CREDIT_SERVICE_URL=$VITE_CREDIT_SERVICE_URL \
    VITE_NOTIFICATION_SERVICE_URL=$VITE_NOTIFICATION_SERVICE_URL
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
