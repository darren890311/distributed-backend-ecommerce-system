# Application Load Balancer Configuration for E-Commerce Microservices
# This ALB routes traffic to ProductService, ShoppingCartService, and CreditCardAuthorizerService

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Application Load Balancer
resource "aws_lb" "ecommerce_alb" {
  name               = "ecommerce-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb_sg.id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = var.enable_deletion_protection
  enable_http2              = true
  enable_cross_zone_load_balancing = true

  tags = {
    Name        = "ecommerce-alb"
    Environment = var.environment
    Project     = "cs6650-assignment3"
  }
}

# Security Group for ALB
resource "aws_security_group" "alb_sg" {
  name        = "ecommerce-alb-sg"
  description = "Security group for Application Load Balancer"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "ecommerce-alb-sg"
    Environment = var.environment
  }
}

# Target Group for Product Service (Port 8082)
resource "aws_lb_target_group" "product_service_tg" {
  name     = "product-service-tg"
  port     = 8082
  protocol = "HTTP"
  vpc_id   = var.vpc_id
  target_type = "ip"
  load_balancing_algorithm_type = "weighted_random"
  load_balancing_anomaly_mitigation = "on"

  # Health check configuration
  health_check {
    enabled             = true
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/actuator/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  # # Stickiness configuration
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400
    enabled         = false
  }

  # Deregistration delay
  deregistration_delay = 30

  tags = {
    Name        = "product-service-tg"
    Environment = var.environment
    Service     = "product-service"
  }
}

# Target Group for Shopping Cart Service (Port 8084)
resource "aws_lb_target_group" "shopping_cart_service_tg" {
  name     = "shopping-cart-service-tg"
  port     = 8084
  protocol = "HTTP"
  vpc_id   = var.vpc_id
  target_type = "ip"

  # Health check configuration
  health_check {
    enabled             = true
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/actuator/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  # # Stickiness configuration
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400
    enabled         = false
  }

  # Deregistration delay
  deregistration_delay = 30

  tags = {
    Name        = "shopping-cart-service-tg"
    Environment = var.environment
    Service     = "shopping-cart-service"
  }
}

# Target Group for Credit Card Authorizer Service (Port 8080)
resource "aws_lb_target_group" "credit_card_service_tg" {
  name     = "credit-card-service-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = var.vpc_id
  target_type = "ip"

  # Health check configuration
  health_check {
    enabled             = true
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/actuator/health"
    protocol            = "HTTP"
    matcher             = "200"
  }

  # # Stickiness configuration
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400
    enabled         = false
  }

  # Deregistration delay
  deregistration_delay = 30

  tags = {
    Name        = "credit-card-service-tg"
    Environment = var.environment
    Service     = "credit-card-authorizer"
  }
}

# HTTP Listener (Port 80)
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.ecommerce_alb.arn
  port              = "80"
  protocol          = "HTTP"

  # Default action - return 404 for unmatched routes
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = jsonencode({
        error   = "Not Found"
        message = "The requested resource was not found"
      })
      status_code = "404"
    }
  }
}

# HTTPS Listener (Port 443) - Optional, requires SSL certificate
# Uncomment and configure if you have an SSL certificate
# resource "aws_lb_listener" "https" {
#   load_balancer_arn = aws_lb.ecommerce_alb.arn
#   port              = "443"
#   protocol          = "HTTPS"
#   ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
#   certificate_arn   = var.ssl_certificate_arn
#
#   default_action {
#     type = "fixed-response"
#
#     fixed_response {
#       content_type = "application/json"
#       message_body = jsonencode({
#         error   = "Not Found"
#         message = "The requested resource was not found"
#       })
#       status_code = "404"
#     }
#   }
# }

# Listener Rule: Route traffic containing "product" to Product Service
resource "aws_lb_listener_rule" "product_service_rule" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.product_service_tg.arn
  }

  condition {
    path_pattern {
      values = ["*/product*", "*/products*"]
    }
  }

  tags = {
    Name    = "product-service-route"
    Service = "product-service"
  }
}

# Listener Rule: Route traffic containing "shopping-cart" or "cart" to Shopping Cart Service
resource "aws_lb_listener_rule" "shopping_cart_service_rule" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 200

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.shopping_cart_service_tg.arn
  }

  condition {
    path_pattern {
      values = ["*/shopping-cart*", "*/cart*"]
    }
  }

  tags = {
    Name    = "shopping-cart-service-route"
    Service = "shopping-cart-service"
  }
}

# Listener Rule: Route traffic containing "credit-card" or "payment" to Credit Card Service
resource "aws_lb_listener_rule" "credit_card_service_rule" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 300

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.credit_card_service_tg.arn
  }

  condition {
    path_pattern {
      values = ["*/credit-card*", "*/payment*", "*/authorize*"]
    }
  }

  tags = {
    Name    = "credit-card-service-route"
    Service = "credit-card-authorizer"
  }
}

# Advanced Routing: HTTP Header-based routing for Product Service
# This allows routing based on specific headers within the product target group
resource "aws_lb_listener_rule" "product_service_header_rule" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 150

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.product_service_tg.arn
  }

  condition {
    path_pattern {
      values = ["*/product*"]
    }
  }

  condition {
    http_header {
      http_header_name = "X-Service-Type"
      values           = ["product", "catalog", "inventory"]
    }
  }

  tags = {
    Name    = "product-service-header-route"
    Service = "product-service"
  }
}

# Advanced Routing: HTTP Header-based routing for Shopping Cart Service
resource "aws_lb_listener_rule" "shopping_cart_service_header_rule" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 250

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.shopping_cart_service_tg.arn
  }

  condition {
    path_pattern {
      values = ["*/shopping-cart*"]
    }
  }

  condition {
    http_header {
      http_header_name = "X-Service-Type"
      values           = ["cart", "checkout"]
    }
  }

  tags = {
    Name    = "shopping-cart-header-route"
    Service = "shopping-cart-service"
  }
}

# Advanced Routing: HTTP Header-based routing for Credit Card Service
resource "aws_lb_listener_rule" "credit_card_service_header_rule" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 350

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.credit_card_service_tg.arn
  }

  condition {
    path_pattern {
      values = ["*/credit-card*"]
    }
  }

  condition {
    http_header {
      http_header_name = "X-Service-Type"
      values           = ["payment", "authorization"]
    }
  }

  tags = {
    Name    = "credit-card-header-route"
    Service = "credit-card-authorizer"
  }
}

# Note: Outputs have been moved to outputs.tf for better organization