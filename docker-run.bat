@echo off
if "%~1"=="" (
    docker compose run --rm app smoke-test
) else (
    docker compose run --rm app %*
)

