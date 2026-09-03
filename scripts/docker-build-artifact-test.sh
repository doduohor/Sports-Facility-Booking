#!/usr/bin/env bash
set -Eeuo pipefail

expected_copy='COPY --from=build /workspace/build/libs/*-all.jar /app/app.jar'

grep --fixed-strings --line-regexp "$expected_copy" Dockerfile
