# URL 차단 검토 프로세스 — Jira 연동 제안서

> 작성 배경: URLViewer 시스템이 시스템망에 위치하여 현업 접근성이 떨어지는 문제.
> 업무망 이전은 정보보호부서 반대로 불가. **Jira가 유일한 망간 브리지**.

---

## 핵심 아이디어: 역할 재정의

지금은 "URLViewer가 메인 + Jira는 보조"로 생각하고 있지만, **완전히 반대로** 뒤집는 게 답이다.

```
[시스템망]                     [업무망]
URLViewer                      Jira
(탐지·분석 엔진)    ──push──▶   (현업 UI·의사결정·SSOT)
(관리자 전용 운영툴)  ◀─pull──
```

- **URLViewer**: 소스 파싱, 호출건수 집계, 상태 계산, Git 이력 추적 → **백엔드 엔진**으로 축소
- **Jira**: 차단대상 티켓의 **단일 진실 원천(SSOT)**. 현업 검토, 의사결정, 이력 관리 모두 여기서
- **현업은 URLViewer 존재 자체를 몰라도 됨**

이렇게 하면 정보보호부서 설득 불필요, 망분리 유지, 현업 접근성 100% 해결.

---

## 구체안: "Jira-Native 차단 검토" 설계

### 1. Jira 프로젝트 구조

```
Project: URL 차단 검토 (URLB)
├─ Epic: [2026-Q2] XXX팀 URL 차단 검토
│   ├─ Story: GET /api/foo/bar  (URL 1건 = Story 1건)
│   ├─ Story: POST /api/baz
│   └─ ...
```

### 2. Story 커스텀 필드

| 필드 | 소스 |
|------|------|
| Repository | `repository_name` |
| HTTP Method | `http_method` |
| API Path | `api_path` |
| Controller 파일 | `controller_file_path` |
| 호출건수(총/1달/1주) | `call_count_*` |
| 마지막 커밋일 | `git_history[0].date` |
| 차단 근거 | `block_criteria` |
| 상태 분류 | 최우선/후순위/검토필요 |
| URLViewer ID | `api_record.id` (**멱등성 키**) |

### 3. 워크플로우

```
Open → In Review → [Decision]
                    ├─ 차단확정 → Done(Blocked)
                    ├─ 제외    → Done(Excluded)
                    └─ 판단불가 → Pending
```

### 4. Assignee 자동 지정

`manager_mappings` JSON의 담당자 → Jira username 매핑 테이블 한 번 만들어두면, 티켓 생성 시 자동 할당.

### 5. 동기화 플로우 (단방향 PUSH 기반)

**PUSH: URLViewer → Jira** (Quartz Job, 주 1회)

```java
// 신규 차단대상 탐지 → Jira Story upsert
for (ApiRecord r : findBlockCandidates()) {
    if (r.jiraIssueKey == null) {
        String key = jiraClient.createIssue(...);  // 신규
        r.jiraIssueKey = key;
    } else {
        jiraClient.updateFields(r.jiraIssueKey, ...);  // 최신 호출건수 갱신
    }
}
```

**PULL: Jira → URLViewer** (같은 Job 안에서 역방향)

```java
// "Done" 상태로 전이된 티켓 수집 → URLViewer 반영
for (Issue i : jira.search("project=URLB AND status=Done AND updated > last_sync")) {
    ApiRecord r = findByJiraKey(i.key);
    r.reviewResult = mapResolution(i.resolution);
    r.reviewOpinion = i.customField("검토의견");
    // 차단확정이면 status 업데이트
}
```

> 핵심: **시스템망 → 업무망 Jira REST API 호출이 가능한지**가 관건.
> 일방향 아웃바운드 허용이면 이 구조로 끝난다.

### 6. URLViewer 페이지 변경

- `/review.html` → **폐기** (또는 관리자 전용으로 축소)
- 각 URL 상세에 **"Jira 티켓 바로가기"** 링크 (`jiraIssueKey` 기반)
- 대시보드에 "Jira 검토 진행률" 카드 추가 (Open/InReview/Done 카운트)

---

## 추가 아이디어

### A. Epic 단위 묶음으로 티켓 폭증 방지
URL 수백 건을 개별 Story로 만들면 Jira가 지저분해진다.
**Epic = 레포×분기, Story = URL** 구조로 묶으면 현업이 팀 단위로 한눈에 본다.

### B. JQL 북마크 + Dashboard Gadget 제공
팀장들에게 "내 팀 미검토 차단대상" JQL 필터 공유:

```
project = URLB AND "담당팀" = "XXX팀" AND status != Done
```

Jira Dashboard Gadget으로 차트까지 제공하면 현업이 별도 툴 없이 Jira에서 모두 해결.

### C. Confluence 연동 (있다면)
주간 요약 리포트를 Confluence 페이지로 자동 발행. Jira 매크로로 라이브 차트 임베드. 경영진 보고용.

### D. 멱등성 + 재오픈 처리
- 한 번 "제외"된 URL도 새 취약점·호출 패턴 변화 시 자동 재오픈
- `externalId = api_record.id`로 매칭해 **절대 중복 티켓 생성 금지**

### E. 감사(Audit) 관점의 보너스
카드사 감사 대응 시 "누가 언제 이 URL 차단을 승인했는가?"를 Jira 이력으로 바로 증빙 가능.
URLViewer DB 로그보다 **공식 문서 증빙력**이 훨씬 강하다.
정보보호부서한테도 오히려 호재.

---

## 확인 필요한 제약

1. **시스템망 → 업무망 Jira REST API 호출 가능 여부** ← 가장 중요
   - 불가하면 Jira Agent Jar 같은 중계 에이전트가 필요할 수 있음
   - 또는 관리자가 주기적으로 CSV export/import 하는 반자동 방식
2. **Jira 프로젝트 신설 권한** 및 커스텀 필드 추가 가능 여부
3. **Jira 서비스 계정**: URLViewer 전용 API 토큰 발급 가능한지
4. **현업 계정 ↔ URLViewer `manager_mappings`** 매칭 가능한 사용자 키(사번 등)
5. **티켓 예상 규모**: 레포 수 × 평균 차단대상 건수 → Jira 스키마 설계에 영향

---

## 추천 결론

> **"URLViewer는 엔진, Jira는 얼굴"**

- 현업 UI를 굳이 URLViewer에 만들 필요 없음 → `/review.html` 폐기
- URLViewer는 Quartz Job으로 Jira와 단방향 PUSH + 주기 PULL만
- 현업은 이미 쓰는 Jira에서 평소처럼 티켓 처리
- 정보보호부서 입장에서도 "시스템망 → 업무망 아웃바운드 API 호출 1개"만 허용하면 돼서 설득 난이도 낮음
- 감사 증빙, 이력 관리, 알림, 담당자 지정 전부 Jira 기본기능으로 해결

먼저 **1번 제약(아웃바운드 API 호출)**이 열려 있는지부터 확인.
이게 막혀 있으면 "관리자 반자동 동기화" 방식으로 한 단계 내려와야 하는데, 그것도 충분히 굴러간다.
