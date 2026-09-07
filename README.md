<div align="center">

# Mozhou · AI WeChat Content Studio

**Turn research, writing, editing, review, scheduling, and WeChat publishing into one coherent workflow.**

**🇬🇧 English** | [🇨🇳 简体中文](README_CN.md)

`Java 17+` · `Spring Boot 4` · `Vue 3` · `MySQL 8` · `Docker` · `Agent4j`

</div>

![Mozhou dashboard](docs/images/dashboard.png)

## Why Mozhou?

Publishing a strong WeChat article takes much more than generating a block of text. Research lives in browser tabs, AI drafts require repeated copy and paste, images and layouts need manual cleanup, multiple Official Accounts introduce credential and status sprawl, and scheduled jobs are difficult to inspect when something goes wrong.

Mozhou brings that entire lifecycle into a self-hosted AI content operations studio:

- **An editor-aware AI agent** — the agent reads the current document, targets logical blocks, streams insertions and replacements, and updates metadata or covers through explicit tools.
- **One workflow from idea to WeChat** — manage accounts, articles, assets, drafts, publishing states, and followers without jumping between disconnected tools.
- **Autonomous scheduled creation** — describe a recurring assignment in natural language and let the agent research, browse, verify, write, illustrate, and deliver it on schedule.
- **Human control and traceability** — automatic saving, optimistic locking, revision snapshots, rollback, role-based access, execution history, and audit logs keep automation accountable.
- **Your infrastructure, your credentials** — self-host the application, use your own model gateway, and keep WeChat secrets and LLM API keys encrypted in your database.

## What it can do

| Capability | What you get |
| --- | --- |
| Multiple Official Accounts | Centralized AppID/AppSecret, account type, default author and writing style, connection tests, and access-token caching. |
| AI collaborative editor | Tiptap rich-text editing, autosave, mobile preview, and Agent4j Tool Calling that edits the live document instead of returning a detached draft. |
| Assets and images | Local asset library, web-image import, AI image generation/editing, cover management, and automatic WeChat content-image upload. |
| WeChat drafts and publishing | Create or update drafts, submit publishing jobs, refresh publishing results, and watch slow operations through real-time SSE progress. |
| Scheduled content agents | Persistent Quartz JDBC schedules for one-time, interval, daily, weekly, monthly, yearly, and advanced Cron execution. |
| Autonomous research | Search and browse sources, import media, preserve tool traces, and inspect every scheduled-agent run. |
| Users and security | ADMIN / OPERATOR / EDITOR / REVIEWER / VIEWER roles, token authentication, password reset, expiry redirects, and audit logging. |
| Follower synchronization | Synchronize and query Official Account followers as a foundation for future content operations and analytics. |

## Product tour

### See the complete content pipeline at a glance

![Content operations dashboard](docs/images/dashboard.png)

### Edit with an AI agent that shares the same document context

![AI collaborative article editor](docs/images/ai-editor.png)

### Manage Official Accounts and article lifecycles in one place

<p align="center">
  <img src="docs/images/accounts.png" alt="Official Account management" width="49%">
  <img src="docs/images/articles.png" alt="Article management" width="49%">
</p>

### Let autonomous agents deliver on schedule

<p align="center">
  <img src="docs/images/scheduled-tasks.png" alt="Scheduled tasks" width="49%">
  <img src="docs/images/schedule-editor.png" alt="Scheduled agent configuration" width="49%">
</p>

<details>
<summary><strong>More screens: sign-in, Official Account setup, and system settings</strong></summary>

![Sign-in page](docs/images/login.png)

![Official Account setup](docs/images/account-settings.png)

![LLM and system settings](docs/images/settings.png)

</details>

## Quick start with Docker (recommended)

The fastest path is the published `linux/amd64` + `linux/arm64` image with Docker Compose running MySQL alongside the application.

### 1. Prepare the environment

```bash
git clone https://github.com/onlyGuo/wechat-article-bot.git
cd wechat-article-bot
cp deploy/env/dev.env.example deploy/env/dev.env
```

Edit `deploy/env/dev.env` and replace at least these values:

```dotenv
MYSQL_PASSWORD=a-strong-database-password
MYSQL_ROOT_PASSWORD=another-strong-password
ADMIN_PASSWORD=your-first-login-password
APP_SECRET_KEY=at-least-32-random-characters
```

Generate a strong encryption key with:

```bash
openssl rand -hex 32
```

> [!IMPORTANT]
> `APP_SECRET_KEY` encrypts WeChat AppSecrets and LLM API keys. Keep it unchanged across restarts, migrations, and upgrades. Losing or replacing it makes previously stored secrets unreadable.

### 2. Pull and start

```bash
docker compose --env-file deploy/env/dev.env \
  -f compose.yaml -f compose.dev.yaml pull app mysql

docker compose --env-file deploy/env/dev.env \
  -f compose.yaml -f compose.dev.yaml up --no-build -d
```

Inspect the deployment:

```bash
docker compose --env-file deploy/env/dev.env \
  -f compose.yaml -f compose.dev.yaml ps

docker compose --env-file deploy/env/dev.env \
  -f compose.yaml -f compose.dev.yaml logs -f app
```

Open <http://localhost:8081>. The default username is `admin`; the password is the value you set in `ADMIN_PASSWORD`.

![Mozhou sign-in page](docs/images/login.png)

### 3. Stop or upgrade

```bash
# Stop while preserving database and upload volumes
docker compose --env-file deploy/env/dev.env \
  -f compose.yaml -f compose.dev.yaml down

# Upgrade: change IMAGE_TAG first, then run
docker compose --env-file deploy/env/dev.env \
  -f compose.yaml -f compose.dev.yaml pull app
docker compose --env-file deploy/env/dev.env \
  -f compose.yaml -f compose.dev.yaml up --no-build -d
```

`down` preserves the MySQL and upload volumes. Do not use `down -v` unless you intentionally want to erase that data.

## First-run configuration

### Step 1: change the administrator password

After signing in, open **System Settings → Change Login Password**. Changing a password invalidates every previously issued token for that user.

### Step 2: configure the LLM service

Open **System Settings → LLM Service** and configure:

1. **Protocol**
   - Choose `Responses / Codex` for OpenAI Responses-compatible services and Codex models.
   - Choose `Chat Completions` for OpenAI-compatible chat endpoints.
   - Choose `Anthropic Messages` for native Claude endpoints.
2. **Base URL** — enter the service root only; do not append `/v1/responses`, `/chat/completions`, or `/messages`.
3. **Model and API key** — both must match the selected provider.
4. **Image model** — configure it if agents should generate or edit images. Its Base URL and key can be independent, or the key can reuse the LLM credential.

![LLM configuration](docs/images/settings.png)

### Step 3: connect a WeChat Official Account

Open **Official Accounts → Add Official Account** and enter the name, account type, AppID, AppSecret, original ID, default author, and preferred writing style. Save it, then select **Test Connection**.

![Official Account configuration](docs/images/account-settings.png)

Before testing, make sure:

- The account has the required draft, publishing, material, or follower API permissions.
- The server's outbound IP is present in the WeChat Official Platform allowlist.
- The server time and timezone are correct and it can reach the WeChat APIs.

### Step 4: produce the first article

1. Upload a cover and content images in **Assets**, or upload them from the editor.
2. Create an article, then select its target Official Account and cover.
3. Tell the Mozhou agent the topic, audience, tone, and editing requirements.
4. Use **Preview**, then **Sync Draft**. A cover is required before WeChat draft synchronization.
5. Review the result in WeChat and let a user with publishing permission submit it.

### Step 5: create a scheduled content agent

Open **Scheduled Tasks → New Task**, select a timezone and schedule, then describe in natural language:

- The freshness window, trusted sources, and facts that must be verified.
- The target reader, topic boundary, structure, tone, and length.
- The cover and inline-image strategy.
- Whether the result should remain a local draft, sync to WeChat drafts, or submit for publishing.

Run the task once with **Run Now** before enabling the schedule. Review its research, tool calls, output, and errors in the execution history.

![Scheduled content agent](docs/images/schedule-editor.png)

## Production deployment

For production, use a dedicated MySQL 8 instance and place an HTTPS reverse proxy in front of the application.

```bash
cp deploy/env/prod.env.example deploy/env/prod.env
# Set the database URL, passwords, APP_SECRET_KEY, and IMAGE_TAG

docker compose --env-file deploy/env/prod.env \
  -f compose.yaml -f compose.prod.yaml pull

docker compose --env-file deploy/env/prod.env \
  -f compose.yaml -f compose.prod.yaml up -d
```

The production override enables a read-only root filesystem, drops Linux capabilities, prevents privilege escalation, and applies CPU/memory boundaries. Uploaded assets live in the `uploads_data` volume. Back up both that volume and MySQL before every upgrade.

## Environment variables

| Variable | Purpose | Production guidance |
| --- | --- | --- |
| `IMAGE_REPOSITORY` | Container image repository | Use `docker.io/guoshengkai/wechat-article-bot` for the published image. |
| `IMAGE_TAG` | Image version | Pin a release version instead of relying only on `latest`. |
| `MYSQL_URL` | JDBC connection URL | Enable TLS and restrict network access. |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | Application database account | Use a dedicated least-privilege account. |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Bootstrap administrator | Used for initialization; change the password after first login. |
| `APP_SECRET_KEY` | Master key for stored credentials | Use a random value of at least 32 characters and preserve it permanently. |
| `TOKEN_TTL_HOURS` | Back-office login lifetime | Set according to your organization's security policy. |
| `STORAGE_PATH` | Image asset directory | Mount persistent storage and back it up regularly. |
| `JAVA_TOOL_OPTIONS` | JVM memory, encoding, and timezone | Tune `MaxRAMPercentage` for the container limit. |

## macOS Apple Container

On macOS 26, Apple's native `container` CLI can build, run, and publish OCI images without Docker Desktop:

```bash
container system start
container build --arch arm64 --tag wechat-article-bot:local --file Dockerfile .
cp deploy/env/native.env.example deploy/env/native.env
container volume create wechat-article-uploads
container run --rm --publish 8081:8081 \
  --volume wechat-article-uploads:/app/data/uploads \
  --env-file deploy/env/native.env wechat-article-bot:local
```

Apple `container` does not currently include Compose. Multi-container environments require manually combining networks, volumes, and multiple `container run` commands. Docker Compose remains the recommended full local stack.

## Run from source

Requirements: JDK 17+, MySQL 8+, and Node.js 20+.

```sql
CREATE DATABASE `wechat-article` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
cp .env.example .env
# Configure the application database, test database, administrator, and APP_SECRET_KEY
./mvnw spring-boot:run
```

In another terminal:

```bash
cd webui
npm install
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/api` and `/uploads` to `http://localhost:8081`.

## Verification

```bash
./mvnw test
cd webui && npm run build
```

Backend integration tests use a real MySQL server rather than H2 compatibility mode. `ENV.MYSQL_TEST_URL` must point to a dedicated database named `wechat-article-test`; the test bootstrap validates that name before clearing it.

## Technology stack

- **Backend:** Java 17, Spring Boot 4.1, Spring Security, Smart MyBatis, Quartz JDBC, and Agent4j.
- **Frontend:** Vue 3, Vite, Pinia, Tiptap, DOMPurify, and SSE.
- **Storage:** MySQL 8 plus local or mounted file storage.
- **Integrations:** WeChat Official Platform APIs, OpenAI-compatible Responses / Chat Completions, Anthropic Messages, and image-generation services.

## Security and operations checklist

- Never commit `.env` or `deploy/env/*.env` files.
- Put the application behind HTTPS and expose only required ports.
- Back up MySQL and the upload volume regularly and before upgrades.
- Rotate LLM and WeChat credentials when required, but do not casually replace `APP_SECRET_KEY`.
- Validate scheduled agents in local-draft or WeChat-draft mode before allowing automatic publishing.

## License

Licensed under the [Apache License 2.0](LICENSE).
