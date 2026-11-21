# Terraform: demo deployment (KAN-103, epic KAN-102)

AWS resources for the course demo: one EC2 instance that runs the whole stack with Docker Compose. Why it looks like
this (no RDS, no ALB, default VPC, no SSH, DuckDNS) is in `docs/deployment/INFRASTRUCTURE_DECISIONS.md`. The full-scale
plan (own VPC, ALB, RDS, CloudFront) is epic KAN-6 and is not here.

The owner runs `plan`/`apply` with their own credentials. Region: `eu-central-1`.

## What gets created

| File | Resources |
|---|---|
| `bootstrap/main.tf` | `syncchord-tfstate-<account id>`: versioned, encrypted, private, TLS-only bucket for the state (applied once) |
| `network.tf` | security group in the default VPC: TCP 80, 443; TCP + UDP 3478; UDP 49160–49200 (`turn_relay_port_*`). **No 22** |
| `instance.tf` | `t4g.micro` (Ubuntu 24.04 arm64), 20 GB encrypted gp3, IMDSv2 with hop limit 2, Elastic IP |
| `cloud-init.yaml` | first boot: 2 GB swap file, Docker Engine + compose plugin, log rotation, `/opt/syncchord` |
| `iam.tf` | instance role: `AmazonSSMManagedInstanceCore`, read/write on the uploads bucket, read of `/syncchord/*` parameters only (a deny blocks the rest, which the managed policy would allow) |
| `s3.tf` | `syncchord-uploads-<account id>`: private, owner-enforced, SSE-S3, CORS GET from `https://<app_domain>`, unfinished multipart uploads removed after a day |
| `budget.tf` | monthly AWS Budgets alert: e-mail at 80% actual and 100% forecast (default limit 20 USD) |

The app stack (Compose file, Caddy, coturn, `.env`) is KAN-104; deploys are KAN-105.

Expected cost: about 13 USD/month (instance ~7, Elastic IP ~3.6, volume ~2, S3 cents).

## Prerequisites

- **Terraform ≥ 1.10** (native S3 state locking). CI uses 1.13. Without a local install it runs in Docker, from this folder:
  `docker run --rm -it -v "${PWD}:/w" -w /w -v "$HOME/.aws:/root/.aws:ro" -e AWS_PROFILE hashicorp/terraform:1.13 <command>`.
  In that case the `$(terraform ... output ...)` in step 3 below does not work; paste the bucket name instead.
- A **default VPC** in `eu-central-1` (every account has one unless it was deleted; `aws ec2 create-default-vpc`
  creates it again).
- **AWS CLI v2** with a profile allowed to create EC2, IAM, S3 and Budgets resources, e.g. `aws configure --profile syncchord`,
  then `export AWS_PROFILE=syncchord` (PowerShell: `$env:AWS_PROFILE = "syncchord"`). Check with `aws sts get-caller-identity`.
- The **Session Manager plugin** for the AWS CLI, for the shell on the instance:
  <https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html>.
- A **DuckDNS** subdomain (sign in at <https://www.duckdns.org>), e.g. `myname.duckdns.org`.

## First apply

1. **State bucket** (once per account):
   ```bash
   cd infra/terraform/bootstrap
   terraform init
   terraform apply
   ```
   Its own state is the local `bootstrap/terraform.tfstate` (gitignored). Keep that file; the bucket has
   `prevent_destroy`, so it is not removed by accident.

2. **Variables**: copy `example.tfvars` to `terraform.tfvars` (gitignored) and set `app_domain` and `budget_email`.

3. **Main config**, from `infra/terraform`:
   ```bash
   terraform init -backend-config="bucket=$(terraform -chdir=bootstrap output -raw state_bucket)"
   terraform plan -out tfplan
   terraform apply tfplan
   ```
   PowerShell: `terraform init -backend-config="bucket=$(terraform -chdir=bootstrap output -raw state_bucket)"` works the
   same. The plan should list only the resources in the table above (about 20).

4. **DuckDNS**: on duckdns.org, set the subdomain's IPv4 to the `elastic_ip` output. It never changes, so this is a
   one-time step; no update client is needed.

5. **Budget alert**: AWS sends the alerts to `budget_email`; check that the address is right under
   Billing → Budgets → `syncchord-monthly`.

## Checks after apply (KAN-103 acceptance)

Cloud-init needs 2–4 minutes after the instance starts (package upgrade and Docker install).

```bash
terraform output -raw ssm_connect_command   # then run the printed command
# on the instance:
cloud-init status --wait                    # "status: done"
sudo docker compose version
swapon --show                               # /swapfile, 2G
ls -ld /opt/syncchord                       # drwxr-x--- root root
```

From your own machine, ports 22 and 5432 must not answer:

```bash
nc -vz -w 5 <elastic_ip> 22     # times out
nc -vz -w 5 <elastic_ip> 5432   # times out
```
(PowerShell: `Test-NetConnection <elastic_ip> -Port 22`.) Port 443 also does not answer yet; Caddy comes in KAN-104.

## Outputs

| Output | Used for |
|---|---|
| `elastic_ip` | DuckDNS record; coturn `external-ip` (KAN-104) |
| `instance_id` | SSM target; deploy workflow (KAN-105) |
| `uploads_bucket` | `MINIO_BUCKET_NAME` in the app's `.env` (KAN-104) |
| `app_origin` | `CORS_ALLOWED_ORIGINS` of the backend |
| `ssm_connect_command` | shell on the instance |

## Changing things later

- **Instance type**: set `instance_type = "t4g.small"` (2 GB) in `terraform.tfvars` and apply; the instance is stopped
  and resized in place, the disk and the Elastic IP stay. Keep to arm64 (`t4g.*`).
- **CPU credits**: the default `standard` keeps the bill fixed; under sustained load the CPU is throttled to the
  baseline (10% for `t4g.micro`). `unlimited` keeps bursting and bills the extra credits.
- **Domain**: change `app_domain`; the bucket CORS follows. The backend's `CORS_ALLOWED_ORIGINS` and the Google OAuth
  client's JavaScript origins have to be changed by hand.
- **New Ubuntu AMI**: ignored on purpose (`lifecycle.ignore_changes = [ami]`), because replacing the instance would
  delete the Postgres data on its root volume. Updates come from unattended-upgrades.
- **`cloud-init.yaml`** runs only on the first boot. Changing it later updates the instance's user data (with a stop and
  start) but does not run it again; apply such changes on the instance by hand, or replace the instance on purpose with
  `terraform apply -replace=aws_instance.app` (this deletes the database).

## After the demo: destroy

The database lives only on the instance and is deleted with it; take a `pg_dump` first if anything should be kept.

```bash
# allow deleting the uploads bucket with its files
terraform apply -var uploads_force_destroy=true
terraform destroy
```

What remains is the state bucket (a few cents a month). To remove it too, empty it (all versions) in the S3 console,
delete `prevent_destroy` from `bootstrap/main.tf` and run `terraform destroy` in `bootstrap/`.

Stopping the instance instead of destroying it still costs about 6 USD/month (Elastic IP and volume).
