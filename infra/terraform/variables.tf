# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: inputs for the single-instance deployment; defaults are the
# team's decisions (Leong Wei Zhi, 2026-11-05, options Q&A, recorded in
# docs/deployment.md). Secret values go in the git-ignored
# secrets.auto.tfvars (see secrets.auto.tfvars.example).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (DP7): the External
# Secrets Operator chart version is gone with the operator. Author review:
# Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), first deploy: github_owner_id and
# github_repo_id for the immutable OIDC subject. Author review: Leong Wei
# Zhi, pending pull request review.

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

# GitHub's "immutable subject" OIDC setting (on for this repository) puts
# the owner and repository ids into the token's sub claim:
#   repo:<owner>@<owner id>/<name>@<repo id>:ref:refs/heads/main
# The deploy role trusts that form as well as the plain one.
variable "github_owner_id" {
  description = "numeric id of the GitHub organisation that owns github_repo"
  type        = number
  default     = 325983746
}

variable "github_repo_id" {
  description = "numeric id of the repository github_repo"
  type        = number
  default     = 1360023737
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
