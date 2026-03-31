#!/bin/sh
# ApiViewer 실행 스크립트
# 빌드 후 실행: sh run.sh
# 빌드 없이 실행 (JAR 이미 있을 때): sh run.sh --no-build

MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
JAR="target/api-viewer-1.0.0.jar"

cd "$(dirname "$0")"

if [ "$1" != "--no-build" ]; then
  echo "[INFO] 빌드 중..."
  "$MVN" -q package -DskipTests 2>&1 | grep -v "sun.misc"
fi

if [ ! -f "$JAR" ]; then
  echo "[ERROR] JAR 파일이 없습니다. 먼저 빌드하세요."
  exit 1
fi

echo "[INFO] 서버 시작: http://localhost:8080"
java -jar "$JAR"
