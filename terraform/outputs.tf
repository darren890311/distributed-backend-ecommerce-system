# Consolidated Outputs for ALB Infrastructure

# ============================================================================
# Application Load Balancer Outputs
# ============================================================================

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = aws_lb.ecommerce_alb.dns_name
}

output "alb_arn" {
  description = "ARN of the Application Load Balancer"
  value       = aws_lb.ecommerce_alb.arn
}

output "alb_zone_id" {
  description = "Zone ID of the Application Load Balancer (for Route53 alias records)"
  value       = aws_lb.ecommerce_alb.zone_id
}

output "alb_id" {
  description = "ID of the Application Load Balancer"
  value       = aws_lb.ecommerce_alb.id
}

# ============================================================================
# Target Group Outputs
# ============================================================================

output "product_service_tg_arn" {
  description = "ARN of the Product Service Target Group"
  value       = aws_lb_target_group.product_service_tg.arn
}

output "product_service_tg_name" {
  description = "Name of the Product Service Target Group"
  value       = aws_lb_target_group.product_service_tg.name
}

output "shopping_cart_service_tg_arn" {
  description = "ARN of the Shopping Cart Service Target Group"
  value       = aws_lb_target_group.shopping_cart_service_tg.arn
}

output "shopping_cart_service_tg_name" {
  description = "Name of the Shopping Cart Service Target Group"
  value       = aws_lb_target_group.shopping_cart_service_tg.name
}

output "credit_card_service_tg_arn" {
  description = "ARN of the Credit Card Service Target Group"
  value       = aws_lb_target_group.credit_card_service_tg.arn
}

output "credit_card_service_tg_name" {
  description = "Name of the Credit Card Service Target Group"
  value       = aws_lb_target_group.credit_card_service_tg.name
}

# ============================================================================
# Security Group Outputs
# ============================================================================

output "alb_security_group_id" {
  description = "Security Group ID for the Application Load Balancer"
  value       = aws_security_group.alb_sg.id
}

output "product_service_sg_id" {
  description = "Security Group ID for Product Service instances"
  value       = aws_security_group.product_service_sg.id
}

output "shopping_cart_service_sg_id" {
  description = "Security Group ID for Shopping Cart Service instances"
  value       = aws_security_group.shopping_cart_service_sg.id
}

output "credit_card_service_sg_id" {
  description = "Security Group ID for Credit Card Authorizer instances"
  value       = aws_security_group.credit_card_service_sg.id
}

output "inter_service_sg_id" {
  description = "Security Group ID for inter-service communication"
  value       = aws_security_group.inter_service_sg.id
}

# ============================================================================
# Target Count Outputs
# ============================================================================

output "product_service_target_count" {
  description = "Number of targets attached to Product Service target group"
  value       = length(var.product_service_targets)
}

output "shopping_cart_service_target_count" {
  description = "Number of targets attached to Shopping Cart Service target group"
  value       = length(var.shopping_cart_service_targets)
}

output "credit_card_service_target_count" {
  description = "Number of targets attached to Credit Card Service target group"
  value       = length(var.credit_card_service_targets)
}

# ============================================================================
# Service Endpoints (for easy access)
# ============================================================================

output "product_service_endpoint" {
  description = "Endpoint for Product Service via ALB"
  value       = "http://${aws_lb.ecommerce_alb.dns_name}/products"
}

output "shopping_cart_service_endpoint" {
  description = "Endpoint for Shopping Cart Service via ALB"
  value       = "http://${aws_lb.ecommerce_alb.dns_name}/shopping-cart"
}

output "credit_card_service_endpoint" {
  description = "Endpoint for Credit Card Authorizer via ALB"
  value       = "http://${aws_lb.ecommerce_alb.dns_name}/credit-card-authorizer/authorize"
}

# ============================================================================
# Configuration Summary
# ============================================================================

output "deployment_summary" {
  description = "Summary of the ALB deployment configuration"
  value = {
    alb_name                = aws_lb.ecommerce_alb.name
    alb_dns                 = aws_lb.ecommerce_alb.dns_name
    environment             = var.environment
    region                  = var.aws_region
    product_targets         = length(var.product_service_targets)
    shopping_cart_targets   = length(var.shopping_cart_service_targets)
    credit_card_targets     = length(var.credit_card_service_targets)
    deletion_protection     = var.enable_deletion_protection
  }
}

# ============================================================================
# Testing Commands
# ============================================================================

output "test_commands" {
  description = "Example curl commands to test each service"
  value = <<-EOT
    # Set ALB DNS as environment variable
    export ALB_DNS="${aws_lb.ecommerce_alb.dns_name}"

    # Test Product Service
    curl http://$ALB_DNS/products
    curl http://$ALB_DNS/products/1
    curl -H "X-Service-Type: product" http://$ALB_DNS/products/1

    # Test Shopping Cart Service
    curl -X POST http://$ALB_DNS/shopping-cart -H "Content-Type: application/json" -d '{"customer_id": 123}'
    curl -H "X-Service-Type: cart" http://$ALB_DNS/shopping-cart/1

    # Test Credit Card Authorizer
    curl -X POST http://$ALB_DNS/credit-card-authorizer/authorize -H "Content-Type: application/json" -d '{"credit_card_number": "1234-5678-9012-3456"}'
    curl -H "X-Service-Type: payment" http://$ALB_DNS/credit-card-authorizer/authorize

    # Check ALB Health
    aws elbv2 describe-target-health --target-group-arn ${aws_lb_target_group.product_service_tg.arn}
    aws elbv2 describe-target-health --target-group-arn ${aws_lb_target_group.shopping_cart_service_tg.arn}
    aws elbv2 describe-target-health --target-group-arn ${aws_lb_target_group.credit_card_service_tg.arn}
  EOT
}