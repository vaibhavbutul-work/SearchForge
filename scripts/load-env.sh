#!/usr/bin/env bash
# scripts/load-env.sh
# Loads key-value pairs from .env into the current shell session.
# Usage (must be sourced):
#   source ./scripts/load-env.sh
#   or: . ./scripts/load-env.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Warning: .env file not found at $ENV_FILE. Copy .env.example to .env first."
    return 1 2>/dev/null || exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "Environment variables loaded successfully from .env!"
