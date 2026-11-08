# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5.1), 2026-11-09, issue #48 (EKS re-platform).
# Scope: one RDS PostgreSQL 17 instance holding the seven service databases
# (team decisions DP4 and DP16, Leong Wei Zhi, 2026-11-09, options Q&A,
# recorded in docs/deployment.md: db.t4g.micro, private subnets, reachable
# from the cluster only; the 7 roles and databases are created by the
# db-init Job in infra/k8s with the master credentials from foc/app).
# Nothing is kept at teardown (DP12): no final snapshot.
# Author review: Leong Wei Zhi, pending pull request review.

resource "random_password" "rds_master" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "main" {
  name       = "foc"
  subnet_ids = local.private_subnet_ids
  tags       = { Name = "foc" }
}

resource "aws_security_group" "rds" {
  name        = "foc-rds"
  description = "FoC database: PostgreSQL from the cluster nodes only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from the cluster"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.main.vpc_config[0].cluster_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "main" {
  identifier     = "foc"
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "postgres"
  username = "focadmin"
  password = random_password.rds_master.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  # the default postgres17 parameter group enforces TLS (rds.force_ssl);
  # pgjdbc's default sslmode=prefer negotiates it without URL changes
  auto_minor_version_upgrade = true
  backup_retention_period    = 1
  skip_final_snapshot        = true
  deletion_protection        = false
  apply_immediately          = true

  tags = { Name = "foc" }
}
