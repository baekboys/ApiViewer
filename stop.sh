#!/bin/sh
# ApiViewer 종료 스크립트
# 사용:
#   sh stop.sh
#   sh stop.sh --port 8080

PORT="8080"

if [ "$1" = "--port" ] && [ -n "$2" ]; then
  PORT="$2"
fi

cd "$(dirname "$0")"

echo "[INFO] ApiViewer 종료 시도 (port=$PORT)"

PID=""
if command -v lsof >/dev/null 2>&1; then
  PID="$(lsof -ti tcp:"$PORT" -sTCP:LISTEN 2>/dev/null | head -n 1)"
fi

if [ -z "$PID" ]; then
  echo "[WARN] 포트($PORT)에서 LISTEN 중인 프로세스를 찾지 못했습니다."
  echo "[INFO] 종료할 프로세스가 없으면 정상입니다."
  exit 0
fi

echo "[INFO] 대상 PID=$PID (SIGTERM)"
kill "$PID" 2>/dev/null || true

# 최대 5초 대기
i=0
while [ $i -lt 10 ]; do
  if kill -0 "$PID" 2>/dev/null; then
    sleep 0.5
    i=$((i+1))
  else
    echo "[INFO] 종료 완료"
    exit 0
  fi
done

echo "[WARN] 종료 지연 — SIGKILL"
kill -9 "$PID" 2>/dev/null || true
echo "[INFO] 종료 완료(강제)"
exit 0

