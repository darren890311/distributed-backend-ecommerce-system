# RabbitMQ Configuration for Warehouse Service
# This configuration creates an EC2 instance running RabbitMQ for message queuing
# between Shopping Cart Service and Warehouse Service

# Data source to get the latest Amazon Linux 2023 AMI
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# EC2 Instance for RabbitMQ
resource "aws_instance" "rabbitmq" {
  ami           = var.rabbitmq_ami_id != "" ? var.rabbitmq_ami_id : data.aws_ami.amazon_linux_2023.id
  instance_type = var.rabbitmq_instance_type
  key_name      = var.key_name

  vpc_security_group_ids = [aws_security_group.rabbitmq_sg.id]
  subnet_id              = var.rabbitmq_subnet_id

  # User data script to install and configure RabbitMQ
  user_data = templatefile("${path.module}/rabbitmq_userdata.sh", {
    rabbitmq_username = var.rabbitmq_username
    rabbitmq_password = var.rabbitmq_password
  })

  # Enable detailed monitoring
  monitoring = var.enable_detailed_monitoring

  # Root volume configuration
  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.rabbitmq_volume_size
    delete_on_termination = true
    encrypted             = true

    tags = {
      Name = "rabbitmq-root-volume"
    }
  }

  tags = merge(
    var.common_tags,
    {
      Name        = "rabbitmq-server"
      Environment = var.environment
      Service     = "rabbitmq"
      Role        = "message-broker"
    }
  )

  # Ensure instance is replaced if user data changes
  user_data_replace_on_change = true

  lifecycle {
    create_before_destroy = true
  }
}

# Elastic IP for RabbitMQ (optional - for stable endpoint)
resource "aws_eip" "rabbitmq_eip" {
  count = var.use_rabbitmq_eip ? 1 : 0

  instance = aws_instance.rabbitmq.id
  domain   = "vpc"

  tags = merge(
    var.common_tags,
    {
      Name        = "rabbitmq-eip"
      Environment = var.environment
      Service     = "rabbitmq"
    }
  )

  depends_on = [aws_instance.rabbitmq]
}

# CloudWatch Alarm for RabbitMQ CPU utilization
resource "aws_cloudwatch_metric_alarm" "rabbitmq_cpu" {
  count = var.enable_cloudwatch_alarms ? 1 : 0

  alarm_name          = "rabbitmq-high-cpu-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors RabbitMQ EC2 CPU utilization"
  alarm_actions       = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []

  dimensions = {
    InstanceId = aws_instance.rabbitmq.id
  }

  tags = merge(
    var.common_tags,
    {
      Name        = "rabbitmq-cpu-alarm"
      Environment = var.environment
    }
  )
}

# CloudWatch Alarm for RabbitMQ Status Check
resource "aws_cloudwatch_metric_alarm" "rabbitmq_status_check" {
  count = var.enable_cloudwatch_alarms ? 1 : 0

  alarm_name          = "rabbitmq-status-check-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = "60"
  statistic           = "Maximum"
  threshold           = "0"
  alarm_description   = "This metric monitors RabbitMQ instance status checks"
  alarm_actions       = var.sns_topic_arn != "" ? [var.sns_topic_arn] : []

  dimensions = {
    InstanceId = aws_instance.rabbitmq.id
  }

  tags = merge(
    var.common_tags,
    {
      Name        = "rabbitmq-status-check-alarm"
      Environment = var.environment
    }
  )
}
