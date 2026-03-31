#!/bin/sh
# Maven Wrapper script
MAVEN_WRAPPER_JAR="$( cd "$( dirname "$0" )" && pwd )/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$( cd "$( dirname "$0" )" && pwd )/.mvn/wrapper/maven-wrapper.properties"
DOWNLOAD_URL=$(grep distributionUrl "$MAVEN_WRAPPER_PROPERTIES" | sed 's/distributionUrl=//')

MVN_DIR="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"
MVN_BIN="$MVN_DIR/bin/mvn"

if [ ! -f "$MVN_BIN" ]; then
  echo "[INFO] Maven 3.9.6 다운로드 중..."
  mkdir -p "$MVN_DIR"
  TMPZIP=$(mktemp /tmp/maven.XXXXXX.zip)
  curl -sL "$DOWNLOAD_URL" -o "$TMPZIP"
  unzip -q "$TMPZIP" -d "$HOME/.m2/wrapper/dists/"
  EXTRACTED=$(find "$HOME/.m2/wrapper/dists/" -maxdepth 1 -name "apache-maven-3.9.6" -type d 2>/dev/null | head -1)
  if [ -z "$EXTRACTED" ]; then
    EXTRACTED=$(find "$HOME/.m2/wrapper/dists/" -maxdepth 2 -name "mvn" | head -1 | xargs -I{} dirname {} | xargs -I{} dirname {})
  fi
  rm -f "$TMPZIP"
fi

exec "$MVN_BIN" "$@"
