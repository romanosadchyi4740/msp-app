# Latest Ubuntu 24.04 LTS for arm64 from Canonical; the SSM agent comes preinstalled
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-arm64-server-*"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  iam_instance_profile   = aws_iam_instance_profile.app.name
  vpc_security_group_ids = [aws_security_group.app.id]

  # Base setup only (swap, Docker, /opt/syncchord); the app stack is KAN-104
  user_data = file("${path.module}/cloud-init.yaml")

  credit_specification {
    cpu_credits = var.cpu_credits
  }

  # IMDSv2 only. Hop limit 2 lets containers on the Docker bridge reach the metadata service, so the backend gets
  # S3 credentials from the instance role instead of access keys.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "${var.project}-app"
  }

  # The root volume holds the Postgres data, so a new Ubuntu AMI must not replace the instance on the next apply.
  # The running instance gets updates from unattended-upgrades instead.
  lifecycle {
    ignore_changes = [ami]
  }
}

# A fixed public address: the DuckDNS record and coturn's external-ip point at it
resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id

  tags = {
    Name = "${var.project}-app"
  }
}
