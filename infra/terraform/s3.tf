# Uploads (tracks, covers, tabs, voice messages). Private; browsers open presigned GET URLs made by the backend.
resource "aws_s3_bucket" "uploads" {
  # Bucket names are global, so the account id keeps the name unique
  bucket        = "${var.project}-uploads-${data.aws_caller_identity.current.account_id}"
  force_destroy = var.uploads_force_destroy
}

resource "aws_s3_bucket_public_access_block" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Presigned URLs are fetched by the page (audio player, alphaTab), so the app origin may GET them
resource "aws_s3_bucket_cors_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  cors_rule {
    allowed_methods = ["GET", "HEAD"]
    allowed_origins = [local.app_origin]
    allowed_headers = ["*"]
    expose_headers  = ["Content-Length", "Content-Range", "Accept-Ranges", "ETag"]
    max_age_seconds = 3600
  }
}

# Parts of failed multipart uploads are otherwise kept (and billed) forever
resource "aws_s3_bucket_lifecycle_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

locals {
  app_origin = "https://${var.app_domain}"
}
