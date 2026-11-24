variable "region" {
  description = "AWS region; the backend block in versions.tf names the same one"
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Prefix of resource names and the Project tag"
  type        = string
  default     = "syncchord"
}

variable "app_domain" {
  description = "Public host name of the app without scheme, e.g. myname.duckdns.org; the uploads bucket allows GET from https://<app_domain>"
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$", var.app_domain))
    error_message = "app_domain is a bare host name such as myname.duckdns.org, without https:// or a path."
  }
}

variable "instance_type" {
  description = "EC2 instance type; must be arm64 (Graviton, t4g.*) because the AMI and the images are arm64"
  type        = string
  default     = "t4g.micro"
}

variable "cpu_credits" {
  description = "Burstable CPU credit mode: standard (throttled to the baseline when credits run out, fixed price) or unlimited (keeps bursting, surplus credits are billed)"
  type        = string
  default     = "standard"

  validation {
    condition     = contains(["standard", "unlimited"], var.cpu_credits)
    error_message = "cpu_credits is standard or unlimited."
  }
}

variable "root_volume_gb" {
  description = "Size of the encrypted gp3 root volume in GB; it also holds the Postgres data and the Docker images"
  type        = number
  default     = 20

  validation {
    condition     = var.root_volume_gb >= 10
    error_message = "root_volume_gb is at least 10."
  }
}

variable "turn_relay_port_min" {
  description = "First UDP relay port of coturn (its min-port, KAN-104)"
  type        = number
  default     = 49160
}

variable "turn_relay_port_max" {
  description = "Last UDP relay port of coturn (its max-port, KAN-104); a full relay-only room of 5 needs up to 40 ports"
  type        = number
  default     = 49359

  validation {
    condition     = var.turn_relay_port_max >= var.turn_relay_port_min
    error_message = "turn_relay_port_max must not be lower than turn_relay_port_min."
  }
}

variable "uploads_force_destroy" {
  description = "Let `terraform destroy` delete the uploads bucket even when it still holds files; set to true only before destroying everything after the demo"
  type        = bool
  default     = false
}

variable "github_repository" {
  description = "GitHub repository (owner/name) whose main branch may assume the deploy role (KAN-105)"
  type        = string
  default     = "romanosadchyi4740/msp-app"

  validation {
    condition     = can(regex("^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "github_repository is owner/name, e.g. octocat/hello-world."
  }
}

variable "create_github_oidc_provider" {
  description = "Create the account's GitHub Actions OIDC provider; false when the account already has one (it exists once per account)"
  type        = bool
  default     = true
}

variable "budget_limit_usd" {
  description = "Monthly cost budget in USD for the AWS Budgets alert"
  type        = number
  default     = 20
}

variable "budget_email" {
  description = "E-mail address that gets the budget alerts; set it in terraform.tfvars, never in a committed file"
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_email))
    error_message = "budget_email must be an e-mail address."
  }
}
