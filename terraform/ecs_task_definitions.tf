# ECS Task Definitions for Microservices

# Use existing LabRole
data "aws_iam_role" "lab_role" {
  name = "LabRole"
}

# Product Service Task Definition
resource "aws_ecs_task_definition" "product_service" {
  family                   = "product-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "product-service"
      image     = "${data.aws_ecr_repository.product_service.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8082
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        },
        {
          name  = "PRODUCT_SERVICE_ERROR_RATE"
          value = "0.0"
        },
        # KV Database URL - Must be updated after deployment
        # This IP address is session-specific and changes on each AWS Learner Lab restart.
        # To get the correct IP after 'terraform apply':
        # 1. Wait for services to start (~3 min)
        # 2. Run: ./get-service-ips.sh
        # 3. Update this value with the leaderless-kv service IP
        # 4. Run: terraform apply
        # 5. Restart product-service
        {
          name  = "KVSTORE_LEADER_URL"
          value = "http://172.31.80.92:8090"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "product-service"
        }
      }
    }
  ])

  tags = {
    Name        = "product-service-task"
    Environment = var.environment
  }
}

# Product Service Task Definition - FAILING VERSION (for testing)
resource "aws_ecs_task_definition" "product_service_failing" {
  family                   = "product-service-failing"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "product-service-failing"
      image     = "${data.aws_ecr_repository.product_service.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8082
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        },
        {
          name  = "PRODUCT_SERVICE_ERROR_RATE"
          value = "0.5"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "product-service-failing"
        }
      }
    }
  ])

  tags = {
    Name        = "product-service-failing-task"
    Environment = var.environment
  }
}

# Shopping Cart Service Task Definition
resource "aws_ecs_task_definition" "shopping_cart_service" {
  family                   = "shopping-cart-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "shopping-cart-service"
      image     = "${data.aws_ecr_repository.shopping_cart_service.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8084
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        },
        {
          name  = "SPRING_RABBITMQ_HOST"
          value = aws_instance.rabbitmq.private_ip
        },
        {
          name  = "SPRING_RABBITMQ_PORT"
          value = "5672"
        },
        {
          name  = "SPRING_RABBITMQ_USERNAME"
          value = var.rabbitmq_username
        },
        {
          name  = "SPRING_RABBITMQ_PASSWORD"
          value = var.rabbitmq_password
        },
        # Use ALB for inter-service communication to avoid IP address management issues
        # ALB DNS name is stable and handles routing to healthy targets
        {
          name  = "SERVICES_PRODUCT_URL"
          value = "http://${aws_lb.ecommerce_alb.dns_name}"
        },
        {
          name  = "SERVICES_CREDIT_CARD_AUTHORIZER_URL"
          value = "http://${aws_lb.ecommerce_alb.dns_name}"
        },
        # KV Database still uses private IP (single instance, rarely restarts)
        # If IP changes, update this value and redeploy shopping-cart-service
        {
          name  = "KVSTORE_LEADER_URL"
          value = "http://172.31.8.47:8080"
        },
        # Warehouse Service URL - use ALB for stable DNS
        {
          name  = "SERVICES_WAREHOUSE_URL"
          value = "http://${aws_lb.ecommerce_alb.dns_name}"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "shopping-cart-service"
        }
      }
    }
  ])

  tags = {
    Name        = "shopping-cart-service-task"
    Environment = var.environment
  }
}

# Credit Card Authorizer Task Definition
resource "aws_ecs_task_definition" "credit_card_authorizer" {
  family                   = "credit-card-authorizer"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "credit-card-authorizer"
      image     = "${data.aws_ecr_repository.credit_card_authorizer.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        },
        {
          name  = "SERVER_PORT"
          value = "8080"
        }
      ] 

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "credit-card-authorizer"
        }
      }
    }
  ])

  tags = {
    Name        = "credit-card-authorizer-task"
    Environment = var.environment
  }
}

# Warehouse Service Task Definition
resource "aws_ecs_task_definition" "warehouse_service" {
  family                   = "warehouse-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "warehouse-service"
      image     = "${data.aws_ecr_repository.warehouse_service.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8083
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "production"
        },
        {
          name  = "SERVER_PORT"
          value = "8083"
        },
        {
          name  = "SPRING_RABBITMQ_HOST"
          value = aws_instance.rabbitmq.private_ip
        },
        {
          name  = "SPRING_RABBITMQ_PORT"
          value = "5672"
        },
        {
          name  = "SPRING_RABBITMQ_USERNAME"
          value = var.rabbitmq_username
        },
        {
          name  = "SPRING_RABBITMQ_PASSWORD"
          value = var.rabbitmq_password
        },
        {
          name  = "SERVICES_PRODUCT_URL"
          value = "http://${aws_lb.ecommerce_alb.dns_name}"
        },
        {
          name  = "SPRING_RABBITMQ_LISTENER_SIMPLE_CONCURRENCY"
          value = "8"
        },
        {
          name  = "SPRING_RABBITMQ_LISTENER_SIMPLE_MAX_CONCURRENCY"
          value = "16"
        },
        {
          name  = "SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH"
          value = "250"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "warehouse-service"
        }
      }
    }
  ])

  tags = {
    Name        = "warehouse-service-task"
    Environment = var.environment
  }
}
