# The default VPC and its public subnets: no own VPC, no NAT gateway (INFRASTRUCTURE_DECISIONS.md, section 2)
data "aws_vpc" "default" {
  default = true
}

# One security group for the instance. Port 22 is deliberately missing: a shell comes from SSM Session Manager.
# Postgres (5432) is never published by Docker Compose, so it needs no rule either.
resource "aws_security_group" "app" {
  name        = "${var.project}-app"
  description = "SyncChord demo: Caddy (80, 443) and coturn (3478, relay range); no SSH"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project}-app"
  }
}

locals {
  # Port 80 only serves the Let's Encrypt HTTP-01 challenge and the redirect to HTTPS (Caddy)
  ingress_rules = {
    http     = { protocol = "tcp", from = 80, to = 80, description = "Caddy: ACME challenge and redirect to HTTPS" }
    https    = { protocol = "tcp", from = 443, to = 443, description = "Caddy: app, API and WebSocket" }
    turn_udp = { protocol = "udp", from = 3478, to = 3478, description = "coturn: STUN/TURN over UDP" }
    turn_tcp = { protocol = "tcp", from = 3478, to = 3478, description = "coturn: TURN over TCP" }
    turn_relay = {
      protocol    = "udp"
      from        = var.turn_relay_port_min
      to          = var.turn_relay_port_max
      description = "coturn: UDP relay ports (min-port..max-port)"
    }
  }
}

resource "aws_vpc_security_group_ingress_rule" "app" {
  for_each = local.ingress_rules

  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = each.value.protocol
  from_port         = each.value.from
  to_port           = each.value.to
  description       = each.value.description

  tags = {
    Name = "${var.project}-${each.key}"
  }
}

# Outbound is open: apt, Docker Hub/GHCR pulls, Let's Encrypt, S3, SSM, and coturn relaying to peers
resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "All outbound traffic"
}
