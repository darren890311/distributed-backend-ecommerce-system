# Leaderless KV Database ECS Configuration
# This file defines the Leaderless KV Database service for read-heavy Product Service
#
# WORKLOAD ASSUMPTIONS (Product Catalog):
# - READ-HEAVY: Customers frequently browse products (view details, search, list)
# - WRITE-LIGHT: Products are added/updated infrequently by admins
# - Ratio estimate: 95% reads, 5% writes
#
# CONFIGURATION RATIONALE:
# - R=1: Fast reads - only need to read from one node (optimized for browsing)
# - W=N: Strong write consistency - all nodes must acknowledge writes
#        This ensures product updates are immediately visible everywhere
# - This follows the "read-optimized" pattern for catalog-style data

# Task Definition
resource "aws_ecs_task_definition" "leaderless_kv" {
  family                   = "leaderless-kv"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "leaderless-kv"
      image     = "${data.aws_ecr_repository.leaderless_kv.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8090
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SERVER_PORT"
          value = "8090"
        },
        {
          name  = "CLUSTER_NODE_ID"
          value = "1"
        },
        # N=1 for single-node deployment (would be N=5 in production with 5 nodes)
        {
          name  = "CLUSTER_TOTAL_NODES"
          value = "1"
        },
        # W=N (all nodes) - Strong write consistency for product updates
        # In production with 5 nodes, this would be W=5
        {
          name  = "CLUSTER_WRITE_QUORUM"
          value = "1"
        },
        # R=1 - Fast reads, optimized for product browsing
        {
          name  = "CLUSTER_READ_QUORUM"
          value = "1"
        },
        # Simulated network delay for realistic distributed system behavior
        {
          name  = "CLUSTER_NETWORK_DELAY_MS"
          value = "50"
        },
        {
          name  = "CLUSTER_READ_DELAY_MS"
          value = "10"
        },
        {
          name  = "CLUSTER_PEERS"
          value = ""
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "leaderless-kv"
        }
      }
    }
  ])

  tags = {
    Name        = "leaderless-kv-task"
    Environment = var.environment
  }
}

# ECS Service
resource "aws_ecs_service" "leaderless_kv" {
  name            = "leaderless-kv"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.leaderless_kv.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [aws_security_group.leaderless_kv_sg.id]
    assign_public_ip = true
  }

  tags = {
    Name        = "leaderless-kv-service"
    Environment = var.environment
  }
}

# Security Group
resource "aws_security_group" "leaderless_kv_sg" {
  name        = "leaderless-kv-sg"
  description = "Security group for Leaderless KV Database"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTP from Product Service"
    from_port       = 8090
    to_port         = 8090
    protocol        = "tcp"
    security_groups = [aws_security_group.product_service_sg.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "leaderless-kv-sg"
    Environment = var.environment
  }
}

# Output the service details
output "leaderless_kv_service_name" {
  description = "Name of the Leaderless KV ECS service"
  value       = aws_ecs_service.leaderless_kv.name
}
