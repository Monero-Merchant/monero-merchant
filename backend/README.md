# backend

A backend service for managing Monero Merchant operations. It provides APIs for vendors and POS devices to create and track transactions along with features for multiple POS accounts per vendor.

## Features

- Vendor and POS account management
- Secure authentication using JWT
- Transaction creation and tracking
- MoneroPay integration for payment processing
- Admin invite system
- Health check endpoints
- Transfer completion and withdrawal management

## Getting Started

### Prerequisites

- Go 1.26.5 or newer
- PostgreSQL database
- MoneroPay API instance (git apply `moneropay.patch`)
- Monero Wallet RPC
- Outbound HTTPS to `api.coinpaprika.com` and `api.frankfurter.dev` for exchange rates

### Configuration

Run these commands from the `backend/` directory. Copy `.env.example` to `.env` and fill in your environment variables:

```sh
cp .env.example .env
```

Edit `.env` to set database credentials, admin credentials, JWT secrets (including required `JWT_LWS_TOKEN`), MoneroPay URLs, and wallet RPC settings. Generate a separate value with `openssl rand -hex 32` for each secret. The `.env` file is optional: variables already set in the environment take precedence, so containers can be configured with environment variables alone.

### Installation

1. Install dependencies:

   ```sh
   go mod download
   ```

2. Start the backend server:

   ```sh
   go run ./cmd/api/main.go
   ```

The server will start on the port specified in your `.env` file.

### MoneroPay + backend: Docker Setup

For the integrated stack, use `make install` from the repository root; see the [root README](../README.md). The instructions below use the separate `backend/docker-compose.yaml` deployment and an independently running MoneroPay/wallet RPC instance.

#### Prerequisites

- Docker and Docker Compose
- jq and psql optional for tests

1. Set up MoneroPay in a separate terminal/directory

```
git clone https://gitlab.com/moneropay/moneropay.git
cd moneropay
cp .env.example .env
cp docker-compose.override.yaml.example docker-compose.override.yaml
# edit .env and docker-compose.override.yaml
docker compose up -d
```

2. Set up the backend from your chosen parent directory

```
git clone https://github.com/Monero-Merchant/monero-merchant
cd monero-merchant/backend
cp .env.docker.example .env
# edit .env: replace every CHANGEME, including JWT_LWS_TOKEN
# use a different openssl rand -hex 32 value for each secret
# configure reachable MoneroPay, callback, and wallet RPC URLs
# PostgreSQL is published on host port 55432 by default
docker compose up -d --build
```

Compose passes `.env` to the container as environment variables. Keep `PORT=8080` for its fixed container port mapping. Verify that MoneroPay can reach `MONEROPAY_CALLBACK_URL` and that wallet RPC is available at the configured endpoint; the example host port 18083 must match your separate wallet deployment.

3. Useful commands (from `backend/`)

```
# show containers
docker compose ps

# follow logs
docker compose logs -f backend

# recreate API after environment changes
docker compose up -d --force-recreate backend

# rebuild API only
docker compose up -d --build backend
```

## How to use it

Use tools such as Yaak, Postman or curl to interact with the API.

1. **Login as admin**: Use the `/api/auth/login-admin` endpoint with admin credentials to obtain a JWT token.
2. **Create an invite**: Use the `/api/admin/invite` endpoint to create a new invite code.
3. **Register a vendor**: Use the `/api/vendor/create` endpoint with the invite code to create a new vendor account.
4. **Login vendor**: Use the `/api/auth/login-vendor` endpoint to obtain a JWT token.
5. **Create POS**: Use the `/api/vendor/create-pos` endpoint to create a new POS account under the vendor.

Now the POS account can be used with the Monero Merchant app.

To transfer the balance from the vendor account to the Monero wallet, use the `/api/vendor/transfer-balance` endpoint. It will not be instant and will group transfers to be able to payout more often. The background worker checks queued transfers every 30 seconds.

### Example: Login as admin

**POST** `/api/auth/login-admin`

```json
{
  "name": "admin",
  "password": "admin"
}
```

### Example: Create an invite

**POST** `/api/admin/invite`

```json
{
  "valid_until": 1822307285,
  "forced_name": null
}
```

### Example: Register a vendor

**POST** `/api/vendor/create`

```json
{
  "name": "vendor1",
  "password": "yourStrongPassword",
  "invite_code": "<invite-code>",
  "monero_subaddress": "<vendor-payout-subaddress>"
}
```

### Example: Login vendor

**POST** `/api/auth/login-vendor`

```json
{
  "name": "vendor1",
  "password": "yourStrongPassword"
}
```

### Example: Create POS

**POST** `/api/vendor/create-pos`

```json
{
  "name": "pos1",
  "password": "yourStrongPassword"
}
```

### Example: Vendor initiate transfer

**POST** `/api/vendor/transfer-balance`

No request body is required. The authenticated vendor's stored payout subaddress is used.

## API Overview

All endpoints are under `/api` on your backend origin. Admin/vendor/POS authentication means `Authorization: Bearer <access_token>` with the indicated role. Send JSON bodies with `Content-Type: application/json`; values in angle brackets are placeholders. XMR amounts use atomic units (1 XMR = 1,000,000,000,000 units).

| Method | Endpoint | Authentication | Verified request body | Behavior / verification notes |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/login-admin` | None | `{"name":"<admin-name>","password":"<admin-password>"}` | Returns access and refresh tokens; credentials come from configuration. |
| POST | `/api/auth/login-vendor` | None | `{"name":"<vendor-name>","password":"<vendor-password>"}` | Returns access and refresh tokens and vendor ID. |
| POST | `/api/auth/update-password` | Vendor or POS | `{"current_password":"<current-password>","new_password":"<new-password>"}` | Changes own password and returns replacement tokens. Vendor POS reset also accepts `pos_id`. |
| POST | `/api/admin/invite` | Admin | `{"valid_until":1822307285,"forced_name":null}` | Creates an invite; expiry is Unix seconds, forced name is nullable. |
| GET | `/api/admin/vendors` | Admin | None | Lists vendors with calculated balances.  |
| GET | `/api/admin/balance` | Admin | None | Server wallet balance (total, unlocked, locked) and `vendor_balances`, the unpaid vendor sales it should cover. |
| POST | `/api/admin/transfer-balance` | Admin | `{"vendor_id":1}` | Queues the selected vendor's payout to its stored subaddress. |
| POST | `/api/admin/delete` | Admin | `{"vendor_id":1}` | Deletes the selected vendor through the vendor service. |
| GET | `/api/admin/invites` | Admin | None | Lists invites. |
| GET | `/api/admin/transactions?limit=100&offset=0` | Admin | None | Lists all sales, newest first, with `total`. |
| GET | `/api/admin/pos-devices` | Admin | None | Lists all POS devices. |
| GET | `/api/admin/wallet-info` | Admin | None | Returns `exists`, `address` and `wallet_name`. |
| POST | `/api/admin/close-wallet` | Admin | None | Saves and closes the current wallet using `close_wallet`. Returns `success`; 404 if no wallet is open, 502 on other RPC failures. |
| POST | `/api/admin/open-wallet` | Admin | `{"wallet_name":"<wallet name>","wallet_password":"<wallet password>"}` | Opens the supplied wallet using `open_wallet` with `autosave_current: true`. Returns `success`; 400 for a blank name, 502 on RPC failure. Empty passwords are supported. |
| GET | `/api/admin/list-wallet` | Admin | None | Lists wallet names as `{"wallets":["wallet"]}` from `WALLET_DIRECTORY`. 503 if unconfigured, 500 if unreadable. |
| POST | `/api/admin/setup-wallet` | Admin | None | Creates the wallet. 409 if one exists. |
| POST | `/api/admin/restore-wallet` | Admin | `{"seed":"<seed>","restore_height":0}` | Restores the wallet from its seed. 409 if one exists. |
| POST | `/api/admin/wallet-seed` | Admin | `{"password":"<admin-password>"}` | Returns the wallet seed. |
| POST | `/api/vendor/create` | None | `{"name":"<vendor-name>","password":"<vendor-password>","monero_subaddress":"<payout-subaddress>","invite_code":"<invite-code>"}` | Invite-based registration; optional `email` is also supported. |
| GET | `/api/vendor/pos-list` | Vendor | None | Lists the authenticated vendor's POS units. |
| POST | `/api/vendor/create-pos` | Vendor | `{"name":"<pos-name>","password":"<pos-password>"}` | Creates a POS account belonging to the authenticated vendor. |
| GET | `/api/vendor/balance` | Vendor | None | Sums confirmed, untransferred sale amounts; this is an account balance, not a direct wallet RPC balance. |
| GET | `/api/vendor/export` | Vendor | None | Returns confirmed incoming payments as CSV inside JSON `csv_data`. |
| GET | `/api/vendor/transactions` | Vendor | None | Returns confirmed and pending transaction summaries. |
| POST | `/api/vendor/transfer-balance` | Vendor | None | Queues own payout using the stored subaddress.  |
| POST | `/api/vendor/delete` | Vendor | None | Deletes the authenticated vendor. The Yaak example's `vendor_id` body is not read. |
| GET | `/api/misc/health` | None | None | Reports PostgreSQL and MoneroPay health. |
| GET | `/api/misc/daemon-status` | None | None | Reports the configured Monero daemon: `synced`, `height`, `target_height`, `nettype` and connection counts. `status` is 503 when no daemon is configured or it is unreachable. |
| GET | `/api/rates?currencies=EUR,CHF` | Admin, vendor or POS | None | XMR price in each requested currency as `{rate, date}`, plus `xmr_updated_at` and `stale` (price older than 30 min). 400 for an unsupported currency, 503 if the rates can't be fetched yet. |
| GET | `/api/rates/currencies` | Admin, vendor or POS | None | Lists the supported currencies. |
| POST | `/api/auth/login-pos` | None | `{"vendor_id":1,"name":"<pos-name>","password":"<pos-password>"}` | Returns access and refresh tokens. |
| POST | `/api/auth/refresh` | Refresh token in body | `{"refresh_token":"<refresh-token>"}` | Returns replacement access and refresh tokens. |
| POST | `/api/pos/create-transaction` | POS | JSON `amount` (atomic units), `description` (nullable), `amount_in_currency`, `currency`, `required_confirmations` (0–10). | Returns sale ID and receive address. |
| GET | `/api/pos/transaction/{id}` | POS | None | Reads a sale belonging to the authenticated vendor and POS. |
| GET | `/api/pos/transactions` | POS | None | Lists own confirmed payment summaries and pending sales. |
| GET | `/api/pos/balance` | POS | None | Sums own confirmed, untransferred sale amounts. |
| GET | `/api/pos/export` | POS | None | Returns confirmed incoming payments as CSV inside JSON `csv_data`. |
| GET (WebSocket upgrade) | `/api/pos/ws/transaction?transaction_id=<id>` | POS | None | Subscribes to updates for an owned sale. Router uses `HandleFunc`; the handler requires a valid WebSocket handshake. |
| POST | `/api/callback/receive/{jwt}` | Transaction JWT in path | MoneroPay callback JSON. | MoneroPay callback body; JWT signed with `JWT_MONEROPAY_SECRET`. |
| POST | `/api/callback/lws-hook/{jwt}` | Static `JWT_LWS_TOKEN` in path | JSON amount/hash directly or via `tx_info`; optional confirmations/timestamp/height. | Resolves one recent matching sale by amount. |

Replace `$ADMIN_JWT` / `$VENDOR_JWT` token placeholders with tokens returned by login. The invite request also contains a saved JWT; replace it with a fresh admin token.

## Project Structure

- `cmd/api/main.go`: Entry point for the server.
- `internal/core/`: Core configuration, models, server setup.
- `internal/features/`: Business logic for vendor, pos, admin, auth, callback, misc, rates.
- `internal/thirdparty/moneropay/`: MoneroPay API client and models.
- `internal/thirdparty/coinpaprika/`, `internal/thirdparty/frankfurter/`: Exchange rate API clients.

## Environment Variables

See `.env.example` for all required variables:

- `PORT`: Server port
- `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `DB_PORT`: Database settings
- `ADMIN_NAME`, `ADMIN_PASSWORD`: Required admin credentials
- `JWT_SECRET`, `JWT_REFRESH_SECRET`, `JWT_MONEROPAY_SECRET`: Required JWT secrets
- `JWT_LWS_TOKEN`: Required static token for the LWS callback endpoint
- `MONEROPAY_BASE_URL`, `MONEROPAY_CALLBACK_URL`: MoneroPay API settings
- `MONERO_WALLET_RPC_ENDPOINT`, `MONERO_WALLET_RPC_USERNAME`, `MONERO_WALLET_RPC_PASSWORD`: Wallet RPC settings (should be same as MoneroPay)
- `MONERO_DAEMON_RPC_ENDPOINT`, `MONERO_DAEMON_RPC_USERNAME`, `MONERO_DAEMON_RPC_PASSWORD`: Optional Monero daemon RPC for node status (`/api/misc/daemon-status`); username and password are needed when monerod runs with `--rpc-login`
- `TRUSTED_PROXY_HEADER`: Optional header for trusted proxy (for example, `X-Real-IP`)
- `FRANKFURTER_BASE_URL`: Optional, for a self-hosted Frankfurter instance (defaults to `https://api.frankfurter.dev`)
