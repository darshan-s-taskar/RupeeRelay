#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -z "${JAVA_HOME:-}" ] && command -v java >/dev/null 2>&1; then
  DETECTED_JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.home = //p' | head -n 1 || true)
  if [ -n "$DETECTED_JAVA_HOME" ]; then
    export JAVA_HOME="$DETECTED_JAVA_HOME"
  fi
fi

exec mvn -f "$BASE_DIR/pom.xml" "$@"
