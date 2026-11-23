# Production stack on the demo instance (KAN-104, epic KAN-102)

The EC2 instance from `infra/terraform` runs the whole app with Docker Compose:

| Service | What it does |
|---|---|
| `caddy` | Ports 80 and 443. Gets and renews the Let's Encrypt certificate for `APP_DOMAIN`, sends `/api/*`, `/auth/*`, `/ws`, `/ws/*` and `/actuator/health` to the backend and everything else to the frontend |
| `frontend` | The `frontend/Dockerfile` image (nginx), built with an empty `VITE_API_URL`, so it calls the API on its own origin |
| `backend` | `prod` profile, JVM capped at 384 MB heap, Hikari pool of 5, S3 through the instance role (no AWS keys) |
| `postgres` | PostgreSQL 15 tuned down (`shared_buffers=64MB`, `max_connections=20`), data in the `pgdata` volume, no published port |
| `coturn` | TURN/STUN on 3478 (UDP + TCP) and UDP relay ports 49160–49359, host network, `use-auth-secret`, no TCP relaying, at most 12 allocations per user |

Files on the instance, all in `/opt/syncchord`: `docker-compose.prod.yml`, `Caddyfile`, `syncchord-up.sh` (from this
folder) and `.env` (secrets, only there). `syncchord.service` runs `syncchord-up.sh` at every boot.

**Why a boot script:** the instance has no Elastic IP (it would cost money while the instance is stopped), so its public
address changes on every stop/start. `syncchord-up.sh` reads the current public and private IP from the instance
metadata, points the DuckDNS name at the public one, writes both into `.env` (coturn's `external-ip` needs them) and
starts Compose. Run it instead of a bare `docker compose up`; other Compose commands work directly.

Images are built and deployed by GitHub Actions (`.github/workflows/deploy.yml`, KAN-105) on every merge into `main`;
see [Continuous deployment](#continuous-deployment). Section 1 below is the manual way, for the first start before
the workflow is set up.

## Before the first start

- `terraform apply` in `infra/terraform` is done (its README); note the outputs `uploads_bucket`, `public_ip` and
  `ssm_connect_command`.
- DuckDNS: the subdomain exists, and you have the account token (top of duckdns.org after signing in).
- Google sign-in: in the Google Cloud console, add `https://<APP_DOMAIN>` to the OAuth client's **Authorized JavaScript
  origins**. Without it the Google button fails; e-mail/password sign-in works regardless.

## 1. Build and push the images (on your PC)

The instance is ARM (`t4g`), so the images are built for `linux/arm64`. Maven and Vite run natively (the Dockerfiles
build on `$BUILDPLATFORM`); only the small runtime stages are emulated, which Docker Desktop does out of the box.

1. A GitHub personal access token (classic) with the `write:packages` scope, then:
   ```bash
   echo <token> | docker login ghcr.io -u <github-name> --password-stdin
   ```
2. From the repository root (PowerShell needs the same commands; `^`/backtick line continuation aside):
   ```bash
   docker buildx build --platform linux/arm64 --push -t ghcr.io/<github-name>/msp-app-backend:latest backend
   docker buildx build --platform linux/arm64 --push -t ghcr.io/<github-name>/msp-app-frontend:latest \
     --build-arg VITE_API_URL= \
     --build-arg VITE_GOOGLE_CLIENT_ID=<the OAuth client id> \
     frontend
   ```
   `<github-name>` in lower case. `VITE_GOOGLE_CLIENT_ID` is the same id the backend gets as `GOOGLE_CLIENT_ID`.
3. Make both packages **public** once (GitHub → your profile → Packages → package → Package settings → Change
   visibility), so the instance pulls them without a token. They contain no secrets: those come from `.env` at run time.

## 2. First start (on the instance)

Open a shell with the `ssm_connect_command` output, then:

```bash
sudo -i
cd /opt/syncchord
REF=development   # branch or commit of this repository to take the files from
for f in docker-compose.prod.yml Caddyfile syncchord-up.sh syncchord.service .env.example; do
  curl -fsSLO "https://raw.githubusercontent.com/romanosadchyi4740/msp-app/$REF/deploy/$f"
done
chmod 755 syncchord-up.sh
install -m 600 .env.example .env
```

Generate the secrets and put them into `.env` (`nano .env`), with the other values from the comments there:

```bash
echo "DB_PASSWORD=$(openssl rand -hex 24)"
echo "JWT_SECRET=$(openssl rand -base64 48)"       # Base64 of at least 32 bytes
echo "ENCRYPTION_SECRET=$(openssl rand -hex 16)"   # exactly 32 characters
echo "TURN_SECRET=$(openssl rand -hex 32)"
```

`ENCRYPTION_SECRET` encrypts chat messages in the database: changing it later makes the stored messages unreadable.
`DB_PASSWORD` is set when the `pgdata` volume is created; changing it afterwards needs `ALTER USER` in Postgres too.

Then install the boot service and start everything:

```bash
cp syncchord.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now syncchord      # pulls the images and starts the stack; a few minutes the first time
journalctl -u syncchord --no-pager    # the IPs, the DuckDNS answer and the Compose output
```

The backend needs about a minute to start (Flyway runs the migrations). Caddy gets the certificate as soon as the
DuckDNS name points at the instance, which `syncchord-up.sh` has just set (60 s TTL). Then open `https://<APP_DOMAIN>`.

## Checks

```bash
cd /opt/syncchord
docker compose -f docker-compose.prod.yml ps        # all running, backend "healthy"
curl -s https://<APP_DOMAIN>/actuator/health        # {"status":"UP"}
docker stats --no-stream                            # memory of each container against its limit
free -m; swapon --show                              # RAM and swap in use
```

From your PC, only 80, 443, 3478 and the relay range answer; 22, 5432 and 8080 time out:
`Test-NetConnection <public_ip> -Port 5432` (PowerShell) or `nc -vz -w 5 <public_ip> 5432`.

WebRTC: open a room from two different networks (e.g. Wi-Fi and mobile data), and once with relay-only mode switched
on (Settings page), which forces all audio through coturn. `docker compose -f docker-compose.prod.yml logs coturn`
shows the allocations.

## Everyday operations

All from `/opt/syncchord` as root:

| Task | Command |
|---|---|
| Logs | `docker compose -f docker-compose.prod.yml logs -f --tail 200 backend` (or `caddy`, `coturn`, `postgres`) |
| New images (after pushing new `latest`) | `./syncchord-up.sh --pull && docker image prune -f` (with `IMAGE_TAG=latest`) |
| A specific build | `./syncchord-up.sh --tag <commit SHA>` (sets `IMAGE_TAG` in `.env` and pulls; what the deploy workflow runs) |
| After editing `.env` or the Compose file | `./syncchord-up.sh` (recreates only what changed) |
| Restart the backend | `docker compose -f docker-compose.prod.yml restart backend` |
| Database shell | `docker compose -f docker-compose.prod.yml exec postgres psql -U syncchord music_app_db` |
| Back up the database | `docker compose -f docker-compose.prod.yml exec postgres pg_dump -U syncchord music_app_db > dump.sql` |
| Stop everything | `systemctl stop syncchord && docker compose -f docker-compose.prod.yml stop` |

**Stopping the instance between sessions** (EC2 console or `aws ec2 stop-instances`) keeps the database and the
certificate and costs only the disk (~2 USD/month). On the next start the boot service updates DuckDNS and coturn by
itself; the site is back after 1–2 minutes.

**Memory**: the JVM limits in `docker-compose.prod.yml` fit `t4g.micro` (1 GB + 2 GB swap). On `t4g.small`, set e.g.
`BACKEND_JAVA_OPTS=-Xmx768m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k` and `BACKEND_MEM_LIMIT=1100m` in
`.env`, and run `./syncchord-up.sh`.

**Destroying** the instance (`terraform destroy`) deletes the database and the certificate. Let's Encrypt issues at
most 5 certificates a week for the same name; Caddy falls back to ZeroSSL when that limit is hit.

## Continuous deployment

`.github/workflows/deploy.yml` (KAN-105). A release is a PR from `development` into `main`; after the merge the
workflow:

1. builds both images natively on GitHub's arm64 runner and pushes them to GHCR as `<commit SHA>` and `latest`, with
   the workflow's own `GITHUB_TOKEN`;
2. gets AWS credentials through GitHub OIDC (no AWS keys in GitHub) for the deploy role from `infra/terraform`, which
   trusts only this repository's `main` and may only run `AWS-RunShellScript` on this instance;
3. through SSM, as root in `/opt/syncchord`: downloads `docker-compose.prod.yml`, `Caddyfile` and `syncchord-up.sh`
   of the same commit, runs `./syncchord-up.sh --tag <SHA>` (pins `IMAGE_TAG` in `.env`), restarts Caddy if the
   Caddyfile changed and prunes old images;
4. waits for `https://<APP_DOMAIN>/actuator/health` to answer `UP` (up to 5 minutes).

`.env` and `syncchord.service` are never touched; change those by hand. A deploy restarts the backend, so open
lessons drop for about a minute.

**Rollback**: Actions → Deploy → Run workflow (on `main`), with the full SHA of an earlier `main` commit. It skips the
build, checks that both images of that SHA exist, and deploys them with the `deploy/` files of that commit.

**One-time setup**

1. `terraform apply` with the KAN-105 changes (creates the GitHub OIDC provider and the `syncchord-github-deploy`
   role; if the account already has a GitHub OIDC provider, set `create_github_oidc_provider = false`).
2. Repository → Settings → Secrets and variables → Actions → **Variables** (none of these is secret):

   | Variable | Value |
   |---|---|
   | `AWS_DEPLOY_ROLE_ARN` | `terraform output -raw deploy_role_arn` |
   | `AWS_INSTANCE_ID` | `terraform output -raw instance_id` |
   | `APP_DOMAIN` | the same as in `.env`, e.g. `myname.duckdns.org` |
   | `GOOGLE_CLIENT_ID` | the OAuth client id (the same as `GOOGLE_CLIENT_ID` in `.env`) |
3. `.env` on the instance: `IMAGE_REGISTRY=ghcr.io/<owner in lower case>`, the owner of this repository.
4. If the packages `msp-app-backend` and `msp-app-frontend` were first pushed by hand (section 1), give the repository
   write access to each once: the package → Package settings → Manage Actions access → Add repository → Write.
   Packages the workflow creates itself are linked to the repository through the image's source label. Keep them
   public, so the instance pulls without a token.
5. The repository must stay public: the instance downloads the `deploy/` files from `raw.githubusercontent.com`, and
   the arm64 runners are free only for public repositories.

The instance must be running when the workflow runs; a stopped instance fails the SSM step (start it and re-run the
failed jobs).
