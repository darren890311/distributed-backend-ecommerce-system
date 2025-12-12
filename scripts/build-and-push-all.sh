#!/bin/bash
set -e

echo "🚀 Building and pushing all microservices..."
echo ""

# Service 1: Product Service
echo "📦 [1/4] Building Product Service..."
mvn -f product-service/pom.xml clean package -DskipTests -q
docker build --platform linux/amd64 -t product-service:latest -f product-service/Dockerfile .
docker tag product-service:latest 590183802817.dkr.ecr.us-west-2.amazonaws.com/product-service:latest
docker push 590183802817.dkr.ecr.us-west-2.amazonaws.com/product-service:latest
echo "✅ Product Service pushed"

# Service 2: Shopping Cart Service  
echo "📦 [2/4] Building Shopping Cart Service..."
mvn -f shopping-cart-service/pom.xml clean package -DskipTests -q
docker build --platform linux/amd64 -t shopping-cart-service:latest -f shopping-cart-service/Dockerfile .
docker tag shopping-cart-service:latest 590183802817.dkr.ecr.us-west-2.amazonaws.com/shopping-cart-service:latest
docker push 590183802817.dkr.ecr.us-west-2.amazonaws.com/shopping-cart-service:latest
echo "✅ Shopping Cart Service pushed"

# Service 3: Credit Card Authorizer
echo "📦 [3/4] Building Credit Card Authorizer..."
mvn -f credit-card-authorizer/pom.xml clean package -DskipTests -q
docker build --platform linux/amd64 -t credit-card-authorizer:latest -f credit-card-authorizer/Dockerfile .
docker tag credit-card-authorizer:latest 590183802817.dkr.ecr.us-west-2.amazonaws.com/credit-card-authorizer:latest
docker push 590183802817.dkr.ecr.us-west-2.amazonaws.com/credit-card-authorizer:latest
echo "✅ Credit Card Authorizer pushed"

# Service 4: Warehouse Service
echo "📦 [4/4] Building Warehouse Service..."
mvn -f warehouse-service/pom.xml clean package -DskipTests -q
docker build --platform linux/amd64 -t warehouse-service:latest -f warehouse-service/Dockerfile .
docker tag warehouse-service:latest 590183802817.dkr.ecr.us-west-2.amazonaws.com/warehouse-service:latest
docker push 590183802817.dkr.ecr.us-west-2.amazonaws.com/warehouse-service:latest
echo "✅ Warehouse Service pushed"

echo ""
echo "🎉 All services built and pushed successfully!"
