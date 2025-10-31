# AWS Configuration
aws_region = "us-west-2"
environment = "dev"

# Network Configuration
vpc_id = "vpc-0c0af65f0d15a9bad"
public_subnet_ids = [
  "subnet-02d7171436a373991",  # us-west-2b
  "subnet-015538bccff644bc3",  # us-west-2c
]

# We'll add EC2 instance IDs here after launching them
product_service_targets = []
shopping_cart_service_targets = []
credit_card_service_targets = []

# RabbitMQ Configuration (we'll configure this later)
rabbitmq_subnet_id = "subnet-02d7171436a373991"
key_name = "cs6650-assignment3"  # We'll create a key pair next
rabbitmq_username = "admin"
rabbitmq_password = "ChangeMe123!"

# Enable automatic target weights
enable_automatic_target_weights = true
rabbitmq_volume_size = 30