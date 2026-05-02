#!/bin/sh
# macOS: Google Chrome 종료 후 Default 프로필의 디스크 캐시 일부 삭제 +
#        Cookies SQLite 에서 localhost / 127.0.0.1 관련 행만 삭제 (다른 사이트 로그인 유지).
# 사용: sh scripts/clear-chrome-apiviewer-cache.sh
#       또는 restart.sh --clear-chrome

if [ "$(uname -s)" != "Darwin" ]; then
  echo "[WARN] clear-chrome-apiviewer-cache.sh 는 macOS 전용입니다. 스킵합니다."
  exit 0
fi

CHROME_SUP="${CHROME_USER_DATA_ROOT:-$HOME/Library/Application Support/Google/Chrome}"
PROFILE="${CHROME_PROFILE_NAME:-Default}"
PRO_DIR="$CHROME_SUP/$PROFILE"

if [ ! -d "$PRO_DIR" ]; then
  echo "[WARN] Chrome 프로필이 없습니다: $PRO_DIR"
  exit 0
fi

echo "[INFO] Google Chrome 종료 요청..."
osascript -e 'tell application "Google Chrome" to if it is running then quit' 2>/dev/null || true
sleep 2

if pgrep -x "Google Chrome" >/dev/null 2>&1; then
  echo "[WARN] Chrome이 남아 있어 killall 시도합니다."
  killall "Google Chrome" 2>/dev/null || true
  sleep 1
fi

echo "[INFO] 프로필 캐시 디렉터리 삭제: $PRO_DIR"
for d in "Cache" "Code Cache" "GPUCache" "Service Worker/CacheStorage"; do
  p="$PRO_DIR/$d"
  if [ -e "$p" ]; then
    rm -rf "$p" 2>/dev/null && echo "  - removed: $d" || echo "  - skip (잠금/권한): $d"
  fi
done

# 시스템 캐시(일부 리소스) — 없을 수 있음
SYS_CACHE="$HOME/Library/Caches/Google/Chrome"
if [ -d "$SYS_CACHE" ]; then
  rm -rf "$SYS_CACHE" 2>/dev/null && echo "  - removed: ~/Library/Caches/Google/Chrome" || true
fi

COOK="$PRO_DIR/Network/Cookies"
if [ ! -f "$COOK" ]; then
  COOK="$PRO_DIR/Cookies"
fi

if [ -f "$COOK" ] && command -v sqlite3 >/dev/null 2>&1; then
  echo "[INFO] localhost / 127.0.0.1 쿠키만 DB에서 삭제..."
  if sqlite3 "$COOK" "DELETE FROM cookies WHERE host_key LIKE '%localhost%' OR host_key LIKE '%127.0.0.1%';" 2>/dev/null; then
    echo "  - cookies 갱신 완료"
  else
    echo "  - cookies 스킵 (DB 잠금 또는 스키마 차이 — Chrome 완전 종료 후 재시도)"
  fi
else
  echo "[INFO] sqlite3 없음 또는 Cookies DB 없음 — 쿠키 SQL 스킵"
fi

echo "[INFO] Chrome 캐시·localhost 쿠키 정리 끝"
