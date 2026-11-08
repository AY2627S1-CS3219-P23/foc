# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5.1), 2026-11-08, issue #48 (HTTPS).
# Scope: the ACM certificate for the site's hostname and the Application
# Load Balancer that terminates TLS in front of the k3s host (team
# decisions DP18 to DP20, Leong Wei Zhi, 2026-11-08, options Q&A, recorded
# in docs/deployment.md). DNS lives at FreeDNS, outside Terraform: the
# validation CNAME and the site CNAME are added by hand from the outputs.
# Author review: Leong Wei Zhi, pending pull request review.

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
# at FreeDNS and issued the certificate. No validation_record_fqdns: the
# record is not managed here. The runbook requests the certificate alone
# first (-target), so the CNAME exists before this resource starts waiting.
resource "aws_acm_certificate_validation" "site" {
  certificate_arn = aws_acm_certificate.site.arn

  timeouts {
    create = "60m"
  }
}

# --- load balancer ---------------------------------------------------------

resource "aws_lb" "site" {
  name               = "foc"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public.id, aws_subnet.public_b.id]
  # matches Traefik's idle timeout from cloud-init: the STOMP sockets the
  # gateway holds open must not be cut by the balancer
  idle_timeout = 3600
  tags         = { Name = "foc" }
}

resource "aws_lb_target_group" "traefik" {
  name        = "foc-traefik"
  port        = 80
  protocol    = "HTTP"
  target_type = "instance"
  vpc_id      = aws_vpc.main.id

  # "/" is the web pod through Traefik: a backend outage must not take the
  # SPA down with it
  health_check {
    path                = "/"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = { Name = "foc" }
}

resource "aws_lb_target_group_attachment" "host" {
  target_group_arn = aws_lb_target_group.traefik.arn
  target_id        = aws_instance.host.id
  port             = 80
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

# plain HTTP answers with a permanent redirect to https (DP19)
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
