# ---------- build ----------
FROM node:24 AS build
WORKDIR /app

# URL da API embutida no build do Angular (Railway injeta como build arg).
ARG API_URL=http://localhost:8080
ENV API_URL=$API_URL

COPY package*.json .npmrc ./
RUN npm ci
COPY . .
RUN npm run build

# ---------- runtime ----------
FROM node:24-slim
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/dist ./dist
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./
# O servidor SSR escuta em PORT (Railway injeta); padrão 4000.
EXPOSE 4000
CMD ["node", "dist/pop/server/server.mjs"]
