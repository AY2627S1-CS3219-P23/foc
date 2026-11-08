# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the platform secrets in AWS Secrets Manager, copied into k3s
# Secrets by infra/k8s/secrets.sh on the host (team decision: Secrets
# Manager — Leong Wei Zhi, 2026-11-05, options Q&A; DP7 as re-decided on
# 2026-11-08: a script copies them, no External Secrets Operator). Passwords and tokens are
# generated here; the Gmail and GHCR values come from secrets.auto.tfvars.
# recovery_window_in_days = 0 so destroy deletes them at once (teardown
# decision: nothing kept).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (DP7): header updated
# for the host-side copy; no resource changed. Author review: Leong Wei
# Zhi, pending pull request review.
# 2026-11-09, Claude Code (Fable 5.1), issue #48 (EKS re-platform, DP4, DP7,
# DP16): foc/app also carries the database address and the RDS master
# credentials, which the db-init Job uses to create the seven roles and
# databases; secrets.sh now runs in the Deploy job, not on a host.
# Author review: Leong Wei Zhi, pending pull request review.

locals {
  services = ["user", "supplier", "order", "credit", "notification", "rating", "chat"]
}

resource "random_password" "db" {
  for_each = toset(local.services)
  length   = 32
  special  = false
}

resource "random_password" "rabbitmq" {
  length  = 32
  special = false
}

# HS256 platform secret: at least 32 bytes (the services' startup check)
resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "owner_setup" {
  length  = 32
  special = false
}

resource "random_password" "chat_internal" {
  length  = 48
  special = false
}

# One JSON secret with every app value; infra/k8s/secrets.sh copies all
# keys into the Kubernetes Secret foc-secrets. DB_HOST is the RDS address
# every service connects to; the master credentials are read by the
# db-init Job only (DP16).
resource "aws_secretsmanager_secret" "app" {
  name                    = "foc/app"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode(merge(
    { for s in local.services : "${upper(s)}_DB_PASSWORD" => random_password.db[s].result },
    {
      RABBITMQ_PASSWORD   = random_password.rabbitmq.result
      JWT_SECRET          = random_password.jwt.result
      OWNER_SETUP_TOKEN   = random_password.owner_setup.result
      CHAT_INTERNAL_TOKEN = random_password.chat_internal.result
      DB_HOST             = aws_db_instance.main.address
      DB_PORT             = "5432"
      RDS_MASTER_USER     = aws_db_instance.main.username
      RDS_MASTER_PASSWORD = random_password.rds_master.result
      MAIL_USERNAME       = var.mail_username
      MAIL_PASSWORD       = var.mail_password
      MAIL_FROM           = var.mail_from
    },
  ))
}

resource "aws_secretsmanager_secret" "ghcr" {
  name                    = "foc/ghcr"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "ghcr" {
  secret_id = aws_secretsmanager_secret.ghcr.id
  secret_string = jsonencode({
    username = var.ghcr_username
    token    = var.ghcr_token
  })
}
