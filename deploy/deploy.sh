#!/usr/bin/env bash
set -euo pipefail
: "${DEPLOY_HOST:?set DEPLOY_HOST}"
: "${DEPLOY_USER:?set DEPLOY_USER}"
: "${DEPLOY_ENV_FILE:?set path to server environment file}"
# Run explicitly when deployment is approved. No deployment happens during normal CI.
ssh "$DEPLOY_USER@$DEPLOY_HOST" 'mkdir -p ~/taasim/src'
scp src/cassandra_schema.cql "$DEPLOY_USER@$DEPLOY_HOST:~/taasim/src/cassandra_schema.cql"
scp docker-compose.demo.yml "$DEPLOY_USER@$DEPLOY_HOST:~/taasim/docker-compose.yml"
scp "$DEPLOY_ENV_FILE" "$DEPLOY_USER@$DEPLOY_HOST:~/taasim/.env"
scp docker-compose.published.yml "$DEPLOY_USER@$DEPLOY_HOST:~/taasim/docker-compose.published.yml"
ssh "$DEPLOY_USER@$DEPLOY_HOST" 'cd ~/taasim && chmod 600 .env && docker compose -f docker-compose.yml -f docker-compose.published.yml --profile ui pull && docker compose -f docker-compose.yml -f docker-compose.published.yml --profile ui up -d --no-build'
