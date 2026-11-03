FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
COPY client-app/package.json client-app/package.json
COPY driver-app/package.json driver-app/package.json
COPY admin-app/package.json admin-app/package.json
RUN npm ci
COPY . .
ARG APP
ARG VITE_API_BASE=http://localhost:8080
ARG VITE_WS_BASE=http://localhost:8080/ws/tracking
ARG VITE_STRIPE_PK
ENV VITE_API_BASE=$VITE_API_BASE VITE_WS_BASE=$VITE_WS_BASE VITE_STRIPE_PK=$VITE_STRIPE_PK
RUN npm run build --workspace=$APP && cp -r $APP/dist /output
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /output /usr/share/nginx/html
