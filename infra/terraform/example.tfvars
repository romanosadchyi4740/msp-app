# Copy to terraform.tfvars (gitignored, loaded automatically) and fill in. Never commit the real file.

# Required
app_domain   = "myname.duckdns.org"
budget_email = "you@example.com"

# Optional, with their defaults
# instance_type         = "t4g.micro"  # t4g.small (2 GB) if 1 GB plus swap is not enough
# cpu_credits           = "standard"   # "unlimited" keeps bursting past the credits, billed per vCPU-hour
# root_volume_gb        = 20
# budget_limit_usd      = 20
# uploads_force_destroy = false        # true just before `terraform destroy` after the demo
