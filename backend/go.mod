module github.com/monero-merchant/monero-merchant/backend

go 1.26.10

require (
	github.com/go-chi/chi/v5 v5.4.0
	github.com/go-chi/httprate v0.16.0
	github.com/icholy/digest v1.2.0
	github.com/joho/godotenv v1.5.1
)

require (
	github.com/BurntSushi/toml v1.4.1-0.20240526193622-a339e1f7089c // indirect
	github.com/klauspost/cpuid/v2 v2.2.10 // indirect
	github.com/zeebo/xxh3 v1.0.2 // indirect
	golang.org/x/exp/typeparams v0.0.0-20231108232855-2478ac86f678 // indirect
	golang.org/x/mod v0.42.0 // indirect
	golang.org/x/sys v0.49.0 // indirect
	golang.org/x/telemetry v0.0.0-20261010193011-f71dc92da45e // indirect
	golang.org/x/tools v0.52.0 // indirect
	golang.org/x/vuln v1.9.0 // indirect
	honnef.co/go/tools v0.8.2 // indirect
)

require (
	github.com/golang-jwt/jwt/v5 v5.3.1
	github.com/gorilla/websocket v1.5.3
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	github.com/jackc/pgx/v5 v5.10.0 // indirect
	github.com/jackc/puddle/v2 v2.2.2 // indirect
	github.com/jinzhu/inflection v1.0.0 // indirect
	github.com/jinzhu/now v1.1.5 // indirect
	github.com/matoous/go-nanoid/v2 v2.1.0
	golang.org/x/crypto v0.56.0
	golang.org/x/sync v0.24.0 // indirect
	golang.org/x/text v0.41.0 // indirect
	gorm.io/driver/postgres v1.6.2
	gorm.io/gorm v1.31.2
)

tool (
	golang.org/x/vuln/cmd/govulncheck
	honnef.co/go/tools/cmd/staticcheck
)
