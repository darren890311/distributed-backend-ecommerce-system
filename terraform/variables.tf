# Variables for AWS Application Load Balancer Configuration

variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "us-west-2"
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


variable "management_cidr_blocks" {
  description = "CIDR blocks allowed to SSH into instances for management"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "rabbitmq_subnet_id" {
  description = "Subnet ID where RabbitMQ instance will be deployed"
  type        = string
}

variable "key_name" {
  description = "EC2 Key Pair name for SSH access"
  type        = string
}

variable "rabbitmq_username" {
  description = "Username for RabbitMQ administrator"
  type        = string
  default     = "admin"
  sensitive   = true
}

variable "rabbitmq_password" {
  description = "Password for RabbitMQ administrator"
  type        = string
  sensitive   = true
}

variable "rabbitmq_instance_type" {
  description = "EC2 instance type for RabbitMQ server"
  type        = string
  default     = "t3.medium"
}

variable "rabbitmq_volume_size" {
  description = "Size of root volume for RabbitMQ instance in GB"
  type        = number
  default     = 30
}

variable "enable_cloudwatch_alarms" {
  description = "Enable CloudWatch alarms for RabbitMQ"
  type        = bool
  default     = true
}

variable "use_rabbitmq_eip" {
  description = "Whether to assign Elastic IP to RabbitMQ"
  type        = bool
  default     = false
}

variable "enable_automatic_target_weights" {
  description = "Enable automatic target weights"
  type        = bool
  default     = true
}

variable "product_service_targets" {
  description = "Product Service targets"
  type        = list(object({
    id     = string
    port   = optional(number, 8082)
    weight = optional(number, null)
  }))
  default = []
}

variable "shopping_cart_service_targets" {
  description = "Shopping Cart Service targets"
  type        = list(object({
    id     = string
    port   = optional(number, 8084)
    weight = optional(number, null)
  }))
  default = []
}

variable "credit_card_service_targets" {
  description = "Credit Card Service targets"
  type        = list(object({
    id     = string
    port   = optional(number, 8080)
    weight = optional(number, null)
  }))
  default = []
}

# Common tags for all resources
variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    Project   = "cs6650-assignment3"
    ManagedBy = "Terraform"
  }
}


# SNS topic for alarms (optional)
variable "sns_topic_arn" {
  description = "SNS topic ARN for CloudWatch alarm notifications"
  type        = string
  default     = ""
}


# Monitoring CIDR blocks
variable "monitoring_cidr_blocks" {
  description = "CIDR blocks for monitoring access"
  type        = list(string)
  default     = []
}

# Warehouse service instances
variable "warehouse_service_instance_ids" {
  description = "Warehouse service instance IDs"
  type        = list(string)
  default     = []
}

# AMI ID for RabbitMQ
variable "rabbitmq_ami_id" {
  description = "AMI ID for RabbitMQ (empty = latest Amazon Linux)"
  type        = string
  default     = ""
}

# Detailed monitoring
variable "enable_detailed_monitoring" {
  description = "Enable detailed CloudWatch monitoring"
  type        = bool
  default     = false
}


# SSL certificate
variable "ssl_certificate_arn" {
  description = "SSL certificate ARN for HTTPS"
  type        = string
  default     = ""
}
