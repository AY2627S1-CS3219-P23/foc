# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the VPC for the deployment (the sandbox account has no default
# VPC): two public subnets, an internet gateway and the load balancer's
# security group (team decisions, Leong Wei Zhi, options Q&A, recorded in
# docs/deployment.md).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (HTTPS): the second public
# subnet and the load balancer's security group. Author review: Leong Wei
# Zhi, pending pull request review.
# 2026-11-09, Claude Code (Fable 5.1), issue #48 (EKS re-platform, DP3 and
# DP4): the k3s host's security group is gone; private subnets for the
# nodes and the database (zones a and b, plus zone c for the Spot group),
# routed to the NAT instance (nat.tf); the cluster
# security group takes Traefik's NodePort from the load balancer only.
# Author review: Leong Wei Zhi, pending pull request review.
# PR #316 review (Sinnez1): a third public subnet, in zone c, so the load
# balancer reaches the Spot nodes there (team decision, Leong Wei Zhi,
# options Q&A). Author review: Leong Wei Zhi, pending pull request review.

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

# --- public: load balancer and NAT instance --------------------------------

resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.1.0/24"
  availability_zone = var.availability_zone
  tags              = { Name = "foc-public" }
}

# second zone: an ALB needs subnets in two zones (DP18)
resource "aws_subnet" "public_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.2.0/24"
  availability_zone = var.availability_zone_b
  tags              = { Name = "foc-public-b" }
}

# third zone: a load balancer sends no traffic to targets in a zone it is
# not enabled in, and the Spot group spans zone c too (DP3, PR #316 review)
resource "aws_subnet" "public_c" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.3.0/24"
  availability_zone = var.availability_zone_c
  tags              = { Name = "foc-public-c" }
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

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_c" {
  subnet_id      = aws_subnet.public_c.id
  route_table_id = aws_route_table.public.id
}

# --- private: nodes and database (DP4) --------------------------------------
# No route in from the internet. Outbound traffic (image pulls, the EKS and
# EC2 APIs when a node joins, Gmail) leaves through the NAT instance.

resource "aws_subnet" "private" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.11.0/24"
  availability_zone = var.availability_zone
  tags              = { Name = "foc-private" }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.12.0/24"
  availability_zone = var.availability_zone_b
  tags              = { Name = "foc-private-b" }
}

# third zone for the Spot node group only (DP3): one more Spot capacity
# pool. The on-demand node, its volumes and the NAT instance stay in zone a
resource "aws_subnet" "private_c" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.20.13.0/24"
  availability_zone = var.availability_zone_c
  tags              = { Name = "foc-private-c" }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block           = "0.0.0.0/0"
    network_interface_id = aws_network_interface.nat.id
  }
  tags = { Name = "foc-private" }
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_c" {
  subnet_id      = aws_subnet.private_c.id
  route_table_id = aws_route_table.private.id
}

# --- security groups --------------------------------------------------------

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

# The nodes carry the cluster security group EKS creates. Traefik's NodePort
# is reachable from the load balancer only (DP15); nothing else is opened
resource "aws_vpc_security_group_ingress_rule" "nodes_from_alb" {
  security_group_id            = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = local.traefik_node_port
  to_port                      = local.traefik_node_port
  ip_protocol                  = "tcp"
  description                  = "Traefik NodePort, from the load balancer only"
  tags                         = { Name = "foc-traefik-nodeport" }
}
