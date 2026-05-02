# Umbrel Integration

This directory contains files for one-click installation of Monero Merchant on Umbrel home servers.

## Installation

1. Open Umbrel app store
2. Search for "Monero Merchant" or sideload this app
3. Click Install
4. Configure your Monero daemon and wallet RPC settings
5. Set an admin password
6. Click Save and wait for installation to complete

## Configuration

After installation, access the app at `http://<umbrel-ip>:9090`

### Required Settings

- **Monero Daemon RPC Hostname**: The hostname of your Monero full node
  - Public nodes: `node.sethforprivacy.com`, `nodes.hashvault.pro`
  - Or run your own Monero node on Umbrel
- **Monero Daemon RPC Port**: Usually 18081 for mainnet
- **Wallet RPC Password**: Password for the wallet RPC interface
- **Admin Password**: Password for the POS dashboard

## Architecture

- **server**: Monero Merchant backend (Go)
- **app_proxy**: Umbrel's nginx proxy for web access
- **data volume**: Persistent storage for transaction database

## Troubleshooting

- **Cannot connect to daemon**: Verify the daemon hostname and port
- **Wallet sync slow**: Use a trusted public node or run your own
- **App not accessible**: Check Umbrel network settings
