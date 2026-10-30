# Monero Merchant
**Free and Open Source Monero Point of Sale (POS)**

<img width="500" src="https://github.com/user-attachments/assets/ac8414eb-4d28-4609-a4fe-4144e17d46f7" alt="Monero Merchant screenshot" />

---

**Monero Merchant** is a FOSS Android point-of-sale (POS) system for accepting Monero (XMR) payments. It provides a **self-hosted**, **trustless**, and **secure** payment solution for vendors and merchants.

---

## Features
- **Open Source:** Licensed under GNU GPL v3.0.  
- **Trustless Architecture:** Operate your own backend; no reliance on third parties.  
- **Device-Agnostic:** Works on any Android device — no proprietary hardware.  
- **Receipt Printing:** Supports Bluetooth ESC/POS printers.  
- **Scalable:** Unlimited POS clients and vendors, centrally managed.  
- **Secure:** No wallet keys stored or exposed on client devices.  
- **Integrated Backend:** Backend API interfaces with `monerod` and **MoneroPay**.  
- **Fast Payments:** Supports 0-confirmation Monero transactions in 5–10 seconds.

---

## Compatibility
- **Client:** Any Android device, with or without a Bluetooth mobile printer.  
- **Backend:** Ubuntu LTS VPS or LAN environment (low-spec compatible).  

---

## Backend Setup

### Prerequisites
- Docker with the Compose plugin
- OpenSSL

### Install

```
mkdir monero-merchant && cd monero-merchant
curl -fLO https://github.com/Monero-Merchant/monero-merchant/releases/latest/download/docker-compose.yml
curl -fLO https://github.com/Monero-Merchant/monero-merchant/releases/latest/download/example.env
curl -fLO https://github.com/Monero-Merchant/monero-merchant/releases/latest/download/setup.sh
bash setup.sh
docker compose up -d
```

`setup.sh` creates `.env` with generated passwords. Run it as your normal user. If your user can't run `docker` directly, prefix the `docker` commands with `sudo`. `MONERO_MERCHANT_VERSION` in `.env` picks the release: `2` follows the latest 2.x, or pin one like `2.2.0`.

To update, run `docker compose pull && docker compose up -d`.

The vendor portal is at `http://<server>:8080/`, the admin dashboard at `/admin/` (log in with `ADMIN_NAME` and `ADMIN_PASSWORD` from `.env`), and POS devices connect to `http://<server>:8080`. For HTTPS, point a domain at the server, set `SITE_ADDRESS=<domain>`, `HTTP_PORT=80` and `HTTPS_PORT=443` in `.env`, and run `docker compose up -d`.

### Using your own reverse proxy

The `gateway` service is Caddy with [gateway/Caddyfile](gateway/Caddyfile). To use your own proxy instead, remove it and route:

- `/api` and `/api/*` to `backend:8080`, with WebSocket upgrades allowed
- everything else to `frontend:8080`

The proxy must overwrite `X-Real-IP` with the client address, and the backend must only be reachable through it. Otherwise clients can fake their IP and get around the rate limits.

### Wallet

MoneroPay creates the server wallet in `moneropay/data/wallet/` on first start. It has no password, so keep that folder private, and back up the seed under Settings in the admin dashboard.

To restore the same wallet from its seed:

```
docker compose stop moneropay monero-wallet-rpc
sudo mv moneropay/data/wallet moneropay/data/wallet.old
docker compose up -d monero-wallet-rpc
```

Then use "Restore from Seed" on the dashboard's home page, and run `docker compose up -d`. Restoring a different wallet this way breaks pending sales, because MoneroPay's database still refers to the old one.

### Start / Stop / Logs

`docker compose up -d` # start, or apply `.env` changes

`docker compose down` # stop

`docker compose ps` # show running containers

`docker compose logs -f` # stream logs

To uninstall, run `docker compose down -v` and then `sudo rm -rf` the folder. This deletes the database and the wallet.

### From Source

For development, clone the repository and use the Makefile. `make install` runs `setup.sh` and starts the published images, `make rebuild` builds the backend, frontend and gateway from your checkout as `:dev` images and runs them, `make up` switches back to the published images, and `make help` lists the rest.

---

> For detailed backend API usage, see [`backend/README.md`](backend/README.md).

---

## Install the POS App

Download `monero-merchant-pos-<version>.apk` from the [latest release](https://github.com/Monero-Merchant/monero-merchant/releases/latest). Releases are signed with this certificate, which you can check with [AppVerifier](https://github.com/soupslurpr/AppVerifier):

```
com.moneromerchant.pos
0A:69:C3:F6:60:E8:12:DB:CC:99:D7:5B:47:B7:3F:F9:9E:F7:D5:2B:3A:AA:9A:58:EF:BD:3E:20:7B:D1:87:A8
```

`gh attestation verify <apk> --owner Monero-Merchant` checks that the APK was built by this repo's CI.

---

## Taking Payments

1. Enter the amount in your `primaryFiatCurrency` and tap the **green button**.
2. The app generates a Monero payment QR or NFC tag with address, amount, and note prefilled.
   The customer scans it and sends funds using any Monero wallet.
3. Once the payment reaches the configured confirmation threshold (0, 1, or 10),
   the app automatically advances to the receipt screen where you can:

   * Print a receipt
   * Start a new order

---

## Settings Overview

### Company Information

* Upload a logo (appears on receipts)
* Edit company name and contact details
* Customize footer text

### Fiat Currencies

* Configure the `primaryFiatCurrency`
* Add multiple `referenceFiatCurrencies`

### Security

* Enable PIN protection for app startup or settings access
* **Note:** PINs cannot currently be reset — choose carefully

### Printer Settings

* Select connection type (Bluetooth tested and supported)
* Adjust printer parameters if needed
* Use **Test Print** to verify output

---

## Building Monero Merchant Client from Source

### Prerequisites

* [Android Studio](https://developer.android.com/studio)

### 1. Clone the Repository

```bash
git clone https://github.com/Monero-Merchant/monero-merchant
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Select **Open an Existing Project**
3. Navigate to `monero-merchant/pos` and open it

### 3. Install Dependencies

Android Studio auto-installs dependencies.
If not, manually sync Gradle:

```
File > Sync Project with Gradle Files
```

### 4. Choose Build Variant

```
View > Tool Windows > Build Variants
```

Select `debug` or `release`.

### 5. Build the APK

#### GUI method:

```
Build > Build APK(s)
```

#### Command line:

```bash
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build (unsigned)
```

Output: `app/build/outputs/apk/`

---

## Building with Docker

### Requirements

* Docker Engine ≥ 24
* Docker Compose plugin
* 8 GB RAM, ~10 GB free disk space
* User added to `docker` group

### Build

```bash
git clone https://github.com/Monero-Merchant/monero-merchant
cd monero-merchant/pos
docker compose build --no-cache
docker compose up --abort-on-container-exit
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Donations

Support the project with Monero (XMR):

```
88zkpYQRJPmeuycSN7Jx3UHq9vH1u2dD8eE1rECvCAouPj75Cdnu1eUacQ5p7ZMvdr4e6BRe2FShv4HoatSs9HcwEeZCupZ
```

---

## License

Licensed under the **GNU General Public License v3.0**.
See [LICENSE](LICENSE) for details.
