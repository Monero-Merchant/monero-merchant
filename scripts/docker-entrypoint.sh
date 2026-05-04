#!/bin/sh
set -e

# ------------------------
# Initialize .env from environment variables
# Auto-generate secrets on first boot
# ------------------------

ENV_FILE="${APP_DATA_DIR:-/data}/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "Generating initial .env..."

    gen_secret() { openssl rand -hex 32; }
    gen_password() { openssl rand -hex 16; }

    DB_PASSWORD="${DB_PASSWORD:-$(gen_password)}"
    ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(gen_password)}"
    JWT_SECRET="${JWT_SECRET:-$(gen_secret)}"
    JWT_REFRESH_SECRET="${JWT_REFRESH_SECRET:-$(gen_secret)}"
    JWT_MONEROPAY_SECRET="${JWT_MONEROPAY_SECRET:-$(gen_secret)}"
    JWT_LWS_TOKEN="${JWT_LWS_TOKEN:-$(gen_secret)}"
    WALLET_PASSWORD="${WALLET_PASSWORD:-$(gen_password)}"
    MONEROPAY_POSTGRES_PASSWORD="${MONEROPAY_POSTGRES_PASSWORD:-$(gen_password)}"

    MONERO_DAEMON_ENDPOINT="${MONERO_DAEMON_ENDPOINT:-http://${MONERO_DAEMON_RPC_HOSTNAME:-node.monero.world}:${MONERO_DAEMON_RPC_PORT:-18081}/json_rpc}"

    cat > "$ENV_FILE" << EOF
# Auto-generated on first boot
DB_HOST=backend-db
DB_USER=moneromerchant
DB_PASSWORD=${DB_PASSWORD}
DB_NAME=moneromerchant
DB_PORT=5432

MONEROPAY_POSTGRES_USERNAME=moneropay
MONEROPAY_POSTGRES_PASSWORD=${MONEROPAY_POSTGRES_PASSWORD}
MONEROPAY_POSTGRES_DATABASE=moneropay

ADMIN_NAME=admin
ADMIN_PASSWORD=${ADMIN_PASSWORD}

JWT_SECRET=${JWT_SECRET}
JWT_REFRESH_SECRET=${JWT_REFRESH_SECRET}
JWT_MONEROPAY_SECRET=${JWT_MONEROPAY_SECRET}
JWT_LWS_TOKEN=${JWT_LWS_TOKEN}

MONEROPAY_BASE_URL=http://moneropay:5000
MONEROPAY_CALLBACK_URL=http://backend:8080/callback/

MONERO_DAEMON_RPC_ENDPOINT=${MONERO_DAEMON_ENDPOINT}
MONERO_WALLET_RPC_ENDPOINT=http://monero-wallet-rpc:28081/json_rpc

WALLET_NAME=wallet
WALLET_PASSWORD=${WALLET_PASSWORD}
WALLET_AUTO_REFRESH_PERIOD=2

PORT=8080
EOF

    echo ".env generated at $ENV_FILE"
fi

exec ./backend
