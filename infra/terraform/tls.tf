# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5.1), 2026-11-08, issue #48 (HTTPS).
# Scope: the ACM certificate for the site's hostname and the Application
# Load Balancer that terminates TLS (team decisions, Leong Wei Zhi,
# 2026-11-08, options Q&A, recorded in docs/deployment.md). DNS lives at
# Dynu, outside Terraform: the validation CNAME and the site CNAME are added
# by hand from the outputs.
# Author review: Leong Wei Zhi, pending pull request review.
# PR #310 review (Copilot): the validation resource names the record FQDNs
# it expects, taken from the certificate itself. Second comment (Claude
# reviewer): the balancer's idle timeout sits below Traefik's. Same day:
# the DNS provider is Dynu.
# 2026-11-09, Claude Code (Fable 5.1), issue #48 (EKS re-platform, DP15):
# the target is no longer one instance on port 80 but the node group's
# Auto Scaling group on Traefik's NodePort; the target group is replaced
# (a port change forces it) under a new name. Certificate, listeners and
# the Dynu records are unchanged. Author review: Leong Wei Zhi, pending
# pull request review.

# --- certificate -----------------------------------------------------------

resource "aws_acm_certificate" "site" {
  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = { Name = "foc" }
}

# Waits until ACM has seen the validation CNAME (output acm_validation_record)
# at Dynu and issued the certificate. The record is not managed here, so
# the expected FQDNs come from the certificate's own validation options.
# The runbook requests the certificate alone first (-target), so the CNAME
# exists before this resource starts waiting.
resource "aws_acm_certificate_validation" "site" {
  certificate_arn         = aws_acm_certificate.site.arn
  validation_record_fqdns = [for o in aws_acm_certificate.site.domain_validation_options : o.resource_record_name]

  timeouts {
    create = "60m"
  }
}

# --- load balancer ---------------------------------------------------------

resource "aws_lb" "site" {
  name               = "foc"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  # every zone a node can be in (DP3): targets in a zone the balancer is
  # not enabled in are registered but never sent traffic
  subnets = [aws_subnet.public.id, aws_subnet.public_b.id, aws_subnet.public_c.id]
  # below Traefik's 3600 s idle timeout (infra/helm/traefik-values.yaml), so
  # the balancer closes an idle keep-alive connection before the backend
  # does (AWS guidance; PR #310 review). The STOMP sockets the gateway
  # holds open are kept alive by heartbeats, well inside this
  idle_timeout = 3500
  tags         = { Name = "foc" }
}

# Every node answers Traefik's NodePort; both node groups' Auto Scaling
# groups register and deregister nodes as they are replaced
resource "aws_lb_target_group" "traefik" {
  name        = "foc-traefik-np"
  port        = local.traefik_node_port
  protocol    = "HTTP"
  target_type = "instance"
  vpc_id      = aws_vpc.main.id

  # "/" is the web pod through Traefik: a backend outage must not take the
  # SPA down with it
  health_check {
    path                = "/"
    port                = "traffic-port"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # the https listener points at this group: the new one must exist before
  # the old one goes
  lifecycle {
    create_before_destroy = true
  }

  tags = { Name = "foc" }
}

resource "aws_autoscaling_attachment" "nodes" {
  autoscaling_group_name = aws_eks_node_group.main.resources[0].autoscaling_groups[0].name
  lb_target_group_arn    = aws_lb_target_group.traefik.arn
}

resource "aws_autoscaling_attachment" "stable_nodes" {
  autoscaling_group_name = aws_eks_node_group.stable.resources[0].autoscaling_groups[0].name
  lb_target_group_arn    = aws_lb_target_group.traefik.arn
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.site.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.site.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.traefik.arn
  }
}

# plain HTTP answers with a permanent redirect to https (DP18)
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.site.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      protocol    = "HTTPS"
      port        = "443"
      status_code = "HTTP_301"
    }
  }
}
