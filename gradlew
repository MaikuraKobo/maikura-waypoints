#!/usr/bin/env sh
set -e
GRADLE_VERSION=9.2.0
GRADLE_DIR="$PWD/.gradle-local/gradle-$GRADLE_VERSION"
GRADLE_ZIP="$PWD/.gradle-local/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  echo "Downloading Gradle $GRADLE_VERSION..."
  mkdir -p "$PWD/.gradle-local"
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$GRADLE_ZIP"
  else
    wget -O "$GRADLE_ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  fi
  unzip -o "$GRADLE_ZIP" -d "$PWD/.gradle-local"
fi
exec "$GRADLE_DIR/bin/gradle" "$@"
