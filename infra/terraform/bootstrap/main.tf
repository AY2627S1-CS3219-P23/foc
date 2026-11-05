# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the S3 bucket that holds the main stack's Terraform state, per the
# team's decisions (Leong Wei Zhi, 2026-11-05, options Q&A, recorded in
# docs/architecture.md): Terraform with S3 state, nothing kept on teardown.
# Author review: Leong Wei Zhi, pending pull request review.
#
# Applied once, with local state (this directory's terraform.tfstate is
# git-ignored). Destroy it last, after the main stack.

terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

variable "region" {
  type    = string
  default = "ap-southeast-1"
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { project = "foc" }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "state" {
  bucket = "foc-tfstate-${data.aws_caller_identity.current.account_id}"
  # nothing is kept after the deployment ends (teardown decision)
  force_destroy = true
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "state_bucket" {
  value = aws_s3_bucket.state.bucket
}
