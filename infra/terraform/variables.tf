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
# 2026-11-08, Claude Code (Fable 5.1), PR #307 review: github_repo is
# validated as owner/name, and the two ids default to null and must be set
# whenever github_repo is overridden. Author review: Leong Wei Zhi, pending
# pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (HTTPS): domain_name and
# availability_zone_b for the ACM certificate and the load balancer (DP18,
# DP19). Author review: Leong Wei Zhi, pending pull request review.
# Same day: the hostname moved to Dynu (DP18 revised; FreeDNS restricts
# CNAME records on its shared domains). Author review: Leong Wei Zhi,
# pending pull request review.

variable "region" {
  type    = string
  default = "ap-southeast-1"
}

variable "availability_zone" {
  type    = string
  default = "ap-southeast-1a"
}

variable "availability_zone_b" {
  description = "second zone, for the load balancer only (an ALB needs two subnets in two zones)"
  type        = string
  default     = "ap-southeast-1b"
}

variable "domain_name" {
  description = "the site's hostname: the ACM certificate's name and the PUBLIC_HOST repo variable; its DNS records live at Dynu (DP18)"
  type        = string
  default     = "foc23.ddnsfree.com"
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
  description = "numeric id of the GitHub organisation that owns github_repo; null means this repository's own (325983746), so set it whenever github_repo is overridden"
  type        = number
  default     = null
  validation {
    condition     = var.github_owner_id != null || var.github_repo == "AY2627S1-CS3219-P23/foc"
    error_message = "github_owner_id must be set when github_repo is not AY2627S1-CS3219-P23/foc."
  }
}

variable "github_repo_id" {
  description = "numeric id of the repository github_repo; null means this repository's own (1360023737), so set it whenever github_repo is overridden"
  type        = number
  default     = null
  validation {
    condition     = var.github_repo_id != null || var.github_repo == "AY2627S1-CS3219-P23/foc"
    error_message = "github_repo_id must be set when github_repo is not AY2627S1-CS3219-P23/foc."
  }
}

variable "github_repo" {
  description = "owner/name of the repository whose main branch may deploy"
  type        = string
  default     = "AY2627S1-CS3219-P23/foc"
  validation {
    condition     = length(split("/", var.github_repo)) == 2
    error_message = "github_repo must be <owner>/<name>."
  }
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
