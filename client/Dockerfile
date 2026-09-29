# ---- Build stage ----
FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -qO- http://localhost/ || exit 1
