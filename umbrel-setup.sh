#!/bin/bash
# Monero Merchant - Umbrel Setup Script
# Automated first-time configuration for Umbrel deployment
#
# Usage: ./umbrel-setup.sh
# This script generates all required configuration files and environment
# variables for deploying Monero Merchant on Umbrel OS.

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Monero Merchant - Umbrel Setup                     ║${NC}"
echo -e "${BLUE}║   One-Click Installation for Umbrel OS               ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Configuration defaults
ADMIN_NAME="${ADMIN_NAME:-admin}"
MONERO_DAEMON="${MONERO_DAEMON:-xmr-node.cakewallet.com}"
MONERO_PORT="${MONERO_PORT:-18081}"
PRIMARY_FIAT="${PRIMARY_FIAT:-USD}"
COMPANY_NAME="${COMPANY_NAME:-}"
ENABLE_TOR="${ENABLE_TOR:-true}"

# Generate secure passwords
generate_password() {
    openssl rand -base64 32 | tr -d '/+=' | head -c 24
}

generate_jwt_secret() {
    openssl rand -hex 64
}

echo -e "${YELLOW}[1/6]${NC} Generating secure credentials..."
ADMIN_PASSWORD=$(generate_password)
DB_PASSWORD=$(generate_password)
MONEROPAY_DB_PASSWORD=$(generate_password)
JWT_SECRET=$(generate_jwt_secret)
JWT_REFRESH_SECRET=$(generate_jwt_secret)
JWT_MONEROPAY_SECRET=$(generate_jwt_secret)
JWT_LWS_TOKEN=$(generate_jwt_secret)
WALLET_PASSWORD=$(generate_password)

echo -e "${GREEN}  ✓ Admin password generated${NC}"
echo -e "${GREEN}  ✓ Database passwords generated${NC}"
echo -e "${GREEN}  ✓ JWT secrets generated${NC}"
echo ""

echo -e "${YELLOW}[2/6]${NC} Creating .env configuration..."
cat > .env << ENVEOF
# ========================
# Monero Merchant - Umbrel Configuration
# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)
# ========================

# Public Ports
MONEROPAY_PORT=5000
BACKEND_PORT=8080
BACKEND_DB_PORT=55432

# MoneroPay Database
MONEROPAY_POSTGRES_USERNAME=moneropay
MONEROPAY_POSTGRES_PASSWORD=${MONEROPAY_DB_PASSWORD}

# Monero Daemon
MONERO_DAEMON_RPC_HOSTNAME=${MONERO_DAEMON}
MONERO_DAEMON_RPC_PORT=${MONERO_PORT}
MONERO_DAEMON_RPC_USERNAME=
MONERO_DAEMON_RPC_PASSWORD=

# Backend Admin
ADMIN_NAME=${ADMIN_NAME}
ADMIN_PASSWORD=${ADMIN_PASSWORD}

# Backend Database
DB_HOST=backend-db
DB_USER=moneromerchant
DB_PASSWORD=${DB_PASSWORD}
DB_NAME=moneromerchant
DB_PORT=5432

# JWT Secrets
JWT_SECRET=${JWT_SECRET}
JWT_REFRESH_SECRET=${JWT_REFRESH_SECRET}
JWT_MONEROPAY_SECRET=${JWT_MONEROPAY_SECRET}
JWT_LWS_TOKEN=${JWT_LWS_TOKEN}

# Backend → MoneroPay
MONEROPAY_BASE_URL=http://moneropay:5000
MONEROPAY_CALLBACK_URL=http://backend:8080/callback/

# Monero RPC (backend)
MONERO_DAEMON_RPC_ENDPOINT=http://${MONERO_DAEMON}:${MONERO_PORT}/json_rpc
MONERO_WALLET_RPC_ENDPOINT=http://monero-wallet-rpc:28081/json_rpc
MONERO_WALLET_RPC_USERNAME=
MONERO_WALLET_RPC_PASSWORD=

# Wallet
WALLET_NAME=wallet
WALLET_PASSWORD=${WALLET_PASSWORD}
WALLET_AUTO_REFRESH_PERIOD=10

# Store Configuration
PRIMARY_FIAT_CURRENCY=${PRIMARY_FIAT}
COMPANY_NAME=${COMPANY_NAME}
ENVEOF

echo -e "${GREEN}  ✓ .env file created${NC}"
echo ""

echo -e "${YELLOW}[3/6]${NC} Creating Umbrel app manifest..."
if [ -f umbrel-app.yml ]; then
    # Update defaults in manifest
    sed -i "s/default: admin/default: ${ADMIN_NAME}/" umbrel-app.yml 2>/dev/null || true
    sed -i "s/default: USD/default: ${PRIMARY_FIAT}/" umbrel-app.yml 2>/dev/null || true
    echo -e "${GREEN}  ✓ umbrel-app.yml updated${NC}"
else
    echo -e "${YELLOW}  ⚠ umbrel-app.yml not found - using default${NC}"
fi
echo ""

echo -e "${YELLOW}[4/6]${NC} Setting up data directories..."
mkdir -p data/wallet data/postgresql data/config data/moneropay
chmod 700 data/wallet
chmod 700 data/config
echo -e "${GREEN}  ✓ Data directories created${NC}"
echo ""

echo -e "${YELLOW}[5/6]${NC} Configuring Docker Compose for Umbrel..."
if command -v docker &> /dev/null; then
    DOCKER_COMPOSE_FILE="${DOCKER_COMPOSE_FILE:-umbrel-docker-compose.yml}"
    if [ -f "$DOCKER_COMPOSE_FILE" ]; then
        echo -e "${GREEN}  ✓ Umbrel docker-compose found: ${DOCKER_COMPOSE_FILE}${NC}"
    else
        echo -e "${YELLOW}  ⚠ Using standard docker-compose.yml${NC}"
        DOCKER_COMPOSE_FILE="docker-compose.yml"
    fi
else
    echo -e "${YELLOW}  ⚠ Docker not found - skipping compose validation${NC}"
fi
echo ""

echo -e "${YELLOW}[6/6]${NC} Validating configuration..."
ERRORS=0

# Check required fields
if [ -z "${ADMIN_PASSWORD}" ]; then
    echo -e "${RED}  ✗ Admin password is empty${NC}"
    ERRORS=$((ERRORS + 1))
fi

if [ -z "${JWT_SECRET}" ]; then
    echo -e "${RED}  ✗ JWT secret is empty${NC}"
    ERRORS=$((ERRORS + 1))
fi

# Validate Monero daemon reachability (non-blocking)
echo -n "  Checking Monero daemon (${MONERO_DAEMON}:${MONERO_PORT})... "
if timeout 5 bash -c "echo > /dev/tcp/${MONERO_DAEMON}/${MONERO_PORT}" 2>/dev/null; then
    echo -e "${GREEN}reachable${NC}"
else
    echo -e "${YELLOW}unreachable (will retry on startup)${NC}"
fi

if [ ${ERRORS} -eq 0 ]; then
    echo -e "${GREEN}  ✓ Configuration valid${NC}"
else
    echo -e "${RED}  ✗ ${ERRORS} configuration errors found${NC}"
fi
echo ""

# Display admin credentials (IMPORTANT - save these!)
echo -e "${RED}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║   ⚠️  SAVE THESE CREDENTIALS - THEY WON'T BE SHOWN AGAIN  ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${BLUE}Admin Username:${NC}  ${ADMIN_NAME}"
echo -e "  ${BLUE}Admin Password:${NC}  ${ADMIN_PASSWORD}"
echo ""
echo -e "  ${BLUE}Dashboard URL:${NC}   http://umbrel.local:8080"
echo -e "  ${BLUE}POS Backend:${NC}     http://umbrel.local:8080"
if [ "${ENABLE_TOR}" = "true" ]; then
    echo -e "  ${BLUE}Tor Address:${NC}   (available after Umbrel Tor setup)"
fi
echo ""

# Save credentials to secure file
CREDENTIALS_FILE="monero-merchant-credentials.txt"
cat > "${CREDENTIALS_FILE}" << CREDEOF
Monero Merchant - Umbrel Credentials
Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)
=====================================
Admin Username: ${ADMIN_NAME}
Admin Password: ${ADMIN_PASSWORD}
Database Password: ${DB_PASSWORD}
MoneroPay DB Password: ${MONEROPAY_DB_PASSWORD}
Wallet Password: ${WALLET_PASSWORD}
Dashboard URL: http://umbrel.local:8080
CREDEOF
chmod 600 "${CREDENTIALS_FILE}"
echo -e "${GREEN}Credentials saved to: ${CREDENTIALS_FILE}${NC}"
echo ""

# Start services
echo -e "${YELLOW}Starting services...${NC}"
if command -v docker &> /dev/null; then
    docker compose -f "${DOCKER_COMPOSE_FILE:-umbrel-docker-compose.yml}" up -d 2>/dev/null || \
    docker-compose -f "${DOCKER_COMPOSE_FILE:-umbrel-docker-compose.yml}" up -d 2>/dev/null || \
    echo -e "${YELLOW}  ⚠ Docker compose failed - start manually with:${NC}"
    echo -e "    docker compose -f ${DOCKER_COMPOSE_FILE:-umbrel-docker-compose.yml} up -d"
else
    echo -e "${YELLOW}  ⚠ Docker not available - start services manually${NC}"
fi

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║   Setup Complete!                                    ║${NC}"
echo -e "${GREEN}║   1. Save your credentials (shown above)             ║${NC}"
echo -e "${GREEN}║   2. Open dashboard: http://umbrel.local:8080        ║${NC}"
echo -e "${GREEN}║   3. Install Monero Merchant Android app             ║${NC}"
echo -e "${GREEN}║   4. Configure POS client with backend URL           ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
