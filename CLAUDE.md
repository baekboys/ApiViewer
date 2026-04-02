# ApiViewer — Claude Code 프로젝트 컨텍스트

소스코드나 비즈니스로직이 변경되는 작업들은 CLAUDE.MD파일에 무조건 정리해서 업데이트 함. 이때 기존의 내용은 되도록 수정하지 않음

## 프로젝트 개요
Spring Boot 기반의 웹 애플리케이션. Spring MVC Controller 소스를 파싱하여 API 목록을 추출하고, H2 임베디드 DB에 저장·조회하는 내부 도구.

## 기술 스택
- **Java**: 17
- **Spring Boot**: 3.4.0
- **ORM**: Spring Data JPA
- **DB**: H2 2.3.232 (파일 모드, `./data/api-viewer-db`)
- **Parser**: JavaParser 3.25.10 (Controller 소스 분석, Regex 폴백)
- **빌드**: Maven (`run.sh` / `run.bat`)
- **설정 파일**: `application.properties` (Spring Boot), `repos-config.yml` (레포별 설정)

## 실행 방법
```sh
sh run.sh              # 빌드 후 실행
sh run.sh --no-build   # JAR 있을 때 바로 실행
run.bat                # Windows
```

### 접속 URL
| URL | 설명 |
|-----|------|
| http://localhost:8080 | 추출 페이지 |
| http://localhost:8080/viewer.html | DB 저장 이력 조회 |
| http://localhost:8080/settings.html | 레포지토리 설정 관리 |
| http://localhost:8080/h2-console | H2 DB 콘솔 (JDBC: `jdbc:h2:file:./data/api-viewer-db`, user: sa, pw: 없음) |

---

## 디렉토리 구조
```
src/main/java/com/baek/viewer/
├── controller/
│   ├── ApiViewController.java   # 추출·조회·진행상황·Whatap·DB조회 API
│   └── ConfigController.java    # 레포설정 CRUD, 공통설정 API
├── model/
│   ├── ApiInfo.java             # 추출 결과 (git1~git5 포함, 메모리)
│   ├── ApiRecord.java           # JPA 엔티티 (DB 저장용, gitHistory JSON)
│   ├── ExtractRequest.java      # 추출 요청 DTO
│   ├── GlobalConfig.java        # 공통 설정 엔티티 (startDate, endDate)
│   ├── RepoConfig.java          # 레포별 설정 엔티티
│   ├── WhatapRequest.java       # Whatap 조회 요청 DTO
│   └── WhatapResult.java        # Whatap 조회 결과 DTO
├── repository/
│   ├── ApiRecordRepository.java
│   ├── GlobalConfigRepository.java
│   └── RepoConfigRepository.java
└── service/
    ├── ApiExtractorService.java  # 핵심: Controller 파싱 (JavaParser + Regex 폴백)
    ├── ApiStorageService.java    # 추출 결과 H2 저장/갱신
    └── WhatapService.java        # Whatap APM 호출건수 조회

src/main/resources/static/
├── index.html      # 추출 페이지
├── viewer.html     # DB 이력 조회 페이지 (달력 날짜 선택)
└── settings.html   # 레포지토리 설정 관리 페이지
```

---

## 주요 기능 및 동작 방식

### 1. API 추출 (index.html)
- Controller 파일 탐색 → JavaParser로 파싱 → Regex 폴백
- `parallelStream`으로 병렬 처리
- 추출은 **비동기** (POST `/api/extract` → 202 즉시 반환)
- 프론트에서 500ms 간격으로 GET `/api/progress` 폴링 → 오버레이 진행 표시
- 완료 후 GET `/api/list`로 결과 수신
- **레포지토리명 입력 시** 추출 완료 후 자동으로 H2에 저장

### 2. 설정 관리 (settings.html)
- **repos-config.yml이 유일한 소스**: 기동 시 자동 동기화 (`StartupConfigLoader`), settings.html에서 🔄 YAML 동기화 버튼으로 수동 재동기화 가능
- 레포 직접 추가/편집 UI 제거 → YAML 파일 편집 후 동기화 방식
- YAML 파싱: SnakeYAML (`ReposYamlConfig` POJO 바인딩)
- `global.period.startDate/endDate` → GlobalConfig 저장 (whatap → period로 변경, APM 도구 중립)
- `repositories[]` → RepoConfig 저장 (레포명 기준 upsert)
- **YAML 파일 경로**: `application.properties`의 `api.viewer.repos-config-path` (기본값: `./repos-config.yml`)

### viewer.html URL 호출 컬럼
- API 경로 컬럼 바로 옆에 "URL 호출" 컬럼 추가
- `ApiRecord.fullUrl` (= domain + apiPath, 추출 시 저장) 값으로 `<a target="_blank">` 하이퍼링크 표시
- **공통 설정**: Whatap 조회 시작일/종료일 (GlobalConfig, id=1 단일 레코드)
- **레포별 설정**: RepoConfig (REPO_NAME, ROOT_PATH, DOMAIN, GIT_BIN_PATH, TEAM_NAME, MANAGER_NAME, API_PATH_PREFIX, PATH_CONSTANTS, Whatap 전체)
- index.html 상단 드롭다운에서 레포 선택 시 모든 입력 필드 자동 입력

### 3. Git 커밋 이력
- 파일당 최근 **5개** 커밋 조회 (`git log -5 --pretty=format:%as|%an|%s`)
- ApiInfo: git1~git5 (String[] 각 [날짜, 커미터, 메시지])
- DB 저장: `git_history` TEXT 컬럼에 JSON 배열로 직렬화
  ```json
  [{"date":"2025-01-15","author":"홍길동","message":"fix bug"}, ...]
  ```
- index.html 테이블: git1~git5 모두 표시 (구분선으로 구분)

### 4. DB 저장 규칙 (ApiStorageService)
- 키: `(extract_date, repository_name, api_path, http_method)`
- **같은 날 재추출** → 해당 날짜+레포 전체 삭제 후 재삽입 (최신으로 갱신)
- **다른 날 추출** → 새 날짜로 별도 저장 (이전 날짜 보존)

### 5. 이력 조회 (viewer.html)
- 레포지토리 선택 → 달력 표시 (데이터 있는 날짜 파란색 하이라이트)
- 날짜 클릭 → API 목록 조회 (DB에서 fetch)
- git_history JSON을 파싱하여 최대 5개 커밋 이력 표시

### 6. Whatap APM 연동
- 기간+쿠키 입력 → 실제 호출건수 조회 → API 목록에 매핑
- 레포 설정의 Whatap 정보 + 공통 설정의 기간이 자동 입력됨

---

## API 엔드포인트 목록

### ApiViewController (`/api`)
| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/extract` | 추출 시작 (비동기, 202 반환) |
| GET | `/api/progress` | 추출 진행상황 조회 |
| GET | `/api/list` | 메모리 캐시된 추출 결과 |
| GET | `/api/status` | 추출 중 여부 확인 |
| GET | `/api/db/repositories` | DB 저장된 레포 목록 |
| GET | `/api/db/dates?repository=` | 레포별 추출 날짜 목록 |
| GET | `/api/db/apis?repository=&date=` | DB에서 API 목록 조회 |
| POST | `/api/whatap/stats` | Whatap 호출건수 조회 |

### ConfigController (`/api/config`)
| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/config/global` | 공통 설정 조회 |
| PUT | `/api/config/global` | 공통 설정 저장 |
| GET | `/api/config/repos` | 레포 설정 목록 |
| GET | `/api/config/repos/{id}` | 레포 설정 단건 조회 |
| POST | `/api/config/repos` | 레포 설정 추가 |
| PUT | `/api/config/repos/{id}` | 레포 설정 수정 |
| DELETE | `/api/config/repos/{id}` | 레포 설정 삭제 |

---

## DB 테이블 구조

### api_record
추출된 API 이력. `(extract_date, repository_name, api_path, http_method)` UNIQUE 제약.

### repo_config
레포지토리별 설정. `repo_name` UNIQUE.

### global_config
공통 설정. id=1 단일 레코드 (startDate, endDate).

---

## 주의사항 / 개발 규칙
- Controller 파일 탐색 조건: 파일명에 `Controller` 또는 `Conrtoller`(오타 허용) 포함
- JavaParser 파싱 실패 시 Regex 폴백 자동 적용
- `parallelStream` 사용으로 `currentFile` 추적 시 race condition 있음 (진행 표시 참고용)
- H2 `ddl-auto=update` → 엔티티 변경 시 컬럼 자동 추가 (기존 데이터 보존)
- 기존 DB 레코드에 새 컬럼 추가 시 해당 컬럼은 null로 표시됨
- git_history가 null인 레코드는 viewer에서 "이력 없음"으로 표시
