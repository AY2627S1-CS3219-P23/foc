# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: inputs for the deployment; defaults are the team's decisions
# (Leong Wei Zhi, options Q&A, recorded in docs/deployment.md). Secret
# values go in the git-ignored secrets.auto.tfvars (see
# secrets.auto.tfvars.example).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1): github_owner_id and github_repo_id
# for the immutable OIDC subject (PR #307 review: validated, null defaults);
# domain_name and availability_zone_b for the certificate and the load
# balancer (DP18, DP19; the hostname moved to Dynu the same day). Author
# review: Leong Wei Zhi, pending pull request review.
# 2026-11-09, Claude Code (Fable 5.1), issue #48 (EKS re-platform): the k3s
# host inputs (instance_type, root_volume_gb, k3s_version) are replaced by
# the cluster, node group, NAT instance and database inputs (DP2 to DP4,
# Leong Wei Zhi, 2026-11-09, options Q&A). Author review: Leong Wei Zhi,
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
  description = "second zone: the load balancer, the node group and the database subnet group need two"
  type        = string
  default     = "ap-southeast-1b"
}

variable "domain_name" {
  description = "the site's hostname: the ACM certificate's name and the PUBLIC_HOST repo variable; its DNS records live at Dynu (DP17)"
  type        = string
  default     = "foc23.ddnsfree.com"
}

# --- cluster (DP2, DP3) -----------------------------------------------------

variable "cluster_name" {
  description = "EKS cluster name; the CLUSTER_NAME repo variable"
  type        = string
  default     = "foc"
}

variable "kubernetes_version" {
  description = "EKS Kubernetes version (the region's default on 2026-11-09)"
  type        = string
  default     = "1.37"
}

variable "node_instance_type" {
  description = "Spot node type; x86 because the images are built for linux/amd64 only"
  type        = string
  default     = "t3.medium"
}

variable "node_count" {
  description = "Spot nodes running at rest (the Spot node group's minimum and initial desired size); one on-demand node comes on top"
  type        = number
  default     = 2
}

variable "node_max" {
  description = "the node group's maximum; the third node is added by raising the desired size (infra/README.md)"
  type        = number
  default     = 3
}

variable "stable_node_instance_type" {
  description = "the one on-demand node in zone a that RabbitMQ and Redis select"
  type        = string
  default     = "t3.medium"
}

variable "nat_instance_type" {
  description = "the NAT instance (fck-nat, arm64), on demand"
  type        = string
  default     = "t4g.nano"
}

# --- database (DP4) ---------------------------------------------------------

variable "db_instance_class" {
  description = "RDS PostgreSQL instance class holding the seven service databases"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "RDS PostgreSQL version, a concrete minor (the region's default 17.x on 2026-11-09: `aws rds describe-db-engine-versions --engine postgres --engine-version 17 --default-only`)"
  type        = string
  default     = "17.9"
}

# --- GitHub ------------------------------------------------------------------
# GitHub's "immutable subject" OIDC setting (on for this repository) puts
# the owner and repository ids into the token's sub claim:
#   repo:<owner>@<owner id>/<name>@<repo id>:ref:refs/heads/main

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
