# Auto-Scaling Configuration for CS6650 Assignment 5
# Requirement: Configure microservices to scale on two different parameters
# Maximum: 3 instances per service
# Strategy: Use different metrics (CPU vs Memory) for different services

# ============================================================================
# PRODUCT SERVICE - Scale on CPU Utilization
# ============================================================================

resource "aws_appautoscaling_target" "product_service" {
  max_capacity       = 3
  min_capacity       = 1
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.product_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"

  depends_on = [aws_ecs_service.product_service]
}

resource "aws_appautoscaling_policy" "product_service_cpu" {
  name               = "product-service-cpu-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.product_service.resource_id
  scalable_dimension = aws_appautoscaling_target.product_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.product_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 85.0 # Scale up when CPU > 85% (increased to let it use more capacity)
    scale_in_cooldown  = 30   # Wait 30s before scaling down (faster scale-in)
    scale_out_cooldown = 60   # Wait 60s before scaling up again (slower scale-out)
  }
}

# ============================================================================
# SHOPPING CART SERVICE - Scale on Memory Utilization
# ============================================================================

resource "aws_appautoscaling_target" "shopping_cart_service" {
  max_capacity       = 5     # Increased from 3 for write-heavy workload
  min_capacity       = 2     # Start with 2 instances to handle burst traffic
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.shopping_cart_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"

  depends_on = [aws_ecs_service.shopping_cart_service]
}

resource "aws_appautoscaling_policy" "shopping_cart_service_memory" {
  name               = "shopping-cart-service-memory-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.shopping_cart_service.resource_id
  scalable_dimension = aws_appautoscaling_target.shopping_cart_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.shopping_cart_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = 50.0 # Scale up when Memory > 50% (lowered to scale earlier)
    scale_in_cooldown  = 120  # Wait 120s before scaling down (keep capacity longer)
    scale_out_cooldown = 15   # Wait 15s before scaling up again (react faster)
  }
}

# ============================================================================
# CREDIT CARD AUTHORIZER - Scale on CPU Utilization
# ============================================================================

resource "aws_appautoscaling_target" "credit_card_authorizer" {
  max_capacity       = 3
  min_capacity       = 1
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.credit_card_authorizer.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"

  depends_on = [aws_ecs_service.credit_card_authorizer]
}

resource "aws_appautoscaling_policy" "credit_card_authorizer_cpu" {
  name               = "credit-card-authorizer-cpu-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.credit_card_authorizer.resource_id
  scalable_dimension = aws_appautoscaling_target.credit_card_authorizer.scalable_dimension
  service_namespace  = aws_appautoscaling_target.credit_card_authorizer.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 70.0 # Scale up when CPU > 70%
    scale_in_cooldown  = 60   # Wait 60s before scaling down
    scale_out_cooldown = 30   # Wait 30s before scaling up again
  }
}

# ============================================================================
# WAREHOUSE SERVICE - Scale on Memory Utilization
# ============================================================================

resource "aws_appautoscaling_target" "warehouse_service" {
  max_capacity       = 3
  min_capacity       = 1
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.warehouse_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"

  depends_on = [aws_ecs_service.warehouse_service]
}

resource "aws_appautoscaling_policy" "warehouse_service_memory" {
  name               = "warehouse-service-memory-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.warehouse_service.resource_id
  scalable_dimension = aws_appautoscaling_target.warehouse_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.warehouse_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = 70.0 # Scale up when Memory > 70%
    scale_in_cooldown  = 60   # Wait 60s before scaling down
    scale_out_cooldown = 30   # Wait 30s before scaling up again
  }
}

# ============================================================================
# OUTPUTS - For monitoring and documentation
# ============================================================================

output "autoscaling_configuration" {
  description = "Auto-scaling configuration summary for documentation"
  value = {
    product_service = {
      metric        = "CPU Utilization"
      target        = "85%"
      min_instances = 1
      max_instances = 3
    }
    shopping_cart_service = {
      metric        = "Memory Utilization"
      target        = "50%"
      min_instances = 2
      max_instances = 5
    }
    credit_card_authorizer = {
      metric        = "CPU Utilization"
      target        = "70%"
      min_instances = 1
      max_instances = 3
    }
    warehouse_service = {
      metric        = "Memory Utilization"
      target        = "70%"
      min_instances = 1
      max_instances = 3
    }
  }
}
