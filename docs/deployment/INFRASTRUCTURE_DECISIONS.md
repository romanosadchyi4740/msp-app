# Infrastructure and delivery decisions

Decisions on how SyncChord is built, checked and deployed for the course demo, and why. Agreed on 2026-11-18.
When a decision changes, update this file in the same PR and add the date.

## 1. Goal and scope

- **Goal**: the app must be **publicly accessible, not publicly used**. It needs an HTTPS address for the course
  defense and a few friends testing it, not real traffic, high availability or scaling.
- **Feature scope for the demo**: the SyncChord core is complete and is what gets shown:
  - shared tab position with anchor-time sync and measured drift;
  - the Web Audio metronome;
  - the WebRTC audio mesh with TURN, plus connection quality;
  - rooms, invites, lessons with the 40-minute limit, tabs, room chat, device check.
- **Deliberately left out for now** (the Jira tickets stay open):
  - Premium plan and payments, epic KAN-12. `billing` already has the `PremiumChecker` extension point with a
    stub (`NoSubscriptionsPremiumChecker`), so every user gets the free 40-minute limit.
  - Complaints and moderation, epic KAN-13. `rightsConfirmed` on upload still covers R14.
  - Playwright e2e in CI, load testing (KAN-61), beta and survey (KAN-63).

## 2. Two infrastructure plans

| | Full-scale plan | Demo plan (current) |
|---|---|---|
| Jira | epic **KAN-6**: KAN-24 … KAN-28 | epic **KAN-102**: KAN-103, KAN-104, KAN-105 |
| Network | own VPC | default VPC, one security group |
| Compute | EC2 behind an ALB | one EC2 instance, Docker Compose |
| Database | RDS PostgreSQL | PostgreSQL 15 container on the instance |
| Frontend | S3 + CloudFront | nginx container behind Caddy |
| TLS | ACM on ALB/CloudFront, own domain | Caddy + Let's Encrypt, DuckDNS name |
| TURN | coturn on its own EC2, TURN over TLS on 443 | coturn container on the same instance, UDP/TCP 3478, no TLS |
| Secrets | Secrets Manager | `.env` on the instance (mode 600) or SSM Parameter Store |
| Cost | ~45–55 USD/month | **~13 USD/month** |

The full-scale tickets (KAN-24…28) are **intentionally unchanged**; they remain the plan for a production system. Do
not rewrite them for the demo. The demo work lives only in KAN-102.

## 3. AWS account and cost

- The AWS account is **older than 12 months**, so the 12-month free tier does not apply. Everything is paid.
- Region `eu-central-1` (Frankfurt). Rough monthly prices (check the AWS pricing pages before relying on them):

  | Item | Monthly |
  |---|---|
  | `t4g.micro` (ARM, 2 vCPU burstable, 1 GB) | ~7 USD |
  | public IPv4 address (Elastic IP), charged since 2024 | ~3.6 USD |
  | 20 GB gp3 root volume | ~2 USD |
  | S3 uploads, requests | cents |
  | **Total** | **~13 USD** |

- `us-east-1` was considered on 2026-11-21 (~1.5 USD/month cheaper) and rejected: its ~120 ms round trip from Ukraine,
  against ~30–40 ms to Frankfurt, would add delay to TURN-relayed audio and make the clock sync less accurate.

- Alternatives: `t3.micro` (x86, 1 GB) ~9 USD; `t4g.small` (ARM, 2 GB) ~12 USD, i.e. ~18 USD in total. The
  instance type is a Terraform variable, so switching is a one-line change.
- No RDS (~15 USD) and no ALB (~18 USD): together they would more than double the cost for no benefit at this scale.
- An **AWS Budgets** alert (free) e-mails at 80% of the actual spend and at 100% of the forecast (limit variable,
  default 20 USD).
- After the defense, `terraform destroy` removes everything; the only remaining cost is the small state bucket.

## 4. The instance (KAN-103, KAN-104)

### Instance type and memory

- **`t4g.micro` (ARM)** is the cheapest instance type that works. ARM is fine: every image we use (Temurin, Postgres,
  coturn, Caddy, nginx) has arm64 builds, and GitHub offers free arm64 runners for public repositories.
- 1 GB of RAM is tight but enough for a handful of users, provided that:
  - **a 2 GB swap file** exists (the most important point: without it the kernel kills the JVM or Postgres);
  - the **JVM is limited**: `-Xmx384m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k`, Hikari pool size 5;
  - **Postgres is tuned down**: `shared_buffers=64MB`, `max_connections=20`;
  - **nothing is built on the instance**: images are built in GitHub Actions and only pulled (KAN-105);
  - uploads go to **S3**, not to a MinIO container (saves ~150 MB).
- Rough memory budget: OS + Docker ~150 MB, backend ~350–450 MB, Postgres ~60–100 MB, coturn + Caddy + nginx ~60 MB.
- If it struggles, move to `t4g.small` rather than tuning further.

### HTTPS and the domain

- **HTTPS is mandatory**: browsers allow `getUserMedia` (the microphone) only in a secure context, and an HTTPS page
  cannot open an `http://` API or a `ws://` socket (mixed content). Plain `http://<ip>` would break the lesson audio.
- **Caddy** terminates TLS and gets a Let's Encrypt certificate automatically. It routes `/api/*`, `/auth/*`,
  `/ws*` and `/actuator/health` to the backend and everything else to the frontend. With everything on one origin,
  `VITE_API_URL` is empty and CORS only needs that one origin.
- **Domain: DuckDNS** (free, `<name>.duckdns.org`) for now. The Elastic IP never changes, so the DuckDNS record is
  set once by hand; no update client is needed.
- If a nicer name is wanted later: a `.com` from **Cloudflare Registrar** or **Porkbun** costs ~10–12 USD/year
  (Cloudflare sells at cost, so renewals cost the same). Beware 1–3 USD first-year offers on `.xyz`/`.online` and
  similar: they often renew at 15–40 USD. Route 53 works but costs the most (~14 USD/year + 0.50 USD/month for the
  hosted zone). Changing the domain only means changing `APP_DOMAIN`, the CORS origin and the Google OAuth origin.
- Considered and dropped: CloudFront in front of the instance, which would give HTTPS on `*.cloudfront.net` without
  any domain. More moving parts than Caddy for no gain here.

### TURN (coturn)

- coturn runs on the same instance and is reachable **directly**, not behind Caddy or any proxy. It uses host
  networking, with `external-ip` set to the Elastic IP; without it, relay candidates would carry the private IP.
- Ports: UDP + TCP 3478, plus the UDP relay range 49160–49200, which must match coturn's `min-port`/`max-port`.
- Credentials: `use-auth-secret` with `TURN_SECRET`, the same scheme as locally (`GET /api/rtc/ice-servers`).
- **No TURN over TLS on 443**, since Caddy owns 443 on this instance. Most networks allow UDP or TCP 3478. Relay-only
  mode is the fallback during the demo. TURN over TLS stays in KAN-27.

### Access, storage, secrets

- **No SSH**: port 22 stays closed. A shell comes from **SSM Session Manager**
  (`aws ssm start-session --target <instance-id>`), which needs the instance role `AmazonSSMManagedInstanceCore`. No
  SSH keys to manage or leak.
- **S3 for uploads**: a private bucket with block public access and SSE-S3. Browsers get presigned GET URLs, as now,
  so the bucket's CORS allows GET from the app origin.
- **No AWS keys for the app**: the backend reads S3 credentials from the **instance role** (MinIO client
  `IamAwsProvider`; a small backend change in KAN-104). This needs IMDSv2 with `http_put_response_hop_limit = 2`, so
  containers can reach the instance metadata.
- **Secrets** (`JWT_SECRET`, `ENCRYPTION_SECRET`, `TURN_SECRET`, DB password) live only on the instance in
  `/opt/syncchord/.env` (mode 600), or in SSM Parameter Store under `/syncchord/`. Never in the repository: the
  `prod` profile and `SecretsStartupCheck` refuse to start with a missing value or one committed to the repo.
- Postgres has **no published port**; it is reachable only inside the Compose network.
- Not planned for the demo: database backups (could later be a nightly `pg_dump` to S3).

### Terraform

- Code in `infra/terraform/` (KAN-103). The **owner runs `plan`/`apply`** with their own credentials; Claude writes the
  code and never applies it or touches the state (CLAUDE.md).
- State in an S3 bucket with **native S3 locking** (`use_lockfile = true`, Terraform ≥ 1.10, no DynamoDB table). The
  state bucket comes from a small one-time `bootstrap/` config.
- `*.tfvars` are gitignored (the budget e-mail goes there); `example.tfvars` documents them.
- CI runs `terraform fmt -check` and `terraform validate`, which need no credentials.

## 5. CI (done: KAN-18, PR #65)

- `.github/workflows/ci.yml` runs on every pull request and on every push to `development` and `main`. GitHub picks up
  any workflow file in `.github/workflows/` automatically; no other setup is needed.
- Three jobs (each a separate check):
  - **Backend (tests, coverage)**: `./mvnw verify` on Temurin 25. It runs all unit and integration tests, with
    Testcontainers using the runner's Docker, then writes the JaCoCo report: summary on the run page, HTML as the
    `jacoco-report` artifact. First run: 941 tests, ~3 min, 96.3% line coverage. No coverage threshold yet (KAN-60).
  - **Java style**: `.claude/scripts/java/java_style.py --check` over all backend Java files.
  - **Frontend (lint, tests, build)**: `npm ci`, `npm run lint`, `npm test`, `npm run build` on Node 22.
- A newer push to a PR cancels its running CI; runs for merges into `development`/`main` always finish.
- **Caches** (Maven `~/.m2`, npm) are stored by GitHub and are free. The limit is 10 GB per repository; the least
  recently used caches go first, and anything unused for 7 days is removed. Keys come from a hash of `pom.xml` /
  `package-lock.json`. PRs can read the base branch's cache.
- **Runner minutes**: free without limit for a public repository on standard runners.
- **Lint**: older errors are recorded in `frontend/eslint-suppressions.json` (ESLint bulk suppressions). A new error
  fails CI, and so does a recorded one that is fixed without pruning the file. Fixing them is **KAN-101**.
- Not in CI: Playwright e2e, which needs the running backend, coturn and Chrome; it stays a local check.

## 6. Branch protection (set on 2026-11-18)

On both `development` and `main`:
- the three checks above are **required**, and a PR's branch must be **up to date** with its base before merging;
- **admins may bypass**: "Do not allow bypassing the above settings" is off. The owner wants to keep direct pushes for
  quick fixes; such a push prints "Bypassed rule violations" and CI still runs on it afterwards. For PRs, a red check
  can be merged by the owner only by ticking the explicit bypass box;
- **no required reviews**, since GitHub does not let the owner approve their own PRs;
- force-pushes and branch deletion are blocked.

If a job's `name:` in `ci.yml` changes, update the required checks too; otherwise PRs wait for a check that no longer
runs.

## 7. CD (planned: KAN-105)

- **Release** = merge `development` into `main` (PR). The workflow `.github/workflows/deploy.yml` then:
  1. builds the backend and frontend images on GitHub's **arm64** runner (`ubuntu-24.04-arm`, no emulation), tags them
     with the commit SHA and `latest`, and pushes them to **GHCR** with the workflow's own `GITHUB_TOKEN`;
  2. gets AWS credentials through **GitHub OIDC**, so no AWS keys are stored in GitHub. The deploy role trusts only this
     repository's `main` branch and may only send `AWS-RunShellScript` to this instance;
  3. runs `docker compose pull && docker compose up -d` on the instance through **SSM**, then checks
     `https://<domain>/actuator/health`.
- **Rollback**: run the workflow manually (`workflow_dispatch`) with an older SHA.
- A restart of a few seconds per deploy is acceptable; no blue/green.

## 8. Security work done before going public

- **KAN-22** (PR #63):
  - WebSocket handshakes only from `CORS_ALLOWED_ORIGINS`;
  - `/ws-raw` exists only in the `local` and `test` profiles;
  - no emails or JWT parser messages in logs.
- **KAN-100** (PR #64):
  - expected auth failures return 401/409 instead of 500;
  - client errors are logged at WARN without stack traces;
  - database exceptions are logged without their message (it can hold an email).

## 9. Manual steps for the owner

- [ ] Pick a DuckDNS subdomain (sign in at duckdns.org).
- [ ] AWS CLI profile with rights to create the resources; Terraform ≥ 1.10.
- [ ] `terraform apply` for `bootstrap/`, then the main config (KAN-103); confirm the AWS Budgets e-mail.
- [ ] Set the DuckDNS record to the Elastic IP from the Terraform output.
- [ ] Generate the secrets and write `/opt/syncchord/.env` (KAN-104 README).
- [ ] Add `https://<name>.duckdns.org` to the Google OAuth client's authorized JavaScript origins.
- [ ] Before the defense: test a 3-person room from two different networks (e.g. Wi-Fi and mobile data), and once in
      relay-only mode.
- [ ] After the defense: `terraform destroy` (or stop the instance; the Elastic IP and disk still cost ~6 USD/month).

## 10. Ticket map

| Area | Done | Next / open |
|---|---|---|
| Security before deploy | KAN-22, KAN-100 | — |
| CI | KAN-18 | KAN-101 (lint debt), KAN-60 (coverage target) |
| Demo infrastructure (epic KAN-102) | — | KAN-103 Terraform → KAN-104 stack → KAN-105 CD |
| Full-scale infrastructure (epic KAN-6) | — | KAN-24…28, kept as the later plan |
| Left out of the demo | — | KAN-12 Premium/payments, KAN-13 moderation |
