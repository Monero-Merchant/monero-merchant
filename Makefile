.PHONY: install up down logs ps restart config pull build rebuild reset clean help

install:
	./setup.sh
	$(MAKE) build
	docker compose up -d

up:
	docker compose up -d

build:
	docker build -t moneromerchant/monero-merchant-backend ./backend

rebuild:
	$(MAKE) build
	docker compose up -d --force-recreate backend

down:
	docker compose down

logs:
	docker compose logs -f

ps:
	docker compose ps

restart:
	docker compose down
	docker compose up -d

config:
	docker compose config

pull:
	docker compose pull

reset:
	docker compose down -v --remove-orphans
	rm -f .env
	sudo rm -rf moneropay
	sudo rm -rf simple-monero-wallet-rpc-docker

clean:
	docker compose down --remove-orphans
	docker builder prune -f

help:
	@echo "Available targets:"
	@echo "  make install   - run setup.sh, build the backend, and start services"
	@echo "  make up        - start existing containers"
	@echo "  make build     - build the backend image from local source"
	@echo "  make rebuild   - build local backend source and recreate its container"
	@echo "  make down      - stop containers"
	@echo "  make logs      - follow container logs"
	@echo "  make ps        - show container status"
	@echo "  make restart   - restart containers"
	@echo "  make config    - show resolved docker compose config"
	@echo "  make pull      - pull remote images"
	@echo "  make reset     - DELETE containers, database/wallet data, .env, and cloned deps"
	@echo "  make clean     - remove containers and prune builder cache; preserve application data"
