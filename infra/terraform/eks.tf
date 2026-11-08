# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5.1), 2026-11-09, issue #48 (EKS re-platform).
# Scope: the EKS cluster and its Spot node group per the team's decisions
# (Leong Wei Zhi, 2026-11-09, options Q&A, recorded in docs/deployment.md):
# DP2 EKS, DP3 t3.medium nodes in the private subnets: two Spot (max 3) in
# two zones plus one on-demand in zone a that RabbitMQ and Redis select
# (PR #316 review, re-decided 2026-11-09), DP8 cluster-admin access entry for the GitHub deploy role, DP9
# metrics-server for the HPAs, the EBS CSI driver for the RabbitMQ and
# Redis volumes (the only workload holding an AWS role, through Pod
# Identity). IMDS hop limit 1 on the nodes carries DP7's posture: pods
# cannot use the node role.
# Author review: Leong Wei Zhi, pending pull request review.

locals {
  private_subnet_ids = [aws_subnet.private.id, aws_subnet.private_b.id]
  # Traefik's NodePort (infra/helm/traefik-values.yaml); the load balancer's
  # target group and the cluster security group rule use the same number
  traefik_node_port = 30080
}

# --- control plane ----------------------------------------------------------

resource "aws_iam_role" "cluster" {
  name = "foc-eks-cluster"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  version  = var.kubernetes_version
  role_arn = aws_iam_role.cluster.arn

  # the API endpoint stays public for GitHub Actions' kubectl; the nodes
  # reach it privately without crossing the NAT instance
  vpc_config {
    subnet_ids              = local.private_subnet_ids
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  # access entries only (no aws-auth ConfigMap); the role that runs the
  # apply becomes cluster admin, the deploy role is added below
  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = true
  }

  # vpc-cni, kube-proxy and CoreDNS as EKS installs them
  bootstrap_self_managed_addons = true

  tags = { Name = "foc" }

  depends_on = [aws_iam_role_policy_attachment.cluster]
}

# --- nodes ------------------------------------------------------------------

resource "aws_iam_role" "node" {
  name = "foc-eks-node"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "node" {
  for_each = toset([
    "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy",
    "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
  ])
  role       = aws_iam_role.node.name
  policy_arn = each.value
}

resource "aws_launch_template" "node" {
  name_prefix            = "foc-node-"
  update_default_version = true

  # pods sit one hop behind the node and must not reach the node role
  # (DP7); aws-node runs on the host network, so the CNI is unaffected
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 20
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  tag_specifications {
    resource_type = "instance"
    tags          = { Name = "foc-node" }
  }
}

resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "foc-spot"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = local.private_subnet_ids
  capacity_type   = "SPOT"
  instance_types  = [var.node_instance_type]
  labels          = { "foc.dev/pool" = "spot" }

  scaling_config {
    desired_size = var.node_count
    min_size     = var.node_count
    max_size     = var.node_max
  }

  update_config {
    max_unavailable = 1
  }

  launch_template {
    id      = aws_launch_template.node.id
    version = aws_launch_template.node.latest_version
  }

  lifecycle {
    # the third node is added by raising the desired size outside Terraform
    # (infra/README.md, scaling); an apply must not undo that
    ignore_changes = [scaling_config[0].desired_size]
  }

  tags = { Name = "foc-spot" }

  # nodes bootstrap through the NAT instance (ECR pulls, EC2 API)
  depends_on = [
    aws_iam_role_policy_attachment.node,
    aws_route_table_association.private,
    aws_route_table_association.private_b,
    aws_instance.nat,
  ]
}

# One on-demand node in zone a (DP3, 2026-11-09): RabbitMQ and Redis select
# it by label, so their EBS volumes, bound to that zone, always have a node
# to attach to, and a Spot squeeze never takes the broker or the logout
# denylist. Not tainted: application pods may land here too
resource "aws_eks_node_group" "stable" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "foc-stable"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = [aws_subnet.private.id]
  capacity_type   = "ON_DEMAND"
  instance_types  = [var.stable_node_instance_type]
  labels          = { "foc.dev/pool" = "stable" }

  scaling_config {
    desired_size = 1
    min_size     = 1
    max_size     = 1
  }

  update_config {
    max_unavailable = 1
  }

  launch_template {
    id      = aws_launch_template.node.id
    version = aws_launch_template.node.latest_version
  }

  tags = { Name = "foc-stable" }

  depends_on = [
    aws_iam_role_policy_attachment.node,
    aws_route_table_association.private,
    aws_instance.nat,
  ]
}

# --- add-ons ----------------------------------------------------------------

data "aws_eks_addon_version" "pod_identity" {
  addon_name         = "eks-pod-identity-agent"
  kubernetes_version = aws_eks_cluster.main.version
  most_recent        = true
}

data "aws_eks_addon_version" "metrics_server" {
  addon_name         = "metrics-server"
  kubernetes_version = aws_eks_cluster.main.version
  most_recent        = true
}

data "aws_eks_addon_version" "ebs_csi" {
  addon_name         = "aws-ebs-csi-driver"
  kubernetes_version = aws_eks_cluster.main.version
  most_recent        = true
}

resource "aws_eks_addon" "pod_identity" {
  cluster_name                = aws_eks_cluster.main.name
  addon_name                  = "eks-pod-identity-agent"
  addon_version               = data.aws_eks_addon_version.pod_identity.version
  resolve_conflicts_on_update = "OVERWRITE"
  depends_on                  = [aws_eks_node_group.main, aws_eks_node_group.stable]
}

# the HorizontalPodAutoscalers read CPU from it (k3s shipped it built in)
resource "aws_eks_addon" "metrics_server" {
  cluster_name                = aws_eks_cluster.main.name
  addon_name                  = "metrics-server"
  addon_version               = data.aws_eks_addon_version.metrics_server.version
  resolve_conflicts_on_update = "OVERWRITE"
  depends_on                  = [aws_eks_node_group.main]
}

# EBS CSI driver: gp3 volumes for the RabbitMQ and Redis StatefulSets. Its
# controller is the one pod with an AWS role, bound to its service account
resource "aws_iam_role" "ebs_csi" {
  name = "foc-ebs-csi"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "pods.eks.amazonaws.com" }
      Action    = ["sts:AssumeRole", "sts:TagSession"]
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ebs_csi" {
  role       = aws_iam_role.ebs_csi.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

resource "aws_eks_pod_identity_association" "ebs_csi" {
  cluster_name    = aws_eks_cluster.main.name
  namespace       = "kube-system"
  service_account = "ebs-csi-controller-sa"
  role_arn        = aws_iam_role.ebs_csi.arn
  depends_on      = [aws_eks_addon.pod_identity]
}

resource "aws_eks_addon" "ebs_csi" {
  cluster_name                = aws_eks_cluster.main.name
  addon_name                  = "aws-ebs-csi-driver"
  addon_version               = data.aws_eks_addon_version.ebs_csi.version
  resolve_conflicts_on_update = "OVERWRITE"
  depends_on                  = [aws_eks_pod_identity_association.ebs_csi]
}

# --- deploy access (DP8) -----------------------------------------------------
# The GitHub deploy role is cluster admin: the Deploy job installs Traefik
# and applies the app namespace itself

resource "aws_eks_access_entry" "deploy" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = aws_iam_role.deploy.arn
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "deploy" {
  cluster_name  = aws_eks_cluster.main.name
  principal_arn = aws_iam_role.deploy.arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
  access_scope {
    type = "cluster"
  }
  depends_on = [aws_eks_access_entry.deploy]
}
