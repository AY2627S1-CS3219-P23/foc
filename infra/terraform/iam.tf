# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: IAM for the team's decisions (Leong Wei Zhi, 2026-11-05, options
# Q&A): the instance role (SSM for shell + deploys, Secrets Manager reads
# for the host's secrets.sh), and the GitHub Actions OIDC role that
# may only run commands on this instance through SSM.
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (DP7): the Secrets
# Manager reader is the host's secrets.sh, not External Secrets Operator;
# the policies are unchanged. Author review: Leong Wei Zhi, pending pull
# request review.

data "aws_caller_identity" "current" {}

# --- instance role ----------------------------------------------------------

resource "aws_iam_role" "host" {
  name = "foc-host"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "host_ssm" {
  role       = aws_iam_role.host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# secrets.sh and deploy.sh run on the host and read foc/* through IMDS;
# pods cannot (hop limit 1 in instance.tf, DP7)
resource "aws_iam_role_policy" "host_secrets" {
  name = "read-foc-secrets"
  role = aws_iam_role.host.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
      Resource = "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:foc/*"
    }]
  })
}

resource "aws_iam_instance_profile" "host" {
  name = "foc-host"
  role = aws_iam_role.host.name
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
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:ref:refs/heads/main"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "deploy" {
  name = "run-deploy-on-host"
  role = aws_iam_role.deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          "arn:aws:ssm:${var.region}::document/AWS-RunShellScript",
          aws_instance.host.arn,
        ]
      },
      {
        # no resource-level permissions exist for reading command results
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      },
    ]
  })
}
