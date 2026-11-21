# One-time setup (KAN-103): the S3 bucket that holds the state of the main config in infra/terraform.
# Its own state stays local (terraform.tfstate in this folder, gitignored); it is applied once and rarely changes.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "syncchord"
      ManagedBy = "terraform"
      Stack     = "bootstrap"
    }
  }
}

variable "region" {
  description = "AWS region of the state bucket"
  type        = string
  default     = "eu-central-1"
}

data "aws_caller_identity" "current" {}

# Bucket names are global, so the account id keeps the name unique
resource "aws_s3_bucket" "state" {
  bucket = "syncchord-tfstate-${data.aws_caller_identity.current.account_id}"

  # The state of every later apply lives here; removing the bucket by accident would orphan the resources
  lifecycle {
    prevent_destroy = true
  }
}

# Every state write is kept, so a broken apply can be rolled back to an earlier version
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# The state holds the account id, ARNs and the budget e-mail: refuse any request that is not over TLS
data "aws_iam_policy_document" "state_tls_only" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = data.aws_iam_policy_document.state_tls_only.json

  depends_on = [aws_s3_bucket_public_access_block.state]
}

# Old state versions are only needed for a short time; keeps the bucket from growing forever
resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  depends_on = [aws_s3_bucket_versioning.state]
}

output "state_bucket" {
  description = "Pass to `terraform init -backend-config=\"bucket=...\"` in infra/terraform"
  value       = aws_s3_bucket.state.bucket
}
