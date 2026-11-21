output "elastic_ip" {
  description = "Public address of the instance; point the DuckDNS record and coturn's external-ip at it"
  value       = aws_eip.app.public_ip
}

output "instance_id" {
  description = "EC2 instance id (SSM target)"
  value       = aws_instance.app.id
}

output "uploads_bucket" {
  description = "Uploads bucket name (MINIO_BUCKET_NAME in the app's .env, KAN-104)"
  value       = aws_s3_bucket.uploads.bucket
}

output "app_origin" {
  description = "Origin the uploads bucket allows; also CORS_ALLOWED_ORIGINS of the backend"
  value       = local.app_origin
}

output "ssm_connect_command" {
  description = "Opens a shell on the instance (needs the Session Manager plugin of the AWS CLI)"
  value       = "aws ssm start-session --region ${var.region} --target ${aws_instance.app.id}"
}
