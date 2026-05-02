#!/bin/sh
# ApiViewer 재기동: stop.sh → (옵션) Chrome 캐시·localhost 쿠키 정리 → run.sh
#
#   sh restart.sh
#   sh restart.sh --no-build
#   sh restart.sh --clear-chrome
#   sh restart.sh --clear-chrome --no-build
#   CLEAR_CHROME=1 sh restart.sh

cd "$(dirname "$0")"

CLEAR_FLAG=0
NOBUILD=0
[ "${CLEAR_CHROME:-0}" = "1" ] && CLEAR_FLAG=1

for a in "$@"; do
  case "$a" in
    --clear-chrome) CLEAR_FLAG=1 ;;
    --no-build)    NOBUILD=1 ;;
  esac
done

sh stop.sh

if [ "$CLEAR_FLAG" = "1" ]; then
  sh scripts/clear-chrome-apiviewer-cache.sh || true
fi

if [ "$NOBUILD" = 1 ]; then
  exec sh run.sh --no-build
else
  exec sh run.sh
fi
