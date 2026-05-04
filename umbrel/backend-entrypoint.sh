#!/bin/sh
set -eu

CONFIG_DIR="${MONERO_MERCHANT_CONFIG_DIR:-/config}"
ENV_FILE="${CONFIG_DIR}/backend.env"

rand_hex() {
  if command -v hexdump >/dev/null 2>&1; then
    hexdump -vn "$1" -e '1/1 "%02x"' /dev/urandom
  else
    od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

write_if_missing() {
  key="$1"
  value="$2"
  if ! grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

mkdir -p "$CONFIG_DIR"
umask 077
touch "$ENV_FILE"
chmod 600 "$ENV_FILE"

ADMIN_PASSWORD_VALUE="${ADMIN_PASSWORD:-}"
if [ -z "$ADMIN_PASSWORD_VALUE" ] && [ -n "${APP_PASSWORD:-}" ]; then
  ADMIN_PASSWORD_VALUE="$APP_PASSWORD"
fi
if [ -z "$ADMIN_PASSWORD_VALUE" ]; then
  ADMIN_PASSWORD_VALUE="$(rand_hex 16)"
fi

DAEMON_HOST="${MONERO_DAEMON_RPC_HOSTNAME:-xmr-node.cakewallet.com}"
DAEMON_PORT="${MONERO_DAEMON_RPC_PORT:-18081}"

write_if_missing ADMIN_NAME "${ADMIN_NAME:-admin}"
write_if_missing ADMIN_PASSWORD "$ADMIN_PASSWORD_VALUE"
write_if_missing PORT "${PORT:-8080}"
write_if_missing DB_HOST "${DB_HOST:-backend-db}"
write_if_missing DB_USER "${DB_USER:-moneromerchant}"
write_if_missing DB_PASSWORD "${DB_PASSWORD:-${APP_SEED:-$(rand_hex 16)}}"
write_if_missing DB_NAME "${DB_NAME:-moneromerchant}"
write_if_missing DB_PORT "${DB_PORT:-5432}"
write_if_missing JWT_SECRET "$(rand_hex 32)"
write_if_missing JWT_REFRESH_SECRET "$(rand_hex 32)"
write_if_missing JWT_MONEROPAY_SECRET "$(rand_hex 32)"
write_if_missing JWT_LWS_TOKEN "$(rand_hex 32)"
write_if_missing MONEROPAY_BASE_URL "${MONEROPAY_BASE_URL:-http://moneropay:5000}"
write_if_missing MONEROPAY_CALLBACK_URL "${MONEROPAY_CALLBACK_URL:-http://backend:8080/callback/}"
write_if_missing MONERO_DAEMON_RPC_ENDPOINT "${MONERO_DAEMON_RPC_ENDPOINT:-http://${DAEMON_HOST}:${DAEMON_PORT}/json_rpc}"
write_if_missing MONERO_WALLET_RPC_ENDPOINT "${MONERO_WALLET_RPC_ENDPOINT:-http://monero-wallet-rpc:28081/json_rpc}"
write_if_missing MONERO_WALLET_RPC_USERNAME "${MONERO_WALLET_RPC_USERNAME:-}"
write_if_missing MONERO_WALLET_RPC_PASSWORD "${MONERO_WALLET_RPC_PASSWORD:-}"
write_if_missing WALLET_NAME "${WALLET_NAME:-wallet}"
write_if_missing WALLET_PASSWORD "${WALLET_PASSWORD:-$(rand_hex 16)}"
write_if_missing WALLET_AUTO_REFRESH_PERIOD "${WALLET_AUTO_REFRESH_PERIOD:-2}"

cp "$ENV_FILE" /app/.env
chmod 600 /app/.env

set -a
. "$ENV_FILE"
set +a

exec /app/backend
