# Security Groups for Target Instances
# These security groups allow traffic from the ALB to the microservice instances

# Security Group for Product Service Instances (Port 8082)
resource "aws_security_group" "product_service_sg" {
  name        = "product-service-instances-sg"
  description = "Security group for Product Service instances - allows traffic from ALB"
  vpc_id      = var.vpc_id

  # Allow traffic from ALB on port 8082
  ingress {
    description     = "HTTP from ALB"
    from_port       = 8082
    to_port         = 8082
    protocol        = "tcp"
    security_groups = [aws_security_group.alb_sg.id]
  }

  # Allow SSH access (optional - for management)
  ingress {
    description = "SSH from management network"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # Allow all outbound traffic
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "product-service-instances-sg"
    Environment = var.environment
    Service     = "product-service"
  }
}


# Security Group for Credit Card Authorizer Service Instances (Port 8080)
resource "aws_security_group" "credit_card_service_sg" {
  name        = "credit-card-authorizer-instances-sg"
  description = "Security group for Credit Card Authorizer instances - allows traffic from ALB"
  vpc_id      = var.vpc_id

  # Allow traffic from ALB on port 8080
  ingress {
    description     = "HTTP from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb_sg.id]
  }

  # Allow SSH access (optional - for management)
  ingress {
    description = "SSH from management network"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.management_cidr_blocks
  }

  # Allow all outbound traffic
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "credit-card-authorizer-instances-sg"
    Environment = var.environment
    Service     = "credit-card-authorizer"
  }
}

# Security Group for Inter-Service Communication
# This allows microservices to communicate with each other directly
resource "aws_security_group" "inter_service_sg" {
  name        = "microservices-inter-communication-sg"
  description = "Allows microservices to communicate with each other"
  vpc_id      = var.vpc_id

  # Product Service (8082) accessible from Shopping Cart
  ingress {
    description     = "Product Service from Shopping Cart"
    from_port       = 8082
    to_port         = 8082
    protocol        = "tcp"
    security_groups = [aws_security_group.shopping_cart_service_sg.id]
  }

  # Credit Card Authorizer (8080) accessible from Shopping Cart
  ingress {
    description     = "Credit Card Authorizer from Shopping Cart"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.shopping_cart_service_sg.id]
  }

  # Warehouse Service (8083) accessible from Shopping Cart
  ingress {
    description     = "Warehouse Service from Shopping Cart"
    from_port       = 8083
    to_port         = 8083
    protocol        = "tcp"
    security_groups = [aws_security_group.shopping_cart_service_sg.id]
  }

  # Allow all outbound traffic
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "microservices-inter-communication-sg"
    Environment = var.environment
  }
}
