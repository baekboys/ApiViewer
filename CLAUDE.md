# URL Viewer — Claude Code 프로젝트 컨텍스트

> 소스코드나 비즈니스로직 변경 시 이 파일을 업데이트할 것

---

# 프로젝트 개요

Spring Boot 기반 웹 애플리케이션. Controller 소스를 파싱하여 URL 목록을 추출하고, H2 DB에 저장·조회·관리하는 내부 도구.

---

# 기술 스택

| 항목 | 버전/도구 |
|------|----------|
| Java | 17 |
| Spring Boot | 3.4.0 |
| ORM | Spring Data JPA |
| DB | H2 2.3.232 (파일 모드 `./data/api-viewer-db`) |
| Parser | JavaParser 3.25.10 + Regex 폴백 |
| 배치 | Spring Quartz |
| 엑셀 | ExcelJS (로컬 번들, CDN 사용 금지) |
| 빌드 | Maven |

---

# 환경 제약

| 항목 | 내용 |
|------|------|
| 네트워크 | 망분리 환경, 외부 CDN/인터넷 접근 불가 |
| 라이브러리 | 로컬 번들 또는 내부 Maven 저장소만 사용 |
| ExcelJS | `/exceljs.min.js` 로컬 파일 사용 (CDN 절대 금지) |

---

# 실행 방법

| 명령 | 설명 |
|------|------|
| `sh run.sh` | 빌드 후 실행 |
| `sh run.sh --no-build` | JAR만 실행 |
| `run.bat` | Windows |

---

# 접속 URL

| URL | 설명 | 접근 |
|-----|------|------|
| `/` | 대시보드 (5가지 상태 통계) | 공개 |
| `/extract.html` | URL 분석 | 관리자 전용 |
| `/viewer.html` | 이력 조회 | 공개 |
| `/review.html` | 현업 검토 (차단대상만) | 공개 |
| `/settings.html` | 설정·로그·배치·데이터관리 | 관리자 전용 |
| `/h2-console` | H2 DB 콘솔 (sa / 빈 패스워드) | 관리자 전용 |

---

# 상태 (status) — 5가지

| 상태 | 자동 계산 조건 | 비고 |
|------|---------------|------|
| 차단완료 | ①@Deprecated ②[URL차단작업] 주석 ③UnsupportedOperationException — 3가지 AND | 모든 수정 불가 |
| 최우선 차단대상 | 호출 0건 + 커밋 1년 경과 | 자동 |
| 후순위 차단대상 | 수동 설정 (침해사고 로그, IT담당자 검토건) | 수동 |
| 검토필요 차단대상 | 호출 0건+커밋 1년 미만 / 호출 1~N건+커밋 1년 경과 | 자동 |
| 사용 | 기본값 | — |

| 상태확정 | 설명 |
|----------|------|
| 확정 | `statusOverridden=true`, 자동 재계산 안 함, 수정 불가 (파란 배지) |
| 미확정 | `statusOverridden=false`, 자동 재계산 대상 (회색 배지) |
| 변경불가 | 차단완료 건, 일체 수정 불가 (비활성 배지) |

---

# 인증

| 항목 | 내용 |
|------|------|
| 방식 | 서버 측 UUID 토큰 (AuthService, 8시간 TTL) |
| 발급 | `POST /api/verify-password` → 토큰 반환 |
| 전송 | `X-Admin-Token` 헤더에 토큰 포함 |
| 검증 | AdminInterceptor가 보호 경로에서 토큰 검증 |
| 보호 경로 | `/api/extract`, `/api/config/**`, `/api/logs/**`, `/api/schedule/**`, `/api/db/delete-all`, `/api/db/seed` |
| 공개 경로 | `GET /api/config/global`, `GET /api/config/repos` |

---

# 주요 DB 컬럼 (api_record)

| 컬럼 | 설명 |
|------|------|
| `(repository_name, api_path, http_method)` | UNIQUE 복합키 |
| `status` | 5가지 상태값 |
| `status_overridden` | 상태확정 여부 (boolean) |
| `has_url_block` | UnsupportedOperationException throw 여부 (Y/N) |
| `is_deprecated` | @Deprecated 어노테이션 여부 (Y/N) |
| `controller_file_path` | /{repoName}/{repoPath} |
| `call_count` / `call_count_month` / `call_count_week` | 호출건수 3분할 (총/1달/1주) |
| `block_criteria` | 차단기준 텍스트 |
| `team_override` / `manager_override` | 레코드별 팀/담당자 오버라이드 |
| `manager_mappings` (repo_config) | 프로그램ID별 담당자 매핑 JSON. 매칭 없으면 `managerName`(팀 대표)로 폴백 |
| `blocked_date` / `blocked_reason` | 차단일자/차단근거 (fullComment에서 파싱) |
| `review_result` / `review_opinion` | 현업검토결과/의견 |
| `git_history` | JSON 배열 (최근 5개 커밋) |

---

# 로깅

| 항목 | 내용 |
|------|------|
| 프레임워크 | SLF4J + Logback |
| 출력 | 콘솔 + 파일 동시 |
| MDC | `[ADMIN/USER/SYSTEM] [IP]` 모든 로그에 자동 포함 |
| 파일 위치 | `./logs/app.log` |
| 롤링 | 일자별 (`app-yyyy-MM-dd.log`), 90일 보관 |
| 뷰어 | 설정 페이지에서 달력으로 일자별 조회 |

---

# Quartz 배치

| Job | 설명 | 기본 상태 |
|-----|------|----------|
| GIT_PULL_EXTRACT | Git Pull 후 전체 레포 추출 | 비활성 |
| APM_DAILY | APM 호출건수 일별 수집 | 비활성 |
| APM_WEEKLY | APM 호출건수 주별 수집 | 비활성 |

| 주기 옵션 | 설명 |
|-----------|------|
| 매일 | 지정 시각에 실행 |
| 매주 | 요일 + 시각 |
| 매 N시간 | 간격 지정 |
| 크론식 | 직접 입력 |

---

# 소스 반입 압축 규칙

| 항목 | 내용 |
|------|------|

| 제외 대상 | `target/`, `.git/`, `.idea/`, `.claude/`, `data/`, `logs/`, `.sh`, `.bat`, `mvnw` |
| 출력 경로 | `~/Downloads/ApiViewer.zip` |
| 기존 파일 | 덮어쓰기 가능 |
