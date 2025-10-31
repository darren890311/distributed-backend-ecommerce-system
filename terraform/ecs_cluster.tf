# ECS Cluster for E-Commerce Microservices
resource "aws_ecs_cluster" "main" {
  name = "ecommerce-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name        = "ecommerce-cluster"
    Environment = var.environment
    Project     = "cs6650-assignment3"
  }
}

# CloudWatch Log Group for ECS tasks
resource "aws_cloudwatch_log_group" "ecs_logs" {
  name              = "/ecs/ecommerce"
  retention_in_days = 7

  tags = {
    Name        = "ecommerce-ecs-logs"
    Environment = var.environment
  }
}
