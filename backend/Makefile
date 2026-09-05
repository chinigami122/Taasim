.PHONY: test verify build docker up down logs

test:
	mvn -B test

verify:
	mvn -B verify

build:
	mvn -B clean package -DskipTests

docker:
	docker compose -f ../docker-compose.backend.yml build

up:
	docker compose -f ../docker-compose.yml up -d
	docker compose -f ../docker-compose.backend.yml up -d

down:
	docker compose -f ../docker-compose.backend.yml down
	docker compose -f ../docker-compose.yml down

logs:
	docker compose -f ../docker-compose.backend.yml logs -f
