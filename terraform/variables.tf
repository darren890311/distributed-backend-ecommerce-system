# Variables for AWS Application Load Balancer Configuration

variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "vpc_id" {
  description = "VPC ID where the ALB and target groups will be created"
  type        = string
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs for the Application Load Balancer"
  type        = list(string)
  validation {
    condition     = length(var.public_subnet_ids) >= 2
    error_message = "At least 2 public subnets in different availability zones are required for ALB"
  }
}

variable "enable_deletion_protection" {
  description = "Enable deletion protection for the ALB"
  type        = bool
  default     = false
}

variable "ssl_certificate_arn" {
  description = "ARN of the SSL certificate for HTTPS listener (optional)"
  type        = string
  default     = ""
}

# EC2 Instance IDs for Target Groups
variable "product_service_instance_ids" {
  description = "List of EC2 instance IDs running Product Service"
  type        = list(string)
  default     = []
}

variable "shopping_cart_service_instance_ids" {
  description = "List of EC2 instance IDs running Shopping Cart Service"
  type        = list(string)
  default     = []
}

variable "credit_card_service_instance_ids" {
  description = "List of EC2 instance IDs running Credit Card Authorizer Service"
  type        = list(string)
  default     = []
}

# Automatic Target Weights Configuration
variable "enable_automatic_target_weights" {
  description = "Enable anomaly detection algorithm for automatic target weights"
  type        = bool
  default     = true
}

variable "product_service_targets" {
  description = "Product Service targets with optional custom weights"
  type = list(object({
    id     = string
    port   = optional(number, 8082)
    weight = optional(number, null) # null = automatic weight
  }))
  default = []
}

variable "shopping_cart_service_targets" {
  description = "Shopping Cart Service targets with optional custom weights"
  type = list(object({
    id     = string
    port   = optional(number, 8084)
    weight = optional(number, null) # null = automatic weight
  }))
  default = []
}

variable "credit_card_service_targets" {
  description = "Credit Card Service targets with optional custom weights"
  type = list(object({
    id     = string
    port   = optional(number, 8080)
    weight = optional(number, null) # null = automatic weight
  }))
  default = []
}

# Management Access
variable "management_cidr_blocks" {
  description = "CIDR blocks allowed to SSH into instances for management"
  type        = list(string)
  default     = ["0.0.0.0/0"]  # Restrict this in production!
}

# Tags
variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    Project   = "cs6650-assignment3"
    ManagedBy = "Terraform"
  }
}