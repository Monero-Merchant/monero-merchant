# Monero Merchant on Umbrel

This directory contains an isolated Umbrel packaging scaffold for Monero
Merchant. It keeps the existing root `docker-compose.yml`, `Makefile`, and VPS
installation path untouched while adding the one-click app-store layout required
by issue #30.

## What the app packages

- `umbrel-app.yml` exposes user configuration for daemon RPC host/port, admin
  credentials, wallet name, and zero-confirmation mode.
- `docker-compose.yml` runs Monero wallet RPC, MoneroPay, PostgreSQL for
  MoneroPay, PostgreSQL for the backend, and the Monero Merchant backend behind
  Umbrel's `app_proxy`.
- `backend-entrypoint.sh` creates a persistent `backend.env` in
  `${APP_DATA_DIR}/config`, generating JWT and wallet secrets from
  `/dev/urandom` on first boot and preserving them across app updates.
- All stateful wallet, database, and secret paths live under `${APP_DATA_DIR}`.

## Local smoke test

From the repository root:

```sh
APP_DATA_DIR="$(pwd)/.umbrel-test-data" \
APP_PASSWORD="change-me-in-production" \
APP_SEED="$(openssl rand -hex 32)" \
docker compose -f umbrel/docker-compose.yml config
```

On an Umbrel device, the UI is served through the app proxy on port `8080`.
Remote Android POS clients should use the backend URL shown by Umbrel, e.g.
`http://umbrel.local` on LAN or the app's Tor/Tailscale URL when those remote
access modes are enabled.

## Production follow-up before official app-store submission

The compose file builds the backend from this repository so reviewers can test
the bounty branch directly. For the official Umbrel app store, publish a pinned
multi-arch backend image and replace the `build:` block with an immutable image
tag plus digest.
