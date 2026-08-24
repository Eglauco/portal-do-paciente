# Deploy no Railway — Portal do Paciente

O projeto sobe como **3 recursos** no mesmo projeto Railway:

| Recurso | Pasta (Root Directory) | Como builda |
|---|---|---|
| **PostgreSQL** | — (plugin do Railway) | gerenciado |
| **Backend** (Spring Boot) | `back` | Dockerfile |
| **Frontend** (Angular SSR) | `front` | Dockerfile (Node 24) |

> O `app/` (Expo) é um aplicativo **mobile** — não vai para o Railway. Ele é distribuído via build APK/EAS.

---

## Passo a passo

### 1. Criar o projeto e o banco
1. No Railway: **New Project → Deploy from GitHub repo** e selecione `portal-do-paciente`.
2. **New → Database → Add PostgreSQL**. (Deixe o nome do serviço como **`Postgres`**.)

### 2. Serviço do Backend
1. **New → GitHub Repo** (mesmo repositório).
2. Em **Settings → Source**, defina **Root Directory = `back`** (o Railway usa o `back/Dockerfile`).
3. Em **Settings → Networking**, clique em **Generate Domain** e anote a URL (ex.: `https://pop-back-production.up.railway.app`).
4. Em **Variables**, adicione:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
   SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
   APP_CORS_ALLOWED_ORIGINS=https://SEU-FRONT.up.railway.app
   ```
   (o `APP_CORS_ALLOWED_ORIGINS` recebe a URL do front — gere o domínio do front no passo 3 antes, ou volte aqui depois.)

### 3. Serviço do Frontend
1. **New → GitHub Repo** (mesmo repositório).
2. Em **Settings → Source**, defina **Root Directory = `front`**.
3. Em **Settings → Networking**, **Generate Domain** e anote a URL do front.
4. Em **Variables**, adicione:
   ```
   API_URL=https://SEU-BACK.up.railway.app
   ```
   (URL pública do backend, gerada no passo 2 — o Railway a injeta como *build arg* no Dockerfile e ela é embutida no build do Angular. Se alterar depois, faça **Redeploy** para reconstruir.)

### 4. Amarrar CORS e publicar
1. Confirme no **Backend** que `APP_CORS_ALLOWED_ORIGINS` está com a URL do **front**.
2. Faça **Deploy** dos dois serviços (o Railway reconstrói a cada push no GitHub).

---

## Como funciona
- O **backend** lê `PORT` e o datasource das variáveis de ambiente (com fallback local em `application.properties`). O **Flyway** cria todas as tabelas e popula os dados de exemplo (V1–V10) automaticamente no primeiro start.
- O **frontend** embute a URL da API no build via `scripts/set-api-url.js` (variável `API_URL`); o servidor SSR escuta em `PORT`.
- O **CORS** do backend libera a origem do front via `APP_CORS_ALLOWED_ORIGINS`.

## Rodar localmente (sem Railway)
Nada muda: sem as variáveis, o backend usa `localhost:5432/pop` e o front usa `http://localhost:8080`.
