# KV Database ECS Configuration (Leader-Follower)
# This file defines the KV Database service for Shopping Cart (write-heavy workload)
#
# WORKLOAD ASSUMPTIONS (Shopping Cart):
# - WRITE-HEAVY: Customers frequently add/remove items, update quantities
# - READ-IMPORTANT: Checkout requires accurate cart state (strong consistency)
# - Ratio estimate: 40% reads, 60% writes
#
# CONFIGURATION RATIONALE:
# - W=1: Fast writes - only leader acknowledges (async replication to followers)
#        This optimizes for the frequent add-to-cart operations
# - R=N: Strong read consistency - read from all nodes and return latest
#        Critical for checkout to ensure accurate cart totals
# - This follows the "write-optimized" pattern with eventual consistency
#   but strong reads when needed (checkout)
#
# TRADE-OFF: Cart updates are fast, but there's a brief window where
# followers may have stale data. Checkout reads from all nodes to get
# the most recent state.

# Task Definition
resource "aws_ecs_task_definition" "kv_database" {
  family                   = "kv-database"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "kv-database"
      image     = "${data.aws_ecr_repository.kv_database.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SERVER_PORT"
          value = "8080"
        },
        # N=1 for single-node deployment (would be N=5 in production)
        {
          name  = "N_VALUE"
          value = "1"
        },
        # R=N (all nodes) - Strong read consistency for checkout accuracy
        # In production with 5 nodes, this would be R=5
        {
          name  = "R_VALUE"
          value = "1"
        },
        # W=1 - Fast writes, async replication for quick add-to-cart
        {
          name  = "W_VALUE"
          value = "1"
        },
        {
          name  = "KVSTORE_ROLE"
          value = "leader"
        },
        # W=1 - Optimized for write-heavy shopping cart operations
        {
          name  = "KVSTORE_WRITE_QUORUM"
          value = "1"
        },
        # R=N - Strong consistency for checkout (read from all nodes)
        # In production with 5 nodes, this would be R=5
        {
          name  = "KVSTORE_READ_QUORUM"
          value = "1"
        },
        # Simulated replication delay for realistic distributed behavior
        {
          name  = "KVSTORE_REPLICATION_DELAY_MS"
          value = "100"
        },
        {
          name  = "KVSTORE_FOLLOWERS"
          value = ""
        },
        {
          name  = "KVSTORE_ALL_NODES"
          value = "http://localhost:8080"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "kv-database"
        }
      }
    }
  ])

  tags = {
    Name        = "kv-database-task"
    Environment = var.environment
  }
}

# ECS Service
resource "aws_ecs_service" "kv_database" {
  name            = "kv-database"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.kv_database.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [aws_security_group.kv_database_sg.id]
    assign_public_ip = true
  }

  tags = {
    Name        = "kv-database-service"
    Environment = var.environment
  }
}

# Security Group
resource "aws_security_group" "kv_database_sg" {
  name        = "kv-database-sg"
  description = "Security group for KV Database"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTP from Product Service"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.product_service_sg.id]
  }

  ingress {
    description     = "HTTP from Shopping Cart Service"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.shopping_cart_service_sg.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "kv-database-sg"
    Environment = var.environment
  }
}

# Output the service details
output "kv_database_service_name" {
  description = "Name of the KV Database ECS service"
  value       = aws_ecs_service.kv_database.name
}
