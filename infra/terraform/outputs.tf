output "public_ip" {
  description = "Public address at the last apply; it changes on every stop/start (syncchord-up.sh updates DuckDNS at boot)"
  value       = aws_instance.app.public_ip
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

output "deploy_role_arn" {
  description = "Role the deploy workflow assumes through GitHub OIDC (repository variable AWS_DEPLOY_ROLE_ARN, KAN-105)"
  value       = aws_iam_role.deploy.arn
}

output "ssm_connect_command" {
  description = "Opens a shell on the instance (needs the Session Manager plugin of the AWS CLI)"
  value       = "aws ssm start-session --region ${var.region} --target ${aws_instance.app.id}"
}
