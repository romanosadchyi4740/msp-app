#!/usr/bin/env bash
# Starts (or updates) the stack in /opt/syncchord (KAN-104). Run at every boot by syncchord.service, and by hand after
# changing .env or the Compose file. `syncchord-up.sh --pull` pulls newer images first.
#
# The instance has no Elastic IP, so its public address changes on every stop/start. This script reads the current
# public and private IP from the instance metadata (IMDSv2), points the DuckDNS name at the public one, and hands
# both to Compose for coturn's external-ip.
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

# Reads one variable from .env without executing the file
env_value() {
  sed -n "s/^$1=//p" .env | tail -n 1 | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/"
}

imds() {
  curl -fsS --max-time 5 -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" "http://169.254.169.254/latest/meta-data/$1"
}

IMDS_TOKEN=$(curl -fsS --max-time 5 -X PUT -H "X-aws-ec2-metadata-token-ttl-seconds: 300" \
  http://169.254.169.254/latest/api/token)
PUBLIC_IP=$(imds public-ipv4)
PRIVATE_IP=$(imds local-ipv4)
export PUBLIC_IP PRIVATE_IP
echo "Public IP $PUBLIC_IP, private IP $PRIVATE_IP"

APP_DOMAIN=$(env_value APP_DOMAIN)
DUCKDNS_TOKEN=$(env_value DUCKDNS_TOKEN)
if [[ -n "$DUCKDNS_TOKEN" && "$APP_DOMAIN" == *.duckdns.org ]]; then
  # The token is sent in the query string as DuckDNS requires, but never printed
  answer=$(curl -fsS --max-time 15 --get "https://www.duckdns.org/update" \
    --data-urlencode "domains=${APP_DOMAIN%.duckdns.org}" \
    --data-urlencode "token=$DUCKDNS_TOKEN" \
    --data-urlencode "ip=$PUBLIC_IP" || true)
  if [[ "$answer" == OK* ]]; then
    echo "DuckDNS: $APP_DOMAIN -> $PUBLIC_IP"
  else
    echo "DuckDNS update failed (check DUCKDNS_TOKEN and APP_DOMAIN in .env); continuing" >&2
  fi
else
  echo "No DUCKDNS_TOKEN or not a duckdns.org name: point $APP_DOMAIN at $PUBLIC_IP yourself"
fi

if [[ "${1:-}" == "--pull" ]]; then
  docker compose -f docker-compose.prod.yml pull
fi
# Recreates only the containers whose settings changed, e.g. coturn after a new public IP
docker compose -f docker-compose.prod.yml up -d --remove-orphans
