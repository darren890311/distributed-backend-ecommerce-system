#!/bin/bash

echo "========================================"
echo "CS6650 Assignment 5 - Local Load Test"
echo "========================================"
echo ""
echo "Starting Locust against LOCAL services..."
echo "Services must be running on localhost"
echo ""
echo "Open browser to: http://localhost:8089"
echo "Press Ctrl+C to stop"
echo ""

# Run Locust with localhost configuration
locust -f locustfile.py \
  --host http://localhost:8084 \
  --users 10 \
  --spawn-rate 2 \
  --run-time 5m \
  --html report_local.html