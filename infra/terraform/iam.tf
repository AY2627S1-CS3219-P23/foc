# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the GitHub Actions OIDC deploy role (team decision, Leong Wei Zhi,
# 2026-11-05, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), first deploy and PR #307 review: the
# trust accepts GitHub's immutable subject only (team decision, Leong Wei
# Zhi); null ids fall back to this repository's. Author review: Leong Wei
# Zhi, pending pull request review.
# 2026-11-09, Claude Code (Fable 5.1), issue #48 (EKS re-platform, DP7 and
# DP8): the k3s host role is gone with the host. The deploy role no longer
# runs SSM commands; it describes the cluster (kubectl through an access
# entry, eks.tf) and reads foc/* so secrets.sh can run in the Deploy job.
# Author review: Leong Wei Zhi, pending pull request review.

data "aws_caller_identity" "current" {}

locals {
  github_owner     = split("/", var.github_repo)[0]
  github_repo_name = split("/", var.github_repo)[1]
  # null ids mean the default github_repo's (validated in variables.tf)
  github_owner_id = coalesce(var.github_owner_id, 325983746)
  github_repo_id  = coalesce(var.github_repo_id, 1360023737)
}

# --- GitHub Actions deploy role ----------------------------------------------

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "deploy" {
  name = "foc-github-deploy"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          # the subject GitHub sends with the repository's "immutable
          # subject" setting on (owner and repo ids included); the plain
          # repo:<owner>/<name> form is not trusted
          "token.actions.githubusercontent.com:sub" = "repo:${local.github_owner}@${local.github_owner_id}/${local.github_repo_name}@${local.github_repo_id}:ref:refs/heads/main"
        }
      }
    }]
  })
}

# What the Deploy job does with AWS itself: describe the cluster to write a
# kubeconfig, and read the two secrets for secrets.sh. Everything else goes
# through the Kubernetes API under the access entry in eks.tf
resource "aws_iam_role_policy" "deploy" {
  name = "deploy-to-cluster"
  role = aws_iam_role.deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "eks:DescribeCluster"
        Resource = aws_eks_cluster.main.arn
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
        Resource = "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:foc/*"
      },
    ]
  })
}
