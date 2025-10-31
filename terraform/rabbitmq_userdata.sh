#!/bin/bash
set -e

# Update system
yum update -y

# Install Docker
yum install -y docker
systemctl start docker
systemctl enable docker

# Add ec2-user to docker group
usermod -a -G docker ec2-user

# Run RabbitMQ container
docker run -d \
  --name rabbitmq \
  --restart unless-stopped \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=${rabbitmq_username} \
  -e RABBITMQ_DEFAULT_PASS=${rabbitmq_password} \
  rabbitmq:3-management

# Wait for RabbitMQ to start
sleep 30

echo "RabbitMQ installation complete"
