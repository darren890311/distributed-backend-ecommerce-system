# ECS Services for Microservices

# Product Service - Normal Instance
resource "aws_ecs_service" "product_service" {
  name            = "product-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.product_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # Allow Spring Boot time to start before health checks begin
  health_check_grace_period_seconds = 60

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [
      aws_security_group.product_service_sg.id,
      aws_security_group.inter_service_sg.id
    ]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.product_service_tg.arn
    container_name   = "product-service"
    container_port   = 8082
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name        = "product-service"
    Environment = var.environment
  }
}

# Product Service - Failing Instance (for testing ALB)
resource "aws_ecs_service" "product_service_failing" {
  name            = "product-service-failing"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.product_service_failing.arn
  desired_count   = 0  # Disabled - only enable for fault tolerance testing
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [aws_security_group.product_service_sg.id]
    assign_public_ip = true
  }


  load_balancer {
    target_group_arn = aws_lb_target_group.product_service_tg.arn
    container_name   = "product-service-failing"
    container_port   = 8082
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name        = "product-service-failing"
    Environment = var.environment
  }
}

# Shopping Cart Service
resource "aws_ecs_service" "shopping_cart_service" {
  name            = "shopping-cart-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.shopping_cart_service.arn
  desired_count   = 2  # Increased from 1 for write-heavy workload
  launch_type     = "FARGATE"

  # Allow Spring Boot time to start before health checks begin
  health_check_grace_period_seconds = 60

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [
      aws_security_group.shopping_cart_service_sg.id ]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.shopping_cart_service_tg.arn
    container_name   = "shopping-cart-service"
    container_port   = 8084
  }

  depends_on = [
    aws_lb_listener.http,
    aws_instance.rabbitmq
  ]

  tags = {
    Name        = "shopping-cart-service"
    Environment = var.environment
  }
}

# Credit Card Authorizer Service
resource "aws_ecs_service" "credit_card_authorizer" {
  name            = "credit-card-authorizer"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.credit_card_authorizer.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # Allow Spring Boot time to start before health checks begin
  health_check_grace_period_seconds = 60

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [
      aws_security_group.credit_card_service_sg.id,
      aws_security_group.inter_service_sg.id
    ]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.credit_card_service_tg.arn
    container_name   = "credit-card-authorizer"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name        = "credit-card-authorizer"
    Environment = var.environment
  }
}

# Warehouse Service (load balanced for reserve/ship HTTP endpoints + RabbitMQ consumer)
resource "aws_ecs_service" "warehouse_service" {
  name            = "warehouse-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.warehouse_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # Allow Spring Boot time to start before health checks begin
  health_check_grace_period_seconds = 60

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [
      aws_security_group.warehouse_service_sg.id,
      aws_security_group.inter_service_sg.id
    ]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.warehouse_service_tg.arn
    container_name   = "warehouse-service"
    container_port   = 8083
  }

  depends_on = [
    aws_instance.rabbitmq,
    aws_lb_listener.http
  ]

  tags = {
    Name        = "warehouse-service"
    Environment = var.environment
  }
}
