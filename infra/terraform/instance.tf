# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the single k3s host per the team's decisions (Leong Wei Zhi,
# 2026-11-05, options Q&A, recorded in docs/deployment.md): EC2
# t3.xlarge Spot, persistent request that STOPS on interruption (the disk
# and cluster data survive).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (DP7): IMDS hop limit 1
# (PR #289 review: with 2, any pod could use the instance role) and no
# External Secrets chart in cloud-init. Author review: Leong Wei Zhi,
# pending pull request review. Second round (Sinnez1): the host waits for
# the secret values and the read policy.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (HTTPS): the Elastic IP is
# gone (DP20): inbound traffic comes through the load balancer in tls.tf,
# and the auto-assigned public address serves egress and SSM only.
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
  # egress (k3s install, image pulls, SSM) only: nothing public listens on
  # this address, the load balancer is the way in (DP20). It changes when
  # Spot stops and restarts the host; the balancer targets the instance id
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
    # host processes only: pods sit one hop behind the node and must not
    # reach the instance role (DP7; secrets.sh runs on the host)
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    delete_on_termination = true
  }

  user_data = templatefile("${path.module}/cloud-init.yaml.tftpl", {
    k3s_version = var.k3s_version
    repo_url    = "https://github.com/${var.github_repo}.git"
  })

  # a new AMI or cloud-init must not replace a running host mid-demo
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = { Name = "foc-k3s" }

  # cloud-init's secrets.sh needs the secret values and the read policy to
  # exist before the host boots (PR #302 review)
  depends_on = [
    aws_secretsmanager_secret_version.app,
    aws_secretsmanager_secret_version.ghcr,
    aws_iam_role_policy.host_secrets,
  ]
}
