# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: inputs for the single-instance deployment; defaults are the
# team's decisions (Leong Wei Zhi, 2026-11-05, options Q&A, recorded in
# docs/deployment.md). Secret values go in the git-ignored
# secrets.auto.tfvars (see secrets.auto.tfvars.example).
# Author review: Leong Wei Zhi, pending pull request review.

variable "region" {
  type    = string
  default = "ap-southeast-1"
}

variable "availability_zone" {
  type    = string
  default = "ap-southeast-1a"
}

variable "instance_type" {
  type    = string
  default = "t3.xlarge"
}

variable "root_volume_gb" {
  type    = number
  default = 40
}

variable "k3s_version" {
  type    = string
  default = "v1.36.5+k3s1"
}

variable "external_secrets_chart_version" {
  type    = string
  default = "2.13.0"
}

variable "github_repo" {
  description = "owner/name of the repository whose main branch may deploy"
  type        = string
  default     = "AY2627S1-CS3219-P23/foc"
}

# --- team-supplied secrets (secrets.auto.tfvars) ---------------------------

variable "mail_username" {
  description = "Gmail SMTP account used for OTP emails"
  type        = string
  sensitive   = true
}

variable "mail_password" {
  description = "Gmail app password for mail_username"
  type        = string
  sensitive   = true
}

variable "mail_from" {
  description = "From address on OTP emails"
  type        = string
}

variable "ghcr_username" {
  description = "GitHub user whose token pulls the images"
  type        = string
}

variable "ghcr_token" {
  description = "GitHub token with read:packages only"
  type        = string
  sensitive   = true
}
