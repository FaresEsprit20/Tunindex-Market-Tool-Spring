# syntax=docker/dockerfile:1

# ============================================================================
# Build - compiles the Angular app to static files.
# ============================================================================
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first, so an edit to application source doesn't bust this
# layer and force a full npm ci on every rebuild.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# --configuration development, deliberately NOT the bare `ng build` that
# angular.json's defaultConfiguration would otherwise pick. The production
# configuration's fileReplacements swaps in environment.prod.ts, whose
# gatewayUrl is left as the literal string "https://REPLACE-WITH-YOUR-DOMAIN"
# on purpose - a placeholder written to fail loudly once this ships to a
# real domain. That is correct for an actual deploy and fatal here: this
# stack is reached at http://localhost from the host browser, which is
# exactly what environment.ts (gatewayUrl: http://localhost:8070) already
# says, matching the port docker-compose publishes the gateway on.
RUN npx ng build --configuration development

# ============================================================================
# Serve - nginx, static files only. No Node in the runtime image: this
# project has no server.ts/SSR entry (confirmed - dist/.../browser holds
# nothing but index.html, JS/CSS chunks and assets), so a plain static
# server is the correct and smaller choice.
# ============================================================================
FROM nginx:1.27-alpine AS serve
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/tunindex-market-tool/browser /usr/share/nginx/html
EXPOSE 80
