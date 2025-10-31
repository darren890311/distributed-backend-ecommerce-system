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

# ============================================================================
# RabbitMQ Outputs
# ============================================================================

output "rabbitmq_instance_id" {
  description = "EC2 Instance ID of the RabbitMQ server"
  value       = aws_instance.rabbitmq.id
}

output "rabbitmq_private_ip" {
  description = "Private IP address of the RabbitMQ server"
  value       = aws_instance.rabbitmq.private_ip
}

output "rabbitmq_public_ip" {
  description = "Public IP address of the RabbitMQ server (if applicable)"
  value       = aws_instance.rabbitmq.public_ip
}

output "rabbitmq_eip" {
  description = "Elastic IP address of the RabbitMQ server (if enabled)"
  value       = var.use_rabbitmq_eip ? aws_eip.rabbitmq_eip[0].public_ip : null
}

output "rabbitmq_amqp_endpoint" {
  description = "RabbitMQ AMQP endpoint (use private IP for VPC-internal access)"
  value       = "${aws_instance.rabbitmq.private_ip}:5672"
}

output "rabbitmq_management_url" {
  description = "RabbitMQ Management Console URL"
  value       = "http://${var.use_rabbitmq_eip ? aws_eip.rabbitmq_eip[0].public_ip : aws_instance.rabbitmq.public_ip}:15672"
}

output "rabbitmq_security_group_id" {
  description = "Security Group ID for the RabbitMQ server"
  value       = aws_security_group.rabbitmq_sg.id
}

output "warehouse_service_sg_id" {
  description = "Security Group ID for Warehouse Service instances"
  value       = aws_security_group.warehouse_service_sg.id
}

output "rabbitmq_connection_string" {
  description = "RabbitMQ connection string for services (use private IP)"
  value       = "amqp://${var.rabbitmq_username}:${var.rabbitmq_password}@${aws_instance.rabbitmq.private_ip}:5672/"
  sensitive   = true
}

# ============================================================================
# RabbitMQ Configuration Summary
# ============================================================================

output "rabbitmq_summary" {
  description = "Summary of RabbitMQ deployment configuration"
  value = {
    instance_id         = aws_instance.rabbitmq.id
    instance_type       = var.rabbitmq_instance_type
    private_ip          = aws_instance.rabbitmq.private_ip
    amqp_port          = 5672
    management_port    = 15672
    has_elastic_ip     = var.use_rabbitmq_eip
    cloudwatch_alarms  = var.enable_cloudwatch_alarms
  }
}

# ============================================================================
# Service Connection Instructions
# ============================================================================

output "service_configuration_instructions" {
  description = "Instructions for configuring services to connect to RabbitMQ"
  value = <<-EOT
    # Environment Variables for Shopping Cart Service
    export RABBITMQ_HOST="${aws_instance.rabbitmq.private_ip}"
    export RABBITMQ_PORT="5672"
    export RABBITMQ_USERNAME="${var.rabbitmq_username}"
    export RABBITMQ_PASSWORD="${var.rabbitmq_password}"

    # Environment Variables for Warehouse Service
    export SPRING_RABBITMQ_HOST="${aws_instance.rabbitmq.private_ip}"
    export SPRING_RABBITMQ_PORT="5672"
    export SPRING_RABBITMQ_USERNAME="${var.rabbitmq_username}"
    export SPRING_RABBITMQ_PASSWORD="${var.rabbitmq_password}"

    # Access RabbitMQ Management Console
    Management URL: http://${var.use_rabbitmq_eip ? aws_eip.rabbitmq_eip[0].public_ip : aws_instance.rabbitmq.public_ip}:15672
    Username: ${var.rabbitmq_username}
    Password: ${var.rabbitmq_password}

    # SSH into RabbitMQ Server
    ssh -i <your-key.pem> ec2-user@${var.use_rabbitmq_eip ? aws_eip.rabbitmq_eip[0].public_ip : aws_instance.rabbitmq.public_ip}

    # Check RabbitMQ Status
    ssh -i <your-key.pem> ec2-user@${var.use_rabbitmq_eip ? aws_eip.rabbitmq_eip[0].public_ip : aws_instance.rabbitmq.public_ip} "sudo rabbitmqctl status"

    # List Queues
    ssh -i <your-key.pem> ec2-user@${var.use_rabbitmq_eip ? aws_eip.rabbitmq_eip[0].public_ip : aws_instance.rabbitmq.public_ip} "sudo rabbitmqctl list_queues"
  EOT
  sensitive = true
}