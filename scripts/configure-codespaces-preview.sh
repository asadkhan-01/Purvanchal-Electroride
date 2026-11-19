#!/usr/bin/env bash
# Configure a private GitHub Codespaces Expo web preview for this repository.
# Only the forwarded URL goes into EXPO_PUBLIC_ (never secrets).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
codespace="${CODESPACE_NAME:-}"
if [[ -z "$codespace" ]]; then
  echo "CODESPACE_NAME is not set. Run this script in GitHub Codespaces." >&2
  exit 1
fi
if [[ ! "$codespace" =~ ^[a-zA-Z0-9-]+$ ]]; then
  echo "Invalid Codespaces name." >&2
  exit 1
fi

frontend="https://${codespace}-8081.app.github.dev"
backend="https://${codespace}-8080.app.github.dev"
envfile="$root/apps/mobile/.env.local"

if [[ -f "$envfile" ]]; then
  echo "Updating API URL in existing ignored $envfile"
  # Preserve other development-only settings without exposing them in output.
  tmp="$(mktemp)"
  trap 'rm -f "$tmp"' EXIT
  grep -v '^EXPO_PUBLIC_API_URL=' "$envfile" > "$tmp" || true
  printf 'EXPO_PUBLIC_API_URL=%s\n' "$backend" >> "$tmp"
  cat "$tmp" > "$envfile"
else
  printf 'EXPO_PUBLIC_API_URL=%s\n' "$backend" > "$envfile"
fi
chmod 600 "$envfile"

echo "Configured ignored Expo .env.local with private API URL:"
echo "  $backend"
echo "Phone web URL (forward port 8081, visibility Private):"
echo "  $frontend"
echo
echo "To allow the web origin in Spring Boot, launch/restart the API with:"
echo "  APP_CORS_ALLOWED_ORIGIN=$frontend mvn spring-boot:run"
echo "Run that from services/api and retain your existing DB/bootstrap environment variables."
echo "Restart Expo after applying the .env.local change:"
echo "  cd apps/mobile && npx expo start --web --port 8081 --clear"
echo
echo "Note: GitHub authentication on PRIVATE port 8080 may still block browser cross-origin fetch."
echo "Do not make the backend port public to bypass it. Use a same-origin proxy if required."
