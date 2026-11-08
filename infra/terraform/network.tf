# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the network for the single instance (the sandbox account has no
# default VPC): one public subnet, an internet gateway, and the security
# group — HTTP 80 in from anywhere, nothing else (team decisions: plain HTTP
# on an Elastic IP, k3s API private, shell via SSM — Leong Wei Zhi,
# 2026-11-05, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (HTTPS): a second public
# subnet and the load balancer's security group; the host now takes port
# 80 from the load balancer only (DP19, DP20, Leong Wei Zhi, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.

resource "aws_vpc" "main" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "foc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "foc" }
}

resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.1.0/24"
  availability_zone = var.availability_zone
  tags              = { Name = "foc-public" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = { Name = "foc-public" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# second zone, for the load balancer only: an ALB needs subnets in two
# zones (DP19). The host stays in aws_subnet.public.
resource "aws_subnet" "public_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.2.0/24"
  availability_zone = var.availability_zone_b
  tags              = { Name = "foc-public-b" }
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "alb" {
  name        = "foc-alb"
  description = "FoC load balancer: HTTP (redirected) and HTTPS in, all out"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "redirected to https"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "web + gateway, TLS ends here"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "host" {
  name        = "foc-host"
  description = "FoC k3s host: HTTP in, all out"
  vpc_id      = aws_vpc.main.id

  # the host has no public listener of its own (DP20): Traefik takes plain
  # HTTP from the load balancer, which holds the certificate
  ingress {
    description     = "Traefik, from the load balancer only"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
