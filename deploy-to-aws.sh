#!/bin/bash
set -e

echo "=== CS6650 Assignment 5 - AWS Deployment Script ==="
echo ""

# Get AWS account ID and region
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=$(aws configure get region || echo "us-east-1")

echo "AWS Account ID: $ACCOUNT_ID"
echo "AWS Region: $REGION"
echo ""

# List of services
SERVICES=("kv-database" "product-service" "shopping-cart-service" "credit-card-authorizer" "warehouse-service")

echo "Step 1: Creating ECR repositories..."
for service in "${SERVICES[@]}"; do
  echo "Creating repository for $service..."
  aws ecr create-repository \
    --repository-name "cs6650-$service" \
    --region $REGION 2>/dev/null || echo "  Repository cs6650-$service already exists"
done
echo ""

# Login to ECR
echo "Step 2: Logging into ECR..."
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com
echo ""

# Build and push images
echo "Step 3: Building and pushing Docker images..."

# KV Database
echo "Building kv-database..."
cd kv-tx-stubs
docker build -t cs6650-kv-database .
docker tag cs6650-kv-database:latest $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-kv-database:latest
docker push $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-kv-database:latest
cd ..

# Product Service
echo "Building product-service..."
cd product-service
docker build -t cs6650-product-service .
docker tag cs6650-product-service:latest $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-product-service:latest
docker push $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-product-service:latest
cd ..

# Shopping Cart Service
echo "Building shopping-cart-service..."
cd shopping-cart-service
docker build -t cs6650-shopping-cart-service .
docker tag cs6650-shopping-cart-service:latest $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-shopping-cart-service:latest
docker push $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-shopping-cart-service:latest
cd ..

# Credit Card Authorizer
echo "Building credit-card-authorizer..."
cd credit-card-authorizer
docker build -t cs6650-credit-card-authorizer .
docker tag cs6650-credit-card-authorizer:latest $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-credit-card-authorizer:latest
docker push $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-credit-card-authorizer:latest
cd ..

# Warehouse Service
echo "Building warehouse-service..."
cd warehouse-service
docker build -t cs6650-warehouse-service .
docker tag cs6650-warehouse-service:latest $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-warehouse-service:latest
docker push $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cs6650-warehouse-service:latest
cd ..

echo ""
echo "=== All images built and pushed successfully! ==="
echo ""
echo "Next steps:"
echo "1. cd terraform"
echo "2. terraform init"
echo "3. terraform plan"
echo "4. terraform apply"
