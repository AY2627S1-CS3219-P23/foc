# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: values the runbook copies into the GitHub repo variables and the
# owner-setup step (infra/README.md).
# Author review: Leong Wei Zhi, pending pull request review.

output "public_host" {
  description = "PUBLIC_HOST repo variable; the site is http://<this>"
  value       = aws_eip.host.public_ip
}

output "instance_id" {
  description = "INSTANCE_ID repo variable"
  value       = aws_instance.host.id
}

output "deploy_role_arn" {
  description = "AWS_ROLE_ARN repo variable"
  value       = aws_iam_role.deploy.arn
}

output "owner_setup_token" {
  description = "OWNER_SETUP_TOKEN for the first-owner setup screen"
  value       = random_password.owner_setup.result
  sensitive   = true
}
