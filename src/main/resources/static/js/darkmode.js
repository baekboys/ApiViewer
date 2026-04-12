/* ═══════════════════════════════════════════════════════════════
 * URL Viewer — 다크모드 토글 (모든 페이지 공통)
 * <head> 최상단에서 로드 → 테마 깜빡임(FOUC) 방지
 * ═══════════════════════════════════════════════════════════════ */
(function () {
  var KEY = 'uv-theme';

  function getTheme() {
    var saved = localStorage.getItem(KEY);
    if (saved === 'dark' || saved === 'light') return saved;
    return (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
      ? 'dark' : 'light';
  }

  function applyTheme(t) {
    document.documentElement.setAttribute('data-theme', t);
  }

  function syncButtons(t) {
    document.querySelectorAll('.dark-toggle').forEach(function (btn) {
      btn.textContent = t === 'dark' ? '☀️ 라이트모드' : '🌙 다크모드';
      btn.title       = t === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환';
    });
  }

  /* 전역 토글 함수 — 클릭마다 현재 속성 값 기준으로 토글 (결과 일관성 보장) */
  window.toggleDarkMode = function () {
    var next = (document.documentElement.getAttribute('data-theme') === 'dark') ? 'light' : 'dark';
    localStorage.setItem(KEY, next);
    applyTheme(next);
    syncButtons(next);
  };

  /* 즉시 실행 (DOM 파싱 전 → FOUC 방지) */
  applyTheme(getTheme());

  /* DOM 로드 완료 후 버튼 텍스트 동기화 */
  document.addEventListener('DOMContentLoaded', function () {
    syncButtons(document.documentElement.getAttribute('data-theme') || 'light');
  });
})();
