# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the single k3s host per the team's decisions (Leong Wei Zhi,
# 2026-11-05, options Q&A, recorded in docs/architecture.md): EC2
# t3.xlarge Spot, persistent request that STOPS on interruption (the disk
# and cluster data survive), Elastic IP for a stable URL (the web bundle
# has it baked in).
# Author review: Leong Wei Zhi, pending pull request review.

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_instance" "host" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.host.id]
  iam_instance_profile   = aws_iam_instance_profile.host.name
  # cloud-init downloads k3s before the Elastic IP is associated; the EIP
  # replaces this address once attached
  associate_public_ip_address = true

  instance_market_options {
    market_type = "spot"
    spot_options {
      spot_instance_type             = "persistent"
      instance_interruption_behavior = "stop"
    }
  }

  # standard mode: no CPU surplus charges beyond the Spot price
  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_tokens = "required"
    # pods (External Secrets Operator) sit one hop behind the node
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    delete_on_termination = true
  }

  user_data = templatefile("${path.module}/cloud-init.yaml.tftpl", {
    k3s_version                    = var.k3s_version
    external_secrets_chart_version = var.external_secrets_chart_version
    repo_url                       = "https://github.com/${var.github_repo}.git"
  })

  # a new AMI or cloud-init must not replace a running host mid-demo
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = { Name = "foc-k3s" }
}

resource "aws_eip" "host" {
  domain = "vpc"
  tags   = { Name = "foc" }
}

resource "aws_eip_association" "host" {
  instance_id   = aws_instance.host.id
  allocation_id = aws_eip.host.id
}
