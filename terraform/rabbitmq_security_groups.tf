# Security Group for RabbitMQ Server
# This security group controls access to the RabbitMQ instance

resource "aws_security_group" "rabbitmq_sg" {
  name        = "rabbitmq-sg-${var.environment}"
  description = "Security group for RabbitMQ message broker"
  vpc_id      = var.vpc_id

  # AMQP Protocol (5672) - For message broker communication
  ingress {
    description     = "AMQP from Shopping Cart and Warehouse services"
    from_port       = 5672
    to_port         = 5672
    protocol        = "tcp"
    security_groups = [
      aws_security_group.shopping_cart_service_sg.id,
      aws_security_group.warehouse_service_sg.id
    ]
  }

  # AMQP Protocol (5672) - From management CIDR (for debugging)
  ingress {
    description = "AMQP from management network"
    from_port   = 5672
    to_port     = 5672
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # RabbitMQ Management UI (15672) - For administrative access
  ingress {
    description = "RabbitMQ Management UI"
    from_port   = 15672
    to_port     = 15672
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # SSH Access (22) - For server management
  ingress {
    description = "SSH from management network"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # AMQPS (TLS) - Optional secure AMQP (5671)
  ingress {
    description     = "AMQPS from Shopping Cart and Warehouse services"
    from_port       = 5671
    to_port         = 5671
    protocol        = "tcp"
    security_groups = [
      aws_security_group.shopping_cart_service_sg.id,
      aws_security_group.warehouse_service_sg.id
    ]
  }

  # Prometheus metrics endpoint (15692) - Optional for monitoring
  ingress {
    description = "Prometheus metrics from monitoring"
    from_port   = 15692
    to_port     = 15692
    protocol    = "tcp"
    cidr_blocks = var.monitoring_cidr_blocks
  }

  # Allow all outbound traffic
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.common_tags,
    {
      Name        = "rabbitmq-sg-${var.environment}"
      Environment = var.environment
      Service     = "rabbitmq"
    }
  )
}

# Security Group for Shopping Cart Service
resource "aws_security_group" "shopping_cart_service_sg" {
  name        = "shopping-cart-service-sg-${var.environment}"
  description = "Security group for Shopping Cart Service instances"
  vpc_id      = var.vpc_id

  # HTTP traffic from ALB
  ingress {
    description     = "HTTP from ALB"
    from_port       = 8084
    to_port         = 8084
    protocol        = "tcp"
    security_groups = [aws_security_group.alb_sg.id]
  }

  # SSH Access
  ingress {
    description = "SSH from management network"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # Allow all outbound traffic (needed for RabbitMQ, Product Service, etc.)
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.common_tags,
    {
      Name        = "shopping-cart-service-sg-${var.environment}"
      Environment = var.environment
      Service     = "shopping-cart-service"
    }
  )
}

# Security Group for Warehouse Service
resource "aws_security_group" "warehouse_service_sg" {
  name        = "warehouse-service-sg-${var.environment}"
  description = "Security group for Warehouse Service instances"
  vpc_id      = var.vpc_id

  # HTTP traffic from ALB for reserve/ship endpoints (port 8083)
  ingress {
    description     = "HTTP from ALB"
    from_port       = 8083
    to_port         = 8083
    protocol        = "tcp"
    security_groups = [aws_security_group.alb_sg.id]
  }

  # HTTP traffic for health checks and monitoring (optional)
  ingress {
    description = "HTTP for health checks from management"
    from_port   = 8083
    to_port     = 8083
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # SSH Access
  ingress {
    description = "SSH from management network"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # Allow all outbound traffic (needed for RabbitMQ)
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.common_tags,
    {
      Name        = "warehouse-service-sg-${var.environment}"
      Environment = var.environment
      Service     = "warehouse-service"
    }
  )
}

# Note: These security groups create a secure network architecture:
# 1. RabbitMQ only accepts AMQP connections from Shopping Cart and Warehouse services
# 2. RabbitMQ Management UI is only accessible from management network
# 3. Shopping Cart Service accepts HTTP traffic from ALB
# 4. Warehouse Service is internal-only (no external HTTP access)
# 5. All services can be managed via SSH from management network