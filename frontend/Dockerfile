# syntax=docker/dockerfile:1
# Static build served by nginx: locally (docker-compose.yml, e2e) and behind Caddy on the demo instance
# (deploy/docker-compose.prod.yml, built with an empty VITE_API_URL, i.e. same origin).

# The output is static files, so the build runs natively on the build machine for any target platform
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
# Vite inlines these at build time (see .env.example)
ARG VITE_API_URL
ARG VITE_WS_URL
ARG VITE_GOOGLE_CLIENT_ID
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
