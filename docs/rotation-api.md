# 로테이션 REST API 계약 초안

> 상태: Draft
>
> 기준 문서: `docs/rotation-policy.md`
>
> 화면 참고: 바탕화면 와이어프레임의 홈, 업무 로테이션, 업무·일정, 완료 업무 흐름

## 1. 목적과 범위

이 문서는 매일·매주·격주 업무 정의, 가능 멤버, 회차 조회, 완료, 대타 요청, 완료 취소, `NEEDS_ATTENTION` 복구, 멤버 탈퇴 재배정을 위한 REST API 계약을 정의한다.

정책과 API가 충돌하면 `rotation-policy.md`가 우선한다. 자동 배정은 선택 순서가 아니라 공정성 점수로 후보를 좁힌 뒤 최종 동점자 사이에서 무작위 선택한다. 와이어프레임의 동작은 다음과 같이 연결한다.

| 와이어프레임 동작 | API 동작 |
|---|---|
| 매일·매주·격주 탭 | `GET /occurrences`의 `frequency` 필터 |
| 내 업무의 `업무 완료` | `POST /occurrences/{occurrenceId}/complete` |
| `대타 요청` | `POST /occurrences/{occurrenceId}/substitute-requests` |
| 전체 완료 업무 목록 | `GET /occurrences/completed-history` |
| `완료 취소` | `POST /occurrences/{occurrenceId}/undo-complete` |
| 관리자 관리 필요 목록 | `GET /occurrences?status=NEEDS_ATTENTION` 및 `POST /retry-assignment` |

## 2. 공통 계약

### 2.1 기본 URI와 인증

- 기본 경로는 `/api/groups/{groupId}`다.
- 기존 OAuth2 세션 인증과 CSRF 정책을 그대로 사용한다.
- 모든 대상 리소스는 경로의 그룹에 속해야 한다. 다른 그룹의 UUID를 조합하면 정보 노출을 막기 위해 `404 RESOURCE_NOT_FOUND`를 반환한다.
- 탈퇴한 멤버는 그룹 API에 접근할 수 없다.

권한은 다음과 같다.

| 작업 | 권한 |
|---|---|
| 업무·회차 조회 | 활성 그룹 멤버 |
| 업무 생성·수정·비활성화, 가능 멤버 변경 | `OWNER` |
| 완료 | 해당 회차의 현재 담당자 |
| 대타 요청 생성 | 해당 회차의 현재 담당자 |
| 대타 수락·거절 | 요청 생성 당시의 활성 수신 멤버 |
| 대타 요청 조회 | 요청자, 수신자, `OWNER` |
| 완료 취소 | 해당 회차의 마지막 유효 완료자인 활성 멤버 |
| `NEEDS_ATTENTION` 재시도 | `OWNER` |
| 본인 탈퇴 | 해당 활성 멤버 |
| 다른 멤버 내보내기 | `OWNER` |

`OWNER`라도 다른 담당자를 대신해 완료·대타 응답·완료 취소를 기록할 수 없다.

### 2.2 외부 식별자

이 계약에 나타나는 모든 리소스 식별자는 공개 UUID 문자열이다.

- `groupId`
- `membershipId`
- `choreId`
- `occurrenceId`
- `requestId`

데이터베이스의 숫자 PK, 사용자 PK, 배정 시도 PK는 URI, 요청·응답 본문, `Location` 헤더, 오류 본문에 절대 노출하지 않는다. 배정 이력의 `sequence`는 회차 안의 표시 순번이며 리소스 ID가 아니다.

공개 UUID 예시는 다음 형식을 사용한다.

```text
11111111-1111-4111-8111-111111111111
```

### 2.3 날짜와 시간

- `Instant`는 RFC 3339 UTC 문자열로 응답한다. 예: `2026-07-26T11:00:00Z`
- 현지 달력 날짜는 ISO `YYYY-MM-DD`를 사용한다.
- 현지 시각은 `HH:mm:ss`를 사용한다.
- 요일은 `MONDAY`부터 `SUNDAY`까지의 영문 enum을 사용한다.
- 회차 기간은 `[periodStart, periodEndExclusive)` 반개구간이다.
- 기간 계산에는 회차의 `timeZoneIdSnapshot`을 사용한다.
- 일간 마감일은 `periodStart`, 주간 마감일은 `weeklyDueDay`, 격주 마감일은 `periodEndExclusive` 직전 현지 날짜다. 각각의 날짜에 `dueTime`을 적용해 `dueAt`을 만든다.

### 2.4 리소스 버전과 동시성

변경 가능한 업무, 회차, 그룹 멤버 응답에는 `version`과 같은 값을 가진 `ETag`를 반환한다.

```http
ETag: "7"
```

기존 리소스를 변경하는 요청은 현재 버전을 `If-Match`로 보내야 한다.

```http
If-Match: "7"
```

- `If-Match` 누락: `428 PRECONDITION_REQUIRED`
- 오래된 버전: `409 VERSION_CONFLICT`
- 올바른 버전이지만 허용되지 않는 상태 전이: `409 INVALID_OCCURRENCE_STATE`

같은 회차에 여러 변경 요청이 동시에 도착하면 먼저 커밋된 요청만 성공한다. 나머지 요청은 `409 VERSION_CONFLICT`를 받으며 서버가 자동으로 다른 상태 전이를 시도하지 않는다.

- 대타 요청 생성은 회차의 `version`을 사용한다.
- 대타 수락·거절은 대타 요청의 `version`을 사용한다.
- 대타 요청 생성은 `201 + Location + ETag`, 수락·거절은 `200 + ETag`를 반환한다.

### 2.5 멱등 키

모든 상태 변경 요청은 `Idempotency-Key` 헤더가 필수다.

```http
Idempotency-Key: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa
```

적용 대상은 `POST`, `PATCH`, `PUT`, `DELETE` 전체다.

1. 키는 8~128자의 불투명 문자열이며 UUID 사용을 권장한다.
2. 같은 사용자가 같은 HTTP 메서드와 정규화된 URI에 같은 키·본문·조건 헤더로 재요청하면 최초 상태 코드, 응답 본문, `ETag`를 그대로 재생한다.
3. 재생 응답에는 `Idempotency-Replayed: true`를 추가한다.
4. 같은 키를 다른 본문이나 다른 `If-Match`와 함께 재사용하면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
5. 서버는 멱등 결과를 최소 24시간 보존한다.
6. 멱등 결과 조회를 버전 검사보다 먼저 수행한다. 따라서 성공 응답을 잃어버린 클라이언트가 같은 키로 재시도해도 후속 버전 충돌이 발생하지 않는다.

### 2.6 공통 오류 형식

오류는 `application/problem+json`으로 응답한다.

```json
{
  "type": "https://sharing-log.example/problems/version-conflict",
  "title": "리소스 버전이 변경되었습니다.",
  "status": 409,
  "code": "VERSION_CONFLICT",
  "detail": "회차를 다시 조회한 뒤 요청을 다시 시도해 주세요.",
  "instance": "/api/groups/11111111-1111-4111-8111-111111111111/occurrences/33333333-3333-4333-8333-333333333333/complete",
  "resourceId": "33333333-3333-4333-8333-333333333333",
  "currentVersion": 8,
  "currentStatus": "ASSIGNED",
  "traceId": "01K12ABCDEF89XYZ"
}
```

검증 오류는 필드 정보를 추가한다.

```json
{
  "type": "https://sharing-log.example/problems/validation-failed",
  "title": "요청 값이 올바르지 않습니다.",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "요청 필드를 확인해 주세요.",
  "errors": [
    {
      "field": "schedule.biweeklyAnchorDate",
      "reason": "격주 기준일은 그룹의 주 시작 요일과 같아야 합니다."
    }
  ],
  "traceId": "01K12ABCDEF89XYZ"
}
```

## 3. 리소스 표현

### 3.1 멤버 참조

API는 사용자 계정 ID나 이메일 대신 그룹 멤버십의 공개 UUID만 사용한다.

```json
{
  "membershipId": "44444444-4444-4444-8444-444444444444",
  "displayName": "김지수",
  "avatarUrl": null,
  "status": "ACTIVE"
}
```

`status`는 `ACTIVE` 또는 `LEFT`다. 과거 이력에 포함된 멤버는 현재 `LEFT`일 수 있다.

### 3.2 업무 정의

```json
{
  "choreId": "22222222-2222-4222-8222-222222222222",
  "groupId": "11111111-1111-4111-8111-111111111111",
  "name": "화장실 청소",
  "schedule": {
    "frequency": "WEEKLY",
    "dueTime": "20:00:00",
    "weeklyDueDay": "SUNDAY",
    "biweeklyAnchorDate": null
  },
  "eligibility": {
    "mode": "SELECTED_MEMBERS",
    "members": [
      {
        "membershipId": "44444444-4444-4444-8444-444444444444",
        "displayName": "김지수",
        "avatarUrl": null,
        "status": "ACTIVE"
      },
      {
        "membershipId": "55555555-5555-4555-8555-555555555555",
        "displayName": "이민준",
        "avatarUrl": null,
        "status": "ACTIVE"
      }
    ]
  },
  "active": true,
  "createdByMembershipId": "44444444-4444-4444-8444-444444444444",
  "createdAt": "2026-07-23T13:00:00Z",
  "version": 3
}
```

반복별 스케줄 필드 규칙은 다음과 같다.

| `frequency` | 필수 필드 | 반드시 `null`인 필드 |
|---|---|---|
| `DAILY` | `dueTime` | `weeklyDueDay`, `biweeklyAnchorDate` |
| `WEEKLY` | `dueTime`, `weeklyDueDay` | `biweeklyAnchorDate` |
| `BIWEEKLY` | `dueTime`, `biweeklyAnchorDate` | `weeklyDueDay` |

`eligibility.mode` 값은 다음과 같다.

- `ALL_ACTIVE_MEMBERS`: 회차 생성 시점의 모든 활성 멤버가 가능 멤버다. `members`는 빈 배열이다.
- `SELECTED_MEMBERS`: `members`에 등록된 멤버 중 활성 멤버만 후보가 된다.

업무 일정 변경은 업무의 일정 개정 번호를 증가시킨다. 변경 시점의 현지 날짜에 활성인 미종료 회차가 있으면 그 회차의 ID, 담당자, 상태는 유지하고 주기·기간·마감 시각 스냅샷만 새 개정으로 즉시 변경한다. 완료된 회차의 스냅샷은 바뀌지 않는다.

### 3.3 회차 요약

목록 응답은 다음 요약 표현을 사용한다. 현재 회차 목록의 `choreName`은 업무 정의의 최신 이름을 사용하고, 완료 이력의 `choreName`은 회차 생성 당시 이름 스냅샷을 사용한다. 상태 변경 응답은 화면을 즉시 갱신하는 데 필요한 `occurrenceId`, `status`, `currentAssignee`, `lastAssignee`, `attention`, `closedAt`, `version`만 담은 축약형을 사용하며, 최신 전체 표현과 이력은 단건 조회로 가져온다.

```json
{
  "occurrenceId": "33333333-3333-4333-8333-333333333333",
  "choreId": "22222222-2222-4222-8222-222222222222",
  "choreName": "화장실 청소",
  "frequency": "WEEKLY",
  "periodStart": "2026-07-20",
  "periodEndExclusive": "2026-07-27",
  "timeZoneIdSnapshot": "Asia/Seoul",
  "dueAt": "2026-07-26T11:00:00Z",
  "status": "ASSIGNED",
  "currentAssignee": {
    "membershipId": "55555555-5555-4555-8555-555555555555",
    "displayName": "이민준",
    "avatarUrl": null,
    "status": "ACTIVE"
  },
  "lastAssignee": null,
  "attention": null,
  "substituteRequestNotice": "다른 사용자가 올린 대타 요청입니다",
  "availableActions": [],
  "closedAt": null,
  "version": 4
}
```

- `ASSIGNED`: `currentAssignee`가 반드시 있고 `attention`은 `null`이다.
- `NEEDS_ATTENTION`: `currentAssignee`가 없고 `attention`이 반드시 있다.
- `COMPLETED`, `SKIPPED`: `currentAssignee`가 없고 `lastAssignee`와 `closedAt`이 있다.
- 재배정 실패로 `NEEDS_ATTENTION`이 된 경우 `lastAssignee`에는 직전에 종료된 담당자가 있을 수 있다. 이는 현재 담당자가 아니다.
- 다른 멤버가 이 회차에 진행 중인 대타 요청을 올렸으면 `substituteRequestNotice`는 `다른 사용자가 올린 대타 요청입니다`이고, 그 외에는 `null`이다.
- `availableActions`는 로그인한 요청자 기준이다. 현재 담당자에게는 `COMPLETE`와 진행 중 요청이 없을 때 `REQUEST_SUBSTITUTE`가 포함된다. 마지막 유효 완료자에게는 `UNDO_COMPLETE`, 관리자에게는 필요한 경우 `RETRY_ASSIGNMENT`가 포함된다.

관리 필요 정보의 형식은 다음과 같다.

```json
{
  "reason": "NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE",
  "since": "2026-07-23T14:12:00Z",
  "lastDecisionAt": "2026-07-23T14:12:00Z"
}
```

후보가 없다는 사실은 정상적인 도메인 결과다. 조회나 재시도 응답을 `4xx`로 바꾸지 않는다.

### 3.4 회차 상세와 이력 (후속 설계, 현재 미구현)

단건 회차 응답은 요약 필드에 가능 멤버 스냅샷, 배정 이력, 결정 감사 정보를 추가한다.

```json
{
  "occurrenceId": "33333333-3333-4333-8333-333333333333",
  "choreId": "22222222-2222-4222-8222-222222222222",
  "choreName": "화장실 청소",
  "frequency": "WEEKLY",
  "periodStart": "2026-07-20",
  "periodEndExclusive": "2026-07-27",
  "timeZoneIdSnapshot": "Asia/Seoul",
  "dueAt": "2026-07-26T11:00:00Z",
  "status": "ASSIGNED",
  "currentAssignee": {
    "membershipId": "55555555-5555-4555-8555-555555555555",
    "displayName": "이민준",
    "avatarUrl": null,
    "status": "ACTIVE"
  },
  "lastAssignee": null,
  "attention": null,
  "eligibilitySnapshot": {
    "version": 1,
    "mode": "SELECTED_MEMBERS",
    "membershipIds": [
      "44444444-4444-4444-8444-444444444444",
      "55555555-5555-4555-8555-555555555555"
    ]
  },
  "assignmentHistory": [
    {
      "sequence": 1,
      "assignee": {
        "membershipId": "44444444-4444-4444-8444-444444444444",
        "displayName": "김지수",
        "avatarUrl": null,
        "status": "ACTIVE"
      },
      "trigger": "INITIAL",
      "assignedAt": "2026-07-20T00:00:01Z",
      "endedAt": "2026-07-23T13:55:00Z",
      "endReason": "DECLINED_BY_ASSIGNEE",
      "actorNote": "오늘 야근이라 이번 회차는 어려워요."
    },
    {
      "sequence": 2,
      "assignee": {
        "membershipId": "55555555-5555-4555-8555-555555555555",
        "displayName": "이민준",
        "avatarUrl": null,
        "status": "ACTIVE"
      },
      "trigger": "DECLINE_REASSIGNMENT",
      "assignedAt": "2026-07-23T13:55:00Z",
      "endedAt": null,
      "endReason": null,
      "actorNote": null
    }
  ],
  "assignmentDecisions": [
    {
      "sequence": 1,
      "trigger": "INITIAL",
      "decidedAt": "2026-07-20T00:00:01Z",
      "outcome": "ASSIGNED",
      "algorithmVersion": "fair-random-v4",
      "decisionSeed": "5932104873210042",
      "selectionReasonCodes": [
        "ACTIVE_ELIGIBLE_NOT_DECLINED_FILTER",
        "MINIMUM_ACTIVE_PERIOD_LOAD",
        "MINIMUM_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT",
        "MINIMUM_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT",
        "RANDOM_TIE_BREAK"
      ],
      "candidates": [
        {
          "membershipId": "44444444-4444-4444-8444-444444444444",
          "validSameChoreAssignmentCount": 1,
          "fairnessCredit": 0,
          "effectiveValidSameChoreAssignmentCount": 1,
          "validSameFrequencyAssignmentCount": 3,
          "activePeriodLoad": 0,
          "previousAssignee": false,
          "decision": "SELECTED"
        },
        {
          "membershipId": "55555555-5555-4555-8555-555555555555",
          "validSameChoreAssignmentCount": 1,
          "fairnessCredit": 0,
          "effectiveValidSameChoreAssignmentCount": 1,
          "validSameFrequencyAssignmentCount": 3,
          "activePeriodLoad": 0,
          "previousAssignee": false,
          "decision": "RANDOM_TIE_NOT_SELECTED"
        }
      ],
      "excluded": [],
      "selectedMembershipId": "44444444-4444-4444-8444-444444444444"
    },
    {
      "sequence": 2,
      "trigger": "DECLINE_REASSIGNMENT",
      "decidedAt": "2026-07-23T13:55:00Z",
      "outcome": "ASSIGNED",
      "algorithmVersion": "fair-random-v4",
      "decisionSeed": "7439128746501234",
      "selectionReasonCodes": [
        "ACTIVE_ELIGIBLE_NOT_DECLINED_FILTER",
        "MINIMUM_ACTIVE_PERIOD_LOAD",
        "MINIMUM_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT",
        "MINIMUM_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT",
        "SOLE_FINALIST"
      ],
      "candidates": [
        {
          "membershipId": "55555555-5555-4555-8555-555555555555",
          "validSameChoreAssignmentCount": 1,
          "fairnessCredit": 0,
          "effectiveValidSameChoreAssignmentCount": 1,
          "validSameFrequencyAssignmentCount": 3,
          "activePeriodLoad": 0,
          "previousAssignee": false,
          "decision": "SELECTED"
        }
      ],
      "excluded": [
        {
          "membershipId": "44444444-4444-4444-8444-444444444444",
          "reason": "DECLINED_CURRENT_OCCURRENCE"
        }
      ],
      "selectedMembershipId": "55555555-5555-4555-8555-555555555555"
    }
  ],
  "availableActions": [],
  "createdAt": "2026-07-20T00:00:01Z",
  "closedAt": null,
  "version": 4
}
```

이력 enum은 다음과 같다.

`SKIPPED`, `SKIPPED_ALREADY_DONE`, `DECLINED_BY_ASSIGNEE`,
`DECLINE_REASSIGNMENT`, `DECLINED_CURRENT_OCCURRENCE`는 기존 감사 이력을
역직렬화하고 진행 중인 레거시 회차를 안전하게 처리하기 위한 읽기 호환 값이다.
새 공개 API는 이 값을 생성하지 않는다.

| 필드 | 값 |
|---|---|
| 회차 상태 | `ASSIGNED`, `COMPLETED`, `SKIPPED`, `NEEDS_ATTENTION` |
| 배정 계기 | `INITIAL`, `DECLINE_REASSIGNMENT`, `MEMBER_LEFT_REASSIGNMENT`, `NEEDS_ATTENTION_RETRY`, `PARTICIPATION_CHANGE_REASSIGNMENT`, `PARTICIPATION_CHANGE_RETRY`, `SUBSTITUTE_ACCEPTANCE`, `COMPLETION_REOPENED` |
| 배정 종료 사유 | `COMPLETED`, `SKIPPED_ALREADY_DONE`, `DECLINED_BY_ASSIGNEE`, `ASSIGNEE_LEFT_GROUP`, `PARTICIPATION_REMOVED`, `SUBSTITUTE_ACCEPTED`, `PLAN_REGENERATED` |
| 결정 결과 | `ASSIGNED`, `NO_CANDIDATE` |
| 후보 제외 사유 | `INACTIVE`, `NOT_ELIGIBLE`, `DECLINED_CURRENT_OCCURRENCE` |

`decisionSeed`는 JavaScript의 안전 정수 범위를 넘을 수 있으므로 문자열로 응답한다. 자동 배정은 `fair-random-v4`, 대타 수락과 완료 취소 복원은 `manual-action-v1` 및 seed `0`으로 기록한다. `fair-random-v4`는 같은 반복 주기의 현재 기간 업무량, 업무별 사이클, 같은 반복 주기의 과거 유효 할당 수, 직전 담당 여부 순으로 후보를 좁힌다. 따라서 같은 주기·같은 기간에는 아직 배정되지 않은 가능 멤버를 먼저 선택하고, 중복이 불가피하면 가능 후보 안에서 기간 내 업무량 편차를 최소화한다. 배정 핵심 이력은 삭제하거나 덮어쓰지 않으며 완료 취소 메타데이터만 추가한다.

## 4. 엔드포인트 요약

| 메서드 | URI | 용도 | 성공 |
|---|---|---|---|
| `POST` | `/api/groups/{groupId}/chores` | 업무 생성 및 현재 회차 최초 배정 | `201` |
| `GET` | `/api/groups/{groupId}/chores` | 업무 목록 | `200` |
| `PATCH` | `/api/groups/{groupId}/chores/{choreId}` | 업무명·주기·마감일·가능 멤버 수정 | `200` |
| `DELETE` | `/api/groups/{groupId}/chores/{choreId}` | 업무 비활성화 | `204` |
| `GET` | `/api/groups/{groupId}/occurrences` | 주기·기간·상태별 회차 목록 | `200` |
| `GET` | `/api/groups/{groupId}/occurrences/due-soon` | 개인 설정 범위 안의 내 마감 임박 회차 | `200` |
| `GET` | `/api/groups/{groupId}/occurrences/completed-history` | 전체 또는 내 완료 업무 이력 | `200` |
| `GET` | `/api/groups/{groupId}/notifications/summary` | 마감 임박·받은 대타 요청 개수 | `200` |
| `GET` | `/api/groups/{groupId}/notifications/settings` | 내 주기별 마감 임박 설정 | `200` |
| `PUT` | `/api/groups/{groupId}/notifications/settings` | 내 주기별 마감 임박 설정 변경 | `200` |
| `POST` | `/api/groups/{groupId}/occurrences/{occurrenceId}/complete` | 실제 완료 | `200` |
| `POST` | `/api/groups/{groupId}/occurrences/{occurrenceId}/undo-complete` | 완료 취소 및 같은 멤버로 복원 | `200` |
| `POST` | `/api/groups/{groupId}/occurrences/{occurrenceId}/substitute-requests` | 대타 요청 생성 | `201` |
| `GET` | `/api/groups/{groupId}/substitute-requests` | 받은·보낸·전체 대타 요청 조회 | `200` |
| `GET` | `/api/groups/{groupId}/substitute-requests/{requestId}` | 대타 요청 단건 조회 | `200` |
| `POST` | `/api/groups/{groupId}/substitute-requests/{requestId}/accept` | 대타 수락 | `200` |
| `POST` | `/api/groups/{groupId}/substitute-requests/{requestId}/reject` | 대타 거절 | `200` |
| `POST` | `/api/groups/{groupId}/occurrences/{occurrenceId}/retry-assignment` | 관리 필요 회차 재시도 | `200` |
| `GET` | `/api/groups/{groupId}/rotation-members` | 활성 멤버와 관리 권한 조회 | `200` |
| `PATCH` | `/api/groups/{groupId}/rotation-members/{membershipId}/chore-participations` | 여러 업무 참여 일괄 추가·제외 | `200` |
| `POST` | `/api/groups/{groupId}/members/{membershipId}/leave` | 탈퇴/내보내기 및 미종료 회차 재배정 | `200` |

## 5. 업무 API

### 5.1 업무 생성

```http
POST /api/groups/11111111-1111-4111-8111-111111111111/chores
Content-Type: application/json
Idempotency-Key: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa
```

```json
{
  "name": "화장실 청소",
  "schedule": {
    "frequency": "WEEKLY",
    "dueTime": "20:00:00",
    "weeklyDueDay": "SUNDAY",
    "biweeklyAnchorDate": null
  },
  "eligibility": {
    "mode": "SELECTED_MEMBERS",
    "membershipIds": [
      "44444444-4444-4444-8444-444444444444",
      "55555555-5555-4555-8555-555555555555"
    ]
  }
}
```

성공 응답:

```http
HTTP/1.1 201 Created
Location: /api/groups/11111111-1111-4111-8111-111111111111/chores/22222222-2222-4222-8222-222222222222
ETag: "1"
```

```json
{
  "chore": {
    "choreId": "22222222-2222-4222-8222-222222222222",
    "groupId": "11111111-1111-4111-8111-111111111111",
    "name": "화장실 청소",
    "schedule": {
      "frequency": "WEEKLY",
      "dueTime": "20:00:00",
      "weeklyDueDay": "SUNDAY",
      "biweeklyAnchorDate": null
    },
    "eligibility": {
      "mode": "SELECTED_MEMBERS",
      "members": [
        {
          "membershipId": "44444444-4444-4444-8444-444444444444",
          "displayName": "김지수",
          "avatarUrl": null,
          "status": "ACTIVE"
        },
        {
          "membershipId": "55555555-5555-4555-8555-555555555555",
          "displayName": "이민준",
          "avatarUrl": null,
          "status": "ACTIVE"
        }
      ]
    },
    "active": true,
    "createdByMembershipId": "44444444-4444-4444-8444-444444444444",
    "createdAt": "2026-07-23T13:00:00Z",
    "version": 1
  },
  "currentOccurrence": {
    "occurrenceId": "33333333-3333-4333-8333-333333333333",
    "status": "ASSIGNED",
    "currentAssignee": {
      "membershipId": "55555555-5555-4555-8555-555555555555",
      "displayName": "이민준",
      "avatarUrl": null,
      "status": "ACTIVE"
    },
    "attention": null,
    "version": 1
  }
}
```

업무 생성은 현재 현지 날짜가 속한 회차 생성까지 한 트랜잭션에서 수행한다. 후보가 있으면 `ASSIGNED`, 없으면 생성 자체를 실패시키지 않고 `NEEDS_ATTENTION`으로 응답한다. `(choreId, periodStart)` 유일 제약으로 중복 회차를 막는다.

검증 규칙:

- `name`: 공백 제거 후 1~100자
- `SELECTED_MEMBERS`: 같은 그룹의 활성 멤버를 1명 이상 지정
- 중복 `membershipId`: `400 VALIDATION_FAILED`
- 알 수 없거나 다른 그룹의 멤버: `404 RESOURCE_NOT_FOUND`
- 격주 `biweeklyAnchorDate`: 그룹의 `weekStartsOn`과 같은 요일

### 5.2 업무 목록

```http
GET /api/groups/{groupId}/chores?frequency=WEEKLY&active=true
```

쿼리:

| 이름 | 기본값 | 설명 |
|---|---|---|
| `frequency` | 전체 | `DAILY`, `WEEKLY`, `BIWEEKLY` |
| `active` | `true` | `true`, `false`, `all` |

```json
{
  "items": [
    {
      "choreId": "22222222-2222-4222-8222-222222222222",
      "groupId": "11111111-1111-4111-8111-111111111111",
      "name": "화장실 청소",
      "schedule": {
        "frequency": "WEEKLY",
        "dueTime": "20:00:00",
        "weeklyDueDay": "SUNDAY",
        "biweeklyAnchorDate": null
      },
      "eligibility": {
        "mode": "SELECTED_MEMBERS",
        "members": []
      },
      "active": true,
      "createdByMembershipId": "44444444-4444-4444-8444-444444444444",
      "createdAt": "2026-07-23T13:00:00Z",
      "version": 3
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

현재 목록 응답에는 각 업무의 가능 멤버가 포함된다. 페이징은 적용하지 않으며 `nextCursor=null`, `hasNext=false`다.

### 5.3 업무 수정

```http
PATCH /api/groups/{groupId}/chores/{choreId}
Content-Type: application/json
Idempotency-Key: bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb
If-Match: "3"
```

```json
{
  "name": "공용 화장실 청소",
  "schedule": {
    "frequency": "WEEKLY",
    "dueTime": "19:00:00",
    "weeklyDueDay": "SATURDAY",
    "biweeklyAnchorDate": null
  },
  "eligibility": {
    "mode": "SELECTED_MEMBERS",
    "membershipIds": [
      "44444444-4444-4444-8444-444444444444",
      "55555555-5555-4555-8555-555555555555"
    ]
  }
}
```

- `name`, `schedule`, `eligibility` 중 하나 이상을 보낸다.
- `schedule`을 보내면 주기별 스케줄 객체 전체를 보낸다.
- `eligibility`를 보내면 모드와 멤버십 ID 목록 전체를 보낸다. `ALL_ACTIVE_MEMBERS`는 빈 목록, `SELECTED_MEMBERS`는 중복 없는 활성 그룹 멤버 한 명 이상이어야 한다.
- 가능 멤버 변경은 업무 설정과 미리 생성된 미래 회차에 적용한다. 현재 활성 회차의 담당자와 가능 멤버 스냅샷까지 즉시 바꾸려면 멤버별 참여 업무 일괄 변경 API의 `CURRENT_AND_FUTURE` 범위를 사용한다.
- 일정이 실제로 달라지면 업무의 일정 개정 번호를 증가시킨다. 같은 일정을 다시 보내거나 업무명만 변경하면 개정 번호는 증가하지 않는다.
- 변경 시점의 그룹 현지 날짜에 활성인 `ASSIGNED` 또는 `NEEDS_ATTENTION` 회차가 있으면 회차 ID, 담당자, 상태, 가능 멤버 스냅샷은 유지하고 주기·기간·마감 시각 스냅샷을 새 일정 개정으로 즉시 변경한다.
- 활성 미종료 회차가 없으면 완료 이력을 변경하거나 PATCH 처리 중 새 회차를 만들지 않는다. 이후 스케줄러가 회차를 생성할 때 새 일정 개정을 사용한다.
- 회차 중복 기준은 `(choreId, scheduleRevision, periodStart)`다. 따라서 완료 이력과 새 개정의 기간 시작일이 같아도 별도 회차로 보존할 수 있다.

성공 시 `200`, 갱신된 전체 업무와 새 `ETag`를 반환한다.

### 5.4 업무 비활성화

```http
DELETE /api/groups/{groupId}/chores/{choreId}
Idempotency-Key: dddddddd-dddd-4ddd-8ddd-dddddddddddd
If-Match: "5"
```

성공 시 `204 No Content`다. 이는 물리 삭제가 아니라 `active=false` 전환이다.

- 새 회차 생성을 중단한다.
- 이미 생성된 `ASSIGNED`, `NEEDS_ATTENTION`, 종료 회차는 삭제하거나 상태를 바꾸지 않는다.
- 과거 배정·완료·생략 이력을 보존한다.

## 6. 회차 조회 API

### 6.1 매일·매주·격주 현재 섹션

`activeOn` 날짜를 포함하는 기간의 회차를 조회한다. 날짜는 그룹 시간대를 기준으로 해석한다.

```http
GET /api/groups/{groupId}/occurrences?frequency=DAILY&activeOn=2026-07-23
GET /api/groups/{groupId}/occurrences?frequency=WEEKLY&activeOn=2026-07-23
GET /api/groups/{groupId}/occurrences?frequency=BIWEEKLY&activeOn=2026-07-23
```

격주 업무마다 `anchorDate`가 다를 수 있으므로 `activeOn`은 단순히 하나의 공통 시작일로 변환하지 않는다. 각 업무 회차에 대해 `periodStart <= activeOn < periodEndExclusive`를 검사한다.

응답:

```json
{
  "groupId": "11111111-1111-4111-8111-111111111111",
  "frequency": "WEEKLY",
  "query": {
    "activeOn": "2026-07-23",
    "timeZoneId": "Asia/Seoul"
  },
  "items": [
    {
      "occurrenceId": "33333333-3333-4333-8333-333333333333",
      "choreId": "22222222-2222-4222-8222-222222222222",
      "choreName": "화장실 청소",
      "frequency": "WEEKLY",
      "periodStart": "2026-07-20",
      "periodEndExclusive": "2026-07-27",
      "timeZoneIdSnapshot": "Asia/Seoul",
      "dueAt": "2026-07-26T11:00:00Z",
      "status": "ASSIGNED",
      "currentAssignee": {
        "membershipId": "55555555-5555-4555-8555-555555555555",
        "displayName": "이민준",
        "avatarUrl": null,
        "status": "ACTIVE"
      },
      "lastAssignee": null,
      "attention": null,
      "availableActions": [],
      "closedAt": null,
      "version": 4
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

### 6.2 미래·과거 범위 조회 (후속 설계, 현재 미구현)

와이어프레임의 이번 주, 1주 후, 2주 후 아코디언은 기간 시작일 범위 조회를 사용한다.

```http
GET /api/groups/{groupId}/occurrences?frequency=WEEKLY&periodStartFrom=2026-07-13&periodStartBefore=2026-08-10
```

- `periodStartFrom`: 포함
- `periodStartBefore`: 미포함
- `activeOn`과 범위 파라미터를 동시에 보낼 수 없다.
- 범위 조회에서는 `periodStartFrom`, `periodStartBefore`를 반드시 함께 보낸다.
- 최대 조회 범위는 93일이다.
- `GET`은 회차를 새로 만들지 않는다. 미래 미리보기가 필요하면 내부 스케줄러가 미리 생성한 회차만 반환하며, 생성 범위는 별도의 스케줄러 설정으로 관리한다.

공통 선택 필터:

| 이름 | 설명 |
|---|---|
| `status` | 반복 가능. 예: `status=COMPLETED&status=SKIPPED` |
| `mineOnly` | `true`면 현재 로그인 멤버가 현재 담당자인 `ASSIGNED` 회차만 반환 |
| `choreId` | 특정 업무의 회차만 반환 |
| `size` | 기본 50, 최대 100 |
| `cursor` | 서버 발급 불투명 커서 |

홈의 “내 업무”는 필요한 일간·주간·격주 범위를 병렬 조회하면서 `mineOnly=true&status=ASSIGNED`를 사용한다. 완료 업무 화면은 전용 `GET /occurrences/completed-history`를 사용한다.

### 6.3 회차 단건과 이력 (후속 설계, 현재 미구현)

```http
GET /api/groups/{groupId}/occurrences/{occurrenceId}
```

성공 시 `200`, 3.4의 상세 표현과 `ETag`를 반환한다. 현재 담당자가 없어도 과거 담당자를 `currentAssignee`로 채우지 않는다.

### 6.4 마감 임박 조회와 개인 알림 설정

설정은 그룹 전체가 아니라 로그인한 그룹 멤버 개인에게 저장된다. 기존 멤버와 새 멤버의 기본값은 모든 주기에서 마감 5시간 전이다.

```http
GET /api/groups/{groupId}/notifications/settings
```

```json
{
  "groupId": "11111111-1111-4111-8111-111111111111",
  "membershipId": "55555555-5555-4555-8555-555555555555",
  "dailyHoursBeforeDue": 5,
  "weeklyHoursBeforeDue": 5,
  "biweeklyHoursBeforeDue": 5
}
```

```http
PUT /api/groups/{groupId}/notifications/settings
Content-Type: application/json
```

```json
{
  "dailyHoursBeforeDue": 4,
  "weeklyHoursBeforeDue": 48,
  "biweeklyHoursBeforeDue": 240
}
```

- `dailyHoursBeforeDue`: 1~24
- `weeklyHoursBeforeDue`: 1~168
- `biweeklyHoursBeforeDue`: 1~336

`GET /api/groups/{groupId}/occurrences/due-soon`은 현재 멤버에게 배정된 `ASSIGNED` 회차 중 `현재 시각 <= dueAt <= 현재 시각 + 해당 주기 설정`인 항목만 반환한다. 설정 범위 안이면 미리 생성된 미래 회차도 포함하고, 마감이 지난 회차는 제외한다. `/notifications/summary`의 마감 임박 개수도 같은 조회 결과를 사용한다.

이 API는 알림 조회 범위와 개인 설정을 제공한다. 푸시, 이메일, SSE, WebSocket 같은 발송 채널은 별도 기능이다.

## 7. 담당자 행동 API

모든 행동은 `ASSIGNED` 회차의 현재 담당자만 호출할 수 있다.

### 7.1 실제 완료

```http
POST /api/groups/{groupId}/occurrences/{occurrenceId}/complete
Content-Type: application/json
Idempotency-Key: eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee
If-Match: "4"
```

```json
{}
```

성공:

```http
HTTP/1.1 200 OK
ETag: "5"
```

```json
{
  "outcome": "COMPLETED",
  "occurrence": {
    "occurrenceId": "33333333-3333-4333-8333-333333333333",
    "status": "COMPLETED",
    "currentAssignee": null,
    "lastAssignee": {
      "membershipId": "55555555-5555-4555-8555-555555555555",
      "displayName": "이민준",
      "avatarUrl": null,
      "status": "ACTIVE"
    },
    "attention": null,
    "closedAt": "2026-07-23T14:00:00Z",
    "version": 5
  }
}
```

효과:

- 회차 `COMPLETED`
- 현재 배정 종료 사유 `COMPLETED`
- 현재 담당자 제거
- 해당 멤버의 같은 업무 실제 완료 횟수 1 증가
- 같은 멱등 키가 반복되어도 점수는 한 번만 증가

### 7.2 담당자 행동 오류

| 상황 | 응답 |
|---|---|
| 로그인 멤버가 현재 담당자가 아님 | `403 NOT_CURRENT_ASSIGNEE` |
| 회차가 `ASSIGNED`가 아님 | `409 INVALID_OCCURRENCE_STATE` |
| 읽은 뒤 다른 요청이 상태 변경 | `409 VERSION_CONFLICT` |
| 종료 회차를 다시 완료 | `409 INVALID_OCCURRENCE_STATE` |

### 7.3 전체 완료 이력과 완료 취소

```http
GET /api/groups/{groupId}/occurrences/completed-history?mineOnly=false&choreId={choreId}
```

- `COMPLETED` 회차만 `closedAt` 내림차순으로 반환하며 `SKIPPED`는 제외한다.
- `mineOnly=true`이면 로그인 멤버가 유효하게 완료한 회차만 반환한다.
- 완료 취소된 회차는 `ASSIGNED`로 복원되므로 목록과 완료 집계에서 제외한다.
- 응답은 `groupId`, `mineOnly`, `items`, `totalCount`를 가지며 현재 페이징은 없다.

완료 취소:

```http
POST /api/groups/{groupId}/occurrences/{occurrenceId}/undo-complete
Content-Type: application/json
Idempotency-Key: 99999999-9999-4999-8999-999999999999
If-Match: "{occurrenceVersion}"

{"note": "완료 버튼을 잘못 눌렀어요."}
```

마지막 유효 완료자인 활성 멤버만 호출할 수 있다. 기존 완료 배정은 삭제하지 않고 취소 시각·취소자·선택 메모를 남긴다. 회차는 `ASSIGNED`로 바뀌며 같은 멤버에게 `COMPLETION_REOPENED` 배정을 추가한다. 성공 응답의 `outcome`은 `COMPLETION_UNDONE`이다. `SKIPPED`는 취소할 수 없다.

### 7.4 대타 요청

생성:

```http
POST /api/groups/{groupId}/occurrences/{occurrenceId}/substitute-requests
Content-Type: application/json
Idempotency-Key: aaaaaaaa-0000-4000-8000-aaaaaaaaaaaa
If-Match: "{occurrenceVersion}"

{"reason": "마감 시간에 외부 일정이 있어요."}
```

- 사유는 공백 제거 후 1~500자이며 현재 담당자만 생성할 수 있다.
- 회차 가능 멤버 스냅샷 중 현재 활성 세대의 활성 멤버에게 요청한다. 요청자와 같은 회차의 레거시 수행 불가 멤버는 빼지만, 과거 대타 수락으로 담당에서 빠진 멤버는 다음 대타 요청의 수신자로 다시 포함할 수 있다.
- 요청 중에도 원 담당자와 회차의 `ASSIGNED` 상태는 유지된다.
- 같은 활성 배정에는 진행 중 요청 하나만 허용한다.
- 다른 멤버의 진행 중 요청이 있는 회차에 새 요청을 만들면 `403 SUBSTITUTE_REQUESTED_BY_ANOTHER_MEMBER`와 `다른 사용자가 올린 대타 요청입니다`를 반환한다.
- 수신자가 없으면 `409 NO_SUBSTITUTE_CANDIDATE`다.
- 성공은 `201`, 요청 URI를 담은 `Location`, 요청 버전 `ETag`를 반환한다.

조회:

```http
GET /api/groups/{groupId}/substitute-requests?box=INBOX&status=PENDING
GET /api/groups/{groupId}/substitute-requests/{requestId}
```

`box`는 `INBOX`, `OUTBOX`, `ALL`이며 기본은 `INBOX`다. 요청 상태는 `PENDING`, `ACCEPTED`, `EXHAUSTED`, `CANCELLED`, 수신자 상태는 `PENDING`, `ACCEPTED`, `DECLINED`, `INELIGIBLE`이다. 일반 멤버는 자신이 요청자 또는 수신자인 항목만, `OWNER`는 `ALL`에서 그룹 전체를 볼 수 있다.

수락·거절:

```http
POST /api/groups/{groupId}/substitute-requests/{requestId}/accept
POST /api/groups/{groupId}/substitute-requests/{requestId}/reject
Idempotency-Key: bbbbbbbb-0000-4000-8000-bbbbbbbbbbbb
If-Match: "{requestVersion}"
```

- 첫 유효 수락자가 담당자가 된다. 원 배정은 `SUBSTITUTE_ACCEPTED`, 새 직접 배정은 `SUBSTITUTE_ACCEPTANCE`로 기록하고 다른 수신자는 `INELIGIBLE`이 된다.
- 거절은 해당 수신자만 `DECLINED`로 바꾼다. 응답 가능한 수신자가 없으면 요청은 `EXHAUSTED`가 되지만 원 담당자는 유지된다.
- 완료·현재 담당자의 탈퇴 또는 참여 제외 시 진행 중 요청은 `CANCELLED`가 된다. 일반 수신자의 탈퇴·참여 제외는 해당 수신자만 `INELIGIBLE`로 바꾼다.
- 대타 수락은 공정 랜덤 재실행이 아니라 사용자의 명시적 수락에 따른 직접 배정이다. 같은 회차의 현재 담당자만 수락자로 교체하며 이미 생성된 다른 회차와 로테이션 계획은 변경하지 않는다.

## 8. `NEEDS_ATTENTION` 재시도

```http
POST /api/groups/{groupId}/occurrences/{occurrenceId}/retry-assignment
Content-Type: application/json
Idempotency-Key: 88888888-8888-4888-8888-888888888888
If-Match: "5"
```

기존 회차 스냅샷 그대로 재시도:

```json
{
  "eligibilitySource": "OCCURRENCE_SNAPSHOT",
  "sourceChoreVersion": null
}
```

현재 업무의 가능 멤버 설정을 이 회차에 명시적으로 적용:

```json
{
  "eligibilitySource": "CURRENT_CHORE",
  "sourceChoreVersion": 7
}
```

- `CURRENT_CHORE`에서는 관리자가 확인한 업무 버전 `sourceChoreVersion`이 필수다.
- 현재 업무 버전이 달라졌으면 `409 CHORE_VERSION_CONFLICT`다.
- 새 가능 멤버 조건을 회차에 복사하고 `eligibilitySnapshot.version`을 1 증가시킨다.
- 기존 스냅샷과 변경 감사 이력은 삭제하지 않는다.
- 어느 방식을 사용해도 그 회차에 레거시 수행 불가 이력이 있는 멤버는 다시 후보가 되지 않는다.

재배정 성공:

```json
{
  "outcome": "REASSIGNED",
  "eligibilitySnapshotVersion": 2,
  "appliedChoreVersion": 7,
  "occurrence": {
    "occurrenceId": "33333333-3333-4333-8333-333333333333",
    "status": "ASSIGNED",
    "currentAssignee": {
      "membershipId": "66666666-6666-4666-8666-666666666666",
      "displayName": "박서연",
      "avatarUrl": null,
      "status": "ACTIVE"
    },
    "lastAssignee": null,
    "attention": null,
    "closedAt": null,
    "version": 6
  }
}
```

여전히 후보가 없음:

```json
{
  "outcome": "STILL_NEEDS_ATTENTION",
  "eligibilitySnapshotVersion": 2,
  "appliedChoreVersion": 7,
  "occurrence": {
    "occurrenceId": "33333333-3333-4333-8333-333333333333",
    "status": "NEEDS_ATTENTION",
    "currentAssignee": null,
    "lastAssignee": null,
    "attention": {
      "reason": "NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE",
      "since": "2026-07-23T14:05:00Z",
      "lastDecisionAt": "2026-07-23T14:20:00Z"
    },
    "closedAt": null,
    "version": 6
  }
}
```

두 결과 모두 `200 OK`이며 결정 감사 이력을 추가한다. 자동으로 제한을 완화하거나 임의 멤버에게 강제 배정하지 않는다. `NEEDS_ATTENTION`이 아닌 회차에 호출하면 `409 INVALID_OCCURRENCE_STATE`다.

## 9. 멤버별 참여 업무 일괄 변경

```http
PATCH /api/groups/{groupId}/rotation-members/{membershipId}/chore-participations
Content-Type: application/json
Idempotency-Key: aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa
```

```json
{
  "addChoreIds": ["22222222-2222-4222-8222-222222222222"],
  "removeChoreIds": ["33333333-3333-4333-8333-333333333333"],
  "applicationScope": "NEXT_OCCURRENCE",
  "expectedVersions": {
    "22222222-2222-4222-8222-222222222222": 4,
    "33333333-3333-4333-8333-333333333333": 7
  }
}
```

- `NEXT_OCCURRENCE`: 현재 회차의 스냅샷과 담당자는 유지하고 다음 회차부터 적용한다.
- `CURRENT_AND_FUTURE`: 미종료 회차의 스냅샷도 갱신한다. 제외된 멤버가 현재 담당자라면 즉시 재배정하고, 추가 후 관리 필요 회차는 배정을 다시 시도한다.
- 여러 업무는 하나의 트랜잭션에서 처리하며, 각 업무의 `expectedVersions`가 하나라도 다르면 전체 요청을 롤백한다.
- 추가되거나 재활성화된 참여자는 공정성 라운드의 맨 뒤에서 시작한다.

## 10. 멤버 탈퇴와 자동 재배정

### 10.1 요청

본인 탈퇴와 `OWNER`의 멤버 내보내기는 같은 도메인 명령을 사용한다.

```http
POST /api/groups/{groupId}/members/{membershipId}/leave
Content-Type: application/json
Idempotency-Key: 99999999-9999-4999-8999-999999999999
If-Match: "2"
```

```json
{}
```

### 10.2 성공 응답

```http
HTTP/1.1 200 OK
ETag: "3"
```

```json
{
  "member": {
    "membershipId": "55555555-5555-4555-8555-555555555555",
    "displayName": "이민준",
    "status": "LEFT",
    "leftAt": "2026-07-23T14:30:00Z",
    "version": 3
  },
  "reassignmentSummary": {
    "processedCount": 2,
    "reassignedCount": 1,
    "needsAttentionCount": 1,
    "occurrences": [
      {
        "occurrenceId": "33333333-3333-4333-8333-333333333333",
        "status": "ASSIGNED",
        "newAssignee": {
          "membershipId": "66666666-6666-4666-8666-666666666666",
          "displayName": "박서연",
          "avatarUrl": null,
          "status": "ACTIVE"
        },
        "version": 6
      },
      {
        "occurrenceId": "aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa",
        "status": "NEEDS_ATTENTION",
        "newAssignee": null,
        "version": 3
      }
    ]
  },
  "terminalOccurrencesChanged": 0
}
```

### 10.3 트랜잭션 효과

1. 멤버를 `LEFT`로 바꾸고 `leftAt`을 기록한다.
2. 그 멤버가 현재 담당자인 모든 `ASSIGNED` 회차를 조회한다.
3. 각 활성 배정을 `ASSIGNEE_LEFT_GROUP`으로 종료한다.
4. 탈퇴 멤버를 제외하고 공정 배정 엔진을 실행한다.
5. 후보가 있으면 `MEMBER_LEFT_REASSIGNMENT` 배정을 만들고 `ASSIGNED`를 유지한다.
6. 후보가 없으면 현재 담당자를 비우고 `NEEDS_ATTENTION`으로 전환한다.
7. `COMPLETED`, `SKIPPED` 회차와 과거 배정·점수는 변경하지 않는다.
8. 업무의 가능 멤버 목록에서 탈퇴 멤버를 물리 삭제하지 않는다. 후보 필터에서 `LEFT` 상태로 제외하고 과거 스냅샷은 보존한다.

멤버 상태 전환과 모든 대상 회차 재배정은 한 트랜잭션으로 처리한다. 중간에 버전 충돌이 발생하면 전체를 롤백하고 `409 VERSION_CONFLICT`를 반환한다.

이미 탈퇴한 멤버를 새 멱등 키로 다시 탈퇴시키면 `409 MEMBER_ALREADY_LEFT`다. 최초 요청과 같은 멱등 키 재전송은 최초 `200` 응답을 재생한다. 활성 멤버가 유일한 `OWNER` 본인뿐이면 탈퇴할 수 있다. 다른 활성 멤버가 남아 있는데 유일한 `OWNER`가 탈퇴하려는 경우에는 `409 LAST_OWNER_CANNOT_LEAVE`를 반환한다.

## 11. 상태 코드와 오류 코드

| HTTP | 대표 코드 | 의미 |
|---|---|---|
| `200` | - | 조회, 수정, 상태 전이 성공. 후보 없음도 유효한 `200` 결과 |
| `201` | - | 업무와 현재 회차 생성 성공 |
| `204` | - | 업무 비활성화 성공 |
| `400` | `VALIDATION_FAILED`, `INVALID_QUERY` | 본문, enum, 날짜 범위, UUID 형식 오류 |
| `401` | `UNAUTHENTICATED` | 로그인 필요 |
| `403` | `FORBIDDEN`, `NOT_CURRENT_ASSIGNEE`, `NOT_SUBSTITUTE_RECIPIENT`, `SUBSTITUTE_REQUESTED_BY_ANOTHER_MEMBER` | 역할·담당자·대타 수신자 권한 부족 또는 다른 멤버의 진행 중 대타 요청 |
| `404` | `RESOURCE_NOT_FOUND` | 그룹에 속한 리소스를 찾을 수 없음 |
| `409` | `VERSION_CONFLICT` | `If-Match` 버전이 현재 버전과 다름 |
| `409` | `CHORE_VERSION_CONFLICT` | 재시도에 지정한 현재 업무 버전이 다름 |
| `409` | `INVALID_OCCURRENCE_STATE` | 현재 상태에서 허용되지 않는 행동 |
| `409` | `INVALID_SUBSTITUTE_REQUEST_STATE` | 현재 요청·회차 상태에서 대타 응답 불가 |
| `409` | `SUBSTITUTE_REQUEST_ALREADY_EXISTS` | 같은 활성 배정에 대타 요청이 이미 존재 |
| `409` | `NO_SUBSTITUTE_CANDIDATE` | 대타 요청을 받을 활성 가능 멤버가 없음 |
| `409` | `IDEMPOTENCY_KEY_REUSED` | 같은 키를 다른 요청에 재사용 |
| `409` | `MEMBER_ALREADY_LEFT` | 이미 탈퇴한 멤버에 대한 새 탈퇴 명령 |
| `409` | `LAST_OWNER_CANNOT_LEAVE` | 다른 활성 멤버가 남은 그룹의 유일한 OWNER 탈퇴 |
| `428` | `PRECONDITION_REQUIRED` | 기존 리소스 변경 요청에 `If-Match` 누락 |

후보가 없거나 재시도 후에도 배정되지 않은 상황은 `409`가 아니다. 회차 상태와 `outcome`으로 표현한다.

## 12. 구현 불변조건 체크리스트

API 구현은 어느 진입점에서도 다음 조건을 깨뜨리면 안 된다.

1. `(choreId, periodStart)`당 회차는 최대 하나다.
2. `ASSIGNED`는 현재 담당자와 활성 배정을 정확히 하나 가진다.
3. `NEEDS_ATTENTION`, `COMPLETED`, `SKIPPED`는 현재 담당자와 활성 배정이 없다.
4. 현재 담당자는 같은 그룹의 활성 가능 멤버다.
5. 같은 회차에 레거시 수행 불가 이력이 있는 멤버는 재시도나 탈퇴 재배정에서도 다시 배정되지 않는다.
6. `COMPLETED`만 같은 업무 완료 횟수를 증가시킨다.
7. `SKIPPED`는 완료 횟수와 현재 기간 업무량을 증가시키지 않는다.
8. 담당자 변경은 기존 배정을 종료하고 새 이력을 추가하는 방식으로만 수행한다.
9. 탈퇴는 과거 완료·생략·배정 이력을 삭제하거나 다른 멤버 소유로 바꾸지 않는다.
10. 후보가 없을 때 가능 멤버 제한이나 수행 불가 기록을 자동으로 완화하지 않는다.
11. 동일 멱등 요청은 상태 전이와 점수를 한 번만 반영한다.
12. 낙관적 잠금과 데이터베이스 유일 제약으로 현재 담당자 중복과 중복 회차를 함께 막는다.
13. `COMPLETED`는 마지막 유효 완료자만 재개할 수 있고, 취소된 완료는 완료·기간 업무량 집계에서 제외한다.
14. 회차당 진행 중 대타 요청은 최대 하나이고, 요청당 수락자는 최대 한 명이다.
15. 모든 응답과 오류는 공개 UUID만 사용한다.
