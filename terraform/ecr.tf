  # ECR Repositories - Using existing repositories
  # These repositories already have images pushed to them

  data "aws_ecr_repository" "kv_database" {
    name = "cs6650-kv-database"
  }

  data "aws_ecr_repository" "leaderless_kv" {
    name = "cs6650-leaderless-kv"
  }

  data "aws_ecr_repository" "product_service" {
    name = "cs6650-product-service"
  }

  data "aws_ecr_repository" "shopping_cart_service" {
    name = "cs6650-shopping-cart-service"
  }

  data "aws_ecr_repository" "credit_card_authorizer" {
    name = "cs6650-credit-card-authorizer"
  }

  data "aws_ecr_repository" "warehouse_service" {
    name = "cs6650-warehouse-service"
  }

  # Outputs for ECR repositories
  output "ecr_kv_database_url" {
    description = "URL of KV Database ECR repository"
    value       = data.aws_ecr_repository.kv_database.repository_url
  }

  output "ecr_product_service_url" {
    description = "URL of Product Service ECR repository"
    value       = data.aws_ecr_repository.product_service.repository_url
  }

  output "ecr_shopping_cart_service_url" {
    description = "URL of Shopping Cart Service ECR repository"
    value       = data.aws_ecr_repository.shopping_cart_service.repository_url
  }

  output "ecr_credit_card_authorizer_url" {
    description = "URL of Credit Card Authorizer ECR repository"
    value       = data.aws_ecr_repository.credit_card_authorizer.repository_url
  }

  output "ecr_warehouse_service_url" {
    description = "URL of Warehouse Service ECR repository"
    value       = data.aws_ecr_repository.warehouse_service.repository_url
  }

