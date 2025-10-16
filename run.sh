#!/usr/bin/env bash
set -euo pipefail
mvn -q clean package
mvn -q javafx:run
