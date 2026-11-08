# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: values the runbook copies into the GitHub repo variables and the
# owner-setup step (infra/README.md).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (HTTPS): public_host is the
# hostname (DP18, DP21); alb_dns_name and acm_validation_record are the two
# CNAME records to add at FreeDNS by hand.
# Author review: Leong Wei Zhi, pending pull request review.

output "public_host" {
  description = "PUBLIC_HOST repo variable; the site is https://<this>"
  value       = var.domain_name
}

output "alb_dns_name" {
  description = "destination of the <public_host> CNAME record at FreeDNS"
  value       = aws_lb.site.dns_name
}

output "acm_validation_record" {
  description = "CNAME record to add at FreeDNS so ACM issues the certificate"
  value = {
    name  = one(aws_acm_certificate.site.domain_validation_options).resource_record_name
    value = one(aws_acm_certificate.site.domain_validation_options).resource_record_value
  }
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
