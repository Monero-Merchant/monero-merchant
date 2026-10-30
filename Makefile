.PHONY: install update up down logs ps restart config pull build rebuild reset clean help
.DEFAULT_GOAL := help

install:
	./setup.sh
	docker compose up -d

update:
	docker compose pull
	docker compose up -d

up:
	docker compose up -d

build:
	docker compose -f docker-compose.yml -f docker-compose.build.yml build

rebuild:
	docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build backend frontend gateway

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

clean:
	docker compose down --remove-orphans
	docker builder prune -f

help:
	@echo "Available targets:"
	@echo "  make install   - run setup.sh and start services"
	@echo "  make update    - pull the images for MONERO_MERCHANT_VERSION and restart"
	@echo "  make up        - start existing containers"
	@echo "  make build     - build the backend, frontend and gateway from local source as :dev images"
	@echo "  make rebuild   - build local source and run the :dev images"
	@echo "  make down      - stop containers"
	@echo "  make logs      - follow container logs"
	@echo "  make ps        - show container status"
	@echo "  make restart   - restart containers"
	@echo "  make config    - show resolved docker compose config"
	@echo "  make pull      - pull remote images"
	@echo "  make reset     - DELETE containers, database/wallet data and .env"
	@echo "  make clean     - remove containers and prune builder cache; preserve application data"
