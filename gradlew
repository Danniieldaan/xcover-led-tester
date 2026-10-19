#!/bin/sh
# Gradle wrapper stub — downloads and runs the distribution from gradle/wrapper/gradle-wrapper.properties.
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" >/dev/null && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "gradle-wrapper.jar missing. Install Gradle or run: gradle wrapper" >&2
  echo "Alternatively build in the cloud with the GitHub Actions workflow (.github/workflows/build-apk.yml)." >&2
  exit 1
fi
JAVA_EXE="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVA_EXE" -jar "$WRAPPER_JAR" "$@"
