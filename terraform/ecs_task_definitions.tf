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
      image     = "${aws_ecr_repository.product_service.repository_url}:latest"
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
      image     = "${aws_ecr_repository.product_service.repository_url}:latest"
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
      image     = "${aws_ecr_repository.shopping_cart_service.repository_url}:latest"
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
          name  = "RABBITMQ_HOST"
          value = aws_instance.rabbitmq.private_ip
        },
        {
          name  = "RABBITMQ_PORT"
          value = "5672"
        },
        {
          name  = "RABBITMQ_USERNAME"
          value = var.rabbitmq_username
        },
        {
          name  = "RABBITMQ_PASSWORD"
          value = var.rabbitmq_password
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
      image     = "${aws_ecr_repository.credit_card_authorizer.repository_url}:latest"
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
      image     = "${aws_ecr_repository.warehouse_service.repository_url}:latest"
      essential = true

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
