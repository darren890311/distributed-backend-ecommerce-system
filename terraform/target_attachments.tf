# Target Group Attachments with Automatic Target Weights Support
# AWS ALB uses an anomaly detection algorithm to automatically adjust weights
# based on target health and performance metrics

# Product Service Target Attachments
resource "aws_lb_target_group_attachment" "product_service_targets" {
  for_each = { for idx, target in var.product_service_targets : idx => target }

  target_group_arn = aws_lb_target_group.product_service_tg.arn
  target_id        = each.value.id
  port             = each.value.port

  # Automatic Target Weights:
  # - Set weight to null or omit to enable AWS's automatic weight adjustment
  # - The anomaly detection algorithm adjusts weights based on:
  #   * Target health status
  #   * Response times
  #   * Error rates
  #   * Connection counts
  # - Manually set weight (1-999) to override automatic behavior
  # - Weight of 0 stops routing traffic but keeps target registered

  # If weight is null, AWS uses automatic target weights
  # If weight is provided, it uses the specified weight
  weight = each.value.weight
}

# Shopping Cart Service Target Attachments
resource "aws_lb_target_group_attachment" "shopping_cart_service_targets" {
  for_each = { for idx, target in var.shopping_cart_service_targets : idx => target }

  target_group_arn = aws_lb_target_group.shopping_cart_service_tg.arn
  target_id        = each.value.id
  port             = each.value.port

  weight = each.value.weight
}

# Credit Card Service Target Attachments
resource "aws_lb_target_group_attachment" "credit_card_service_targets" {
  for_each = { for idx, target in var.credit_card_service_targets : idx => target }

  target_group_arn = aws_lb_target_group.credit_card_service_tg.arn
  target_id        = each.value.id
  port             = each.value.port

  weight = each.value.weight
}

# Note: Target count outputs have been moved to outputs.tf for better organization