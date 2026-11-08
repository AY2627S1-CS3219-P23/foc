# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5.1), 2026-11-09, issue #48 (EKS re-platform).
# Scope: the NAT instance that is the private subnets' only way out (team
# decision DP4, Leong Wei Zhi, 2026-11-09, options Q&A, recorded in
# docs/deployment.md: private nodes, a fck-nat t4g.nano on demand rather
# than a NAT gateway). One network interface with source/destination
# checking off holds the Elastic IP, so a stop/start or EC2 auto-recovery
# keeps the address and the private route table needs no change.
# Author review: Leong Wei Zhi, pending pull request review.

# fck-nat: Amazon Linux 2023 with the masquerade rule preconfigured
data "aws_ami" "fck_nat" {
  most_recent = true
  owners      = ["568608671756"]
  filter {
    name   = "name"
    values = ["fck-nat-al2023-*-arm64-ebs"]
  }
}

resource "aws_security_group" "nat" {
  name        = "foc-nat"
  description = "FoC NAT instance: everything in from the VPC, all out"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "forwarded traffic from the private subnets"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [aws_vpc.main.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# the route target: a NAT must accept packets not addressed to itself
resource "aws_network_interface" "nat" {
  subnet_id         = aws_subnet.public.id
  security_groups   = [aws_security_group.nat.id]
  source_dest_check = false
  tags              = { Name = "foc-nat" }
}

# the cluster's one fixed outbound address
resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = { Name = "foc-nat" }
}

resource "aws_eip_association" "nat" {
  allocation_id        = aws_eip.nat.id
  network_interface_id = aws_network_interface.nat.id
}

# SSM only: a shell for troubleshooting, no inbound port
resource "aws_iam_role" "nat" {
  name = "foc-nat"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "nat_ssm" {
  role       = aws_iam_role.nat.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "nat" {
  name = "foc-nat"
  role = aws_iam_role.nat.name
}

resource "aws_instance" "nat" {
  ami                  = data.aws_ami.fck_nat.id
  instance_type        = var.nat_instance_type
  iam_instance_profile = aws_iam_instance_profile.nat.name

  network_interface {
    network_interface_id = aws_network_interface.nat.id
    device_index         = 0
  }

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 8
    encrypted             = true
    delete_on_termination = true
  }

  # a newer image must not replace the cluster's egress path mid-demo
  lifecycle {
    ignore_changes = [ami]
  }

  tags = { Name = "foc-nat" }
}
