# Demo deployment (epic KAN-102, KAN-103): one EC2 instance with Docker Compose (no Elastic IP), the uploads
# bucket, IAM and a budget alert. Decisions: docs/deployment/INFRASTRUCTURE_DECISIONS.md.

terraform {
  # 1.10 is the first version with native S3 state locking (use_lockfile)
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # The bucket comes from the bootstrap config and is passed on init:
  #   terraform init -backend-config="bucket=<state_bucket output of bootstrap>"
  backend "s3" {
    key          = "demo/terraform.tfstate"
    region       = "eu-central-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
      Stack     = "demo"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}
