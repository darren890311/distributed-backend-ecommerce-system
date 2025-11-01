# ECR Repositories for Microservices

resource "aws_ecr_repository" "product_service" {
  name                 = "product-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  tags = {
    Name        = "product-service"
    Environment = var.environment
  }
}

resource "aws_ecr_repository" "shopping_cart_service" {
  name                 = "shopping-cart-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  tags = {
    Name        = "shopping-cart-service"
    Environment = var.environment
  }
}

resource "aws_ecr_repository" "credit_card_authorizer" {
  name                 = "credit-card-authorizer"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  tags = {
    Name        = "credit-card-authorizer"
    Environment = var.environment
  }
}

resource "aws_ecr_repository" "warehouse_service" {
  name                 = "warehouse-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  tags = {
    Name        = "warehouse-service"
    Environment = var.environment
  }
}

# Outputs for ECR repositories
output "ecr_product_service_url" {
  description = "URL of Product Service ECR repository"
  value       = aws_ecr_repository.product_service.repository_url
}

output "ecr_shopping_cart_service_url" {
  description = "URL of Shopping Cart Service ECR repository"
  value       = aws_ecr_repository.shopping_cart_service.repository_url
}

output "ecr_credit_card_authorizer_url" {
  description = "URL of Credit Card Authorizer ECR repository"
  value       = aws_ecr_repository.credit_card_authorizer.repository_url
}

output "ecr_warehouse_service_url" {
  description = "URL of Warehouse Service ECR repository"
  value       = aws_ecr_repository.warehouse_service.repository_url
}
