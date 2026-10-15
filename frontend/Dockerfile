# syntax=docker/dockerfile:1
# Static build served by nginx, for running the whole app locally (e2e).
# In the cloud the same `npm run build` output goes to S3 + CloudFront.

FROM node:22-alpine AS build
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
