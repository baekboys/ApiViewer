#!/bin/sh
# ApiViewer 실행 스크립트
# 빌드 후 실행: sh run.sh
# 빌드 없이 실행 (JAR 이미 있을 때): sh run.sh --no-build
# VS Code/Cursor preLaunchTask: APIVIEWER_IDE_LAUNCH=1 또는 sh run.sh --ide
#   → Chrome 자동 실행 생략(IDE가 브라우저 연결). 로그에 Started … 까지 출력됨.

MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
JAR="target/api-viewer-1.0.0.jar"

cd "$(dirname "$0")"

NO_BUILD=0
IDE_LAUNCH=0
for _a in "$@"; do
  if [ "$_a" = "--no-build" ]; then NO_BUILD=1; fi
  if [ "$_a" = "--ide" ]; then IDE_LAUNCH=1; fi
done

# ── UI 버전(캐시 확인용) — static/common/nav.js 의 APP_UI_VERSION 파싱 ──
UI_VER="$(sed -n 's/.*APP_UI_VERSION[[:space:]]*=[[:space:]]*[\"\x27]\([^\"\x27]*\)[\"\x27].*/\1/p' "./src/main/resources/static/common/nav.js" 2>/dev/null | head -n 1)"
if [ -z "$UI_VER" ]; then UI_VER="(unknown)"; fi
ANSI_BOLD="$(printf '\033[1m')"
ANSI_BLINK="$(printf '\033[5m')"
ANSI_PURPLE="$(printf '\033[35m')"
ANSI_CYAN="$(printf '\033[36m')"
ANSI_YELLOW="$(printf '\033[33m')"
ANSI_RESET="$(printf '\033[0m')"

echo ""
printf "%s%s══════════════════════════════════════════════════════════════%s\n" "$ANSI_BOLD" "$ANSI_CYAN" "$ANSI_RESET"
printf "%s%s%s  █████╗ ██████╗ ██╗██╗   ██╗██╗███████╗██╗    %s\n" "$ANSI_BOLD" "$ANSI_PURPLE" "$ANSI_BLINK" "$ANSI_RESET"
printf "%s%s  ██╔══██╗██╔══██╗██║██║   ██║██║██╔════╝██║    %s\n" "$ANSI_BOLD" "$ANSI_PURPLE" "$ANSI_RESET"
printf "%s%s  ███████║██████╔╝██║██║   ██║██║█████╗  ██║    %s\n" "$ANSI_BOLD" "$ANSI_PURPLE" "$ANSI_RESET"
printf "%s%s  ██╔══██║██╔═══╝ ██║╚██╗ ██╔╝██║██╔══╝  ██║    %s\n" "$ANSI_BOLD" "$ANSI_PURPLE" "$ANSI_RESET"
printf "%s%s  ██║  ██║██║     ██║ ╚████╔╝ ██║███████╗███████╗%s\n" "$ANSI_BOLD" "$ANSI_PURPLE" "$ANSI_RESET"
printf "%s%s  ╚═╝  ╚═╝╚═╝     ╚═╝  ╚═══╝  ╚═╝╚══════╝╚══════╝%s\n" "$ANSI_BOLD" "$ANSI_PURPLE" "$ANSI_RESET"
printf "%s%s  UI VERSION  %s%s%s%s\n" "$ANSI_BOLD" "$ANSI_CYAN" "$ANSI_YELLOW" "$ANSI_BOLD" "$UI_VER" "$ANSI_RESET"
printf "%s%s══════════════════════════════════════════════════════════════%s\n" "$ANSI_BOLD" "$ANSI_CYAN" "$ANSI_RESET"

if [ "$NO_BUILD" != "1" ]; then
  echo "[INFO] 빌드 중..."
  "$MVN" -q package -DskipTests 2>&1 | grep -v "sun.misc"
fi

if [ ! -f "$JAR" ]; then
  echo "[ERROR] JAR 파일이 없습니다. 먼저 빌드하세요."
  exit 1
fi

echo ""
echo "================================================"
echo "  API Viewer 시작"
echo "------------------------------------------------"
echo "  📊 대시보드      : http://localhost:8080/"
echo "  📋 URL분석현황   : http://localhost:8080/viewer.html"
echo "  📈 URL호출현황   : http://localhost:8080/call-stats.html"
echo "  📝 현업검토     : http://localhost:8080/review.html"
echo "  🚧 차단모니터링  : http://localhost:8080/url-block-monitor.html"
echo "  🗺️  업무플로우   : http://localhost:8080/workflow.html"
echo "  ⚙️  설정         : http://localhost:8080/settings.html"
echo "  🔍 URL분석(추출) : http://localhost:8080/extract.html"
echo "------------------------------------------------"
echo "  🗄  H2 콘솔      : http://localhost:8080/h2-console"
echo "    JDBC URL      : jdbc:h2:file:./data/api-viewer-db"
echo "    User / Pass   : sa / (없음)"
echo "================================================"
echo "  종료: Ctrl+C"
echo "================================================"
echo ""

# ── 서버 실행 (백그라운드) ───────────────────────────────────
java -Djava.net.preferIPv4Stack=true -jar "$JAR" &
APP_PID=$!

cleanup() {
  if [ -n "$APP_PID" ]; then
    kill "$APP_PID" 2>/dev/null || true
  fi
}
trap cleanup INT TERM

# ── IDE 연동: 서버만 띄우고 Chrome은 IDE(또는 Touch Bar 디버그)가 연결 ──
if [ "${APIVIEWER_IDE_LAUNCH:-0}" = "1" ] || [ "$IDE_LAUNCH" = "1" ]; then
  echo "[INFO] IDE 모드 — Chrome 자동 실행 생략 (서버만 유지, 종료는 stop.sh 또는 작업 중단)"
  wait "$APP_PID"
  exit 0
fi

# ── 기동 직후 잠깐 대기 후, 서버 준비 대기/Chrome 오픈 ─────────
# 서버가 아직 포트 바인딩 전인 구간에 Chrome을 먼저 띄우면
# "페이지를 찾을 수 없음" 화면이 먼저 뜰 수 있어 약간 대기한다.
sleep 5

# ── 서버 준비 대기 후, Chrome 오픈 ────────────────────────────
URL="http://localhost:8080/dashboard/"
USE_TMP_PROFILE="${USE_TMP_PROFILE:-0}"
TMP_PROFILE=""
if [ "$USE_TMP_PROFILE" = "1" ]; then
  TMP_PROFILE="$(mktemp -d 2>/dev/null || mktemp -d -t apiviewer-chrome)"
  echo "[INFO] Chrome 임시 프로필 사용: $TMP_PROFILE"
else
  echo "[INFO] Chrome 기존 프로필 사용 (가능하면 탭으로 열림)"
fi
echo "[INFO] 서버 준비 대기 중... ($URL)"

i=0
READY="0"
while [ $i -lt 60 ]; do
  code="$(curl -s -o /dev/null -w "%{http_code}" "$URL" || echo "000")"
  if [ "$code" != "000" ]; then
    READY="1"
    break
  fi
  sleep 0.5
  i=$((i+1))
done

if [ "$READY" = "1" ]; then
  # macOS: 화면 사용 가능 영역(desktop bounds)으로 창을 "꽉" 채움
  # - Chrome이 이미 떠 있으면: 탭으로 열고 front window 크기 강제 조정
  # - Chrome이 꺼져 있으면: 새 창으로 열고 크기/위치 지정 + bounds 강제 조정
  CHROME_WIN_ARGS=""
  CHROME_SET_BOUNDS_CMD=""
  if command -v osascript >/dev/null 2>&1; then
    # Finder desktop bounds: "0, 0, 1440, 900" (left, top, right, bottom)
    bounds="$(osascript -e 'tell application "Finder" to get bounds of window of desktop' 2>/dev/null | tr -d ' ')"
    if [ -n "$bounds" ]; then
      OLDIFS="$IFS"
      IFS=','; set -- $bounds; IFS="$OLDIFS"
      x1="$1"; y1="$2"; x2="$3"; y2="$4"
      case "$x1$x2$y1$y2" in
        *[!0-9]*)
          ;;
        *)
          sw=$((x2 - x1))
          sh=$((y2 - y1))
          if [ "$sw" -gt 0 ] && [ "$sh" -gt 0 ]; then
            # desktop bounds 기준으로 꽉 채움
            ww=$sw
            wh=$sh
            px=0
            py=0
            CHROME_WIN_ARGS="--new-window --window-size=${ww},${wh} --window-position=${px},${py}"
            # 이미 떠 있는 Chrome에도 적용되도록 bounds를 한 번 더 강제
            CHROME_SET_BOUNDS_CMD="tell application \"Google Chrome\" to if (count of windows) > 0 then set bounds of front window to {${x1}, ${y1}, ${x2}, ${y2}}"
          fi
          ;;
      esac
    fi
  fi

  # 기존 Chrome이 떠 있으면 "탭으로" 열기 (임시 프로필 모드일 때는 항상 새 인스턴스)
  if pgrep -x "Google Chrome" >/dev/null 2>&1 && [ "$USE_TMP_PROFILE" != "1" ]; then
    echo "[INFO] Chrome 탭으로 열기: $URL"
    open -a "Google Chrome" "$URL" >/dev/null 2>&1 || true
    if [ -n "$CHROME_SET_BOUNDS_CMD" ] && command -v osascript >/dev/null 2>&1; then
      osascript -e "$CHROME_SET_BOUNDS_CMD" >/dev/null 2>&1 || true
    fi
  else
    echo "[INFO] Chrome 실행: $URL"
    if [ "$USE_TMP_PROFILE" = "1" ] && [ -n "$TMP_PROFILE" ]; then
      open -na "Google Chrome" --args --user-data-dir="$TMP_PROFILE" --no-first-run --no-default-browser-check $CHROME_WIN_ARGS "$URL" >/dev/null 2>&1 || true
    else
      open -a "Google Chrome" --args --no-first-run --no-default-browser-check $CHROME_WIN_ARGS "$URL" >/dev/null 2>&1 || true
    fi
    if [ -n "$CHROME_SET_BOUNDS_CMD" ] && command -v osascript >/dev/null 2>&1; then
      osascript -e "$CHROME_SET_BOUNDS_CMD" >/dev/null 2>&1 || true
    fi
  fi
else
  echo "[WARN] 서버 응답을 확인하지 못했습니다. Chrome 자동 실행을 건너뜁니다."
fi

# ── 로그 유지 (Ctrl+C로 종료) ────────────────────────────────
wait "$APP_PID"
