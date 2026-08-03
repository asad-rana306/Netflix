#!/usr/bin/env bash
set -e

# Helper function: Streams logs for a service until its port is ready
start_and_wait() {
  local service=$1
  local port=$2

  echo ""
  echo "=========================================================="
  echo ">>> Starting: $service (Port $port)"
  echo "=========================================================="

  # Start container in background
  docker compose up -d $service

  # Tail logs in the background
  docker compose logs -f $service &
  LOG_PID=$!

  # Wait until the port responds on localhost
  until curl -s http://localhost:$port >/dev/null 2>&1 || nc -z localhost $port 2>/dev/null; do
    sleep 2
  done

  # Force-kill log streaming immediately so the script proceeds
  kill -9 $LOG_PID 2>/dev/null || true

  echo ""
  echo "[SUCCESS] $service is UP and listening on port $port!"
  echo "----------------------------------------------------------"
  sleep 2
}

# ----------------------------------------------------------------
# PHASE 1: Infrastructure
# ----------------------------------------------------------------
echo "=========================================================="
echo ">>> Phase 1: Launching Infrastructure Services..."
echo "=========================================================="
docker compose up -d postgres redis zookeeper kafka pgbouncer mailhog

echo "Waiting for Databases & Brokers to be ready..."
until docker compose exec -T postgres pg_isready -U postgres >/dev/null 2>&1; do sleep 2; done
until docker compose exec -T redis redis-cli ping >/dev/null 2>&1; do sleep 2; done
echo "[SUCCESS] Core Infrastructure is fully ready!"

# ----------------------------------------------------------------
# PHASE 2: Service Discovery
# ----------------------------------------------------------------
start_and_wait "eureka-server" 8761

# ----------------------------------------------------------------
# PHASE 3: Microservices (Sequential Launch)
# ----------------------------------------------------------------
start_and_wait "user-service" 8081
start_and_wait "catalog-service" 8082
start_and_wait "streaming-service" 8083
start_and_wait "payment-service" 8084
start_and_wait "notification-service" 8085

# ----------------------------------------------------------------
# PHASE 4: API Gateway
# ----------------------------------------------------------------
start_and_wait "gateway-service" 8000

echo ""
echo "=========================================================="
echo " ALL NETFLIX MICROSERVICES LAUNCHED SUCCESSFULLY!"
echo "=========================================================="
docker compose ps