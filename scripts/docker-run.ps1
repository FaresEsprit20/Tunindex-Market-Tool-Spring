# Builds fresh jars + images (Maven reactor for the backend, ng build for
# the frontend) and starts the whole stack. One script, run from anywhere:
#
#   .\scripts\docker-run.ps1

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

# Clean slate - drop anything left over from a previous run.
docker compose down

# Fresh jars and the frontend image. No --no-cache: Docker's layer cache is
# content-based (a changed source file busts it), not time-based, so this
# never reuses a stale jar - it just skips rebuilding what didn't change.
docker compose build

docker compose up -d

docker compose ps
