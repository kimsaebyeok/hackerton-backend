# 분석 규칙 정의안 (검토용)

> schema.txt + sample-data.html 기준. 비어 있던 L2~L4 알고리즘을 제안한다.
> 각 항목은 샘플 세션(`a1…0001`, 18이벤트)의 실제 값으로 역검증: ✅ 일치 / ≈ 근사(튜닝 필요) / ⚠ 미일치(확인 필요).
> 결정 반영: 분석 트리거=FE 세션종료 API / 카드 생성=전부 LLM / 지표 배열=전수 저장.

---

## 0. 오케스트레이션 (세션 생명주기)

- **세션 생성**: FE가 수집 토글 ON 시 `session_id` 생성. 백엔드는 `POST /api/v1/sessions`(또는 첫 이벤트 lazy upsert)로 `work_session` 행 생성, `status='collecting'`, `started_at`=최초 이벤트 ts.
- **이벤트 적재**: `POST /api/v1/events` (단건). `session_id` FK 필요 → 세션 행 선존재 전제.
- **세션 종료 + 분석**: FE 토글 OFF → `POST /api/v1/sessions/{id}/close`
  1. `ended_at`, `active_duration_ms`(=Σdwell), `event_count`(=행수) 채움
  2. L1→L2→L3 계산해 `analysis_report`(metrics/tasks JSONB) 적재
  3. L4 LLM 호출해 `efficiency_agenda` 적재
  4. `status='analyzed'`
- 집계 컬럼(`active_duration_ms`, `event_count`)은 **close 시점에 계산**(ingest 증분 아님).

---

## 1. L1 — Enrichment

- `domainCategory` = `domain_category_map`에서 `domain` 정확 매칭, 없으면 `unknown`.
- ⚠ 매칭 규칙 확인 필요: 정확 매칭만인지, 서브도메인 suffix 매칭(`*.lawcompany.co.kr`)도 허용인지. → **제안: 정확 매칭 우선, 미스 시 등록된 패턴의 suffix 매칭, 그래도 없으면 unknown.**

## 2. L2 — Session / Task / Step

### ActionStep (연속 동일 도메인 압축)
- 같은 `(tabId, domain, safeUrl)` 연속 구간 = 1 step. `durationMs`=구간 내 dwell 합, `clickCount`=click 수, `topElements`=click의 `(label,tag,role)` 빈도 집계.

### Task 경계 — **제안**
- 한 세션 안에서 **왕복 사이클(A↔B 상호 전환)이 성립하는 연속 구간** = 1 Task.
- 사이클에 끼지 않는 도메인(샘플의 mail)은 Task `steps`에서 제외되고, 패턴을 끊으면 Task 분할.
- 샘플: admin↔sheets 3왕복이 1 Task, mail은 제외 ✅.

### signature 정규화 — ✅ 일치
- `domainSequence`를 카테고리로 치환 → 연속 중복 압축 → **최소 반복 단위**로 축약 → `>`로 조인.
- 샘플: admin,sheets×3 → `admin_internal>spreadsheet` ✅.

### Task.steps — ✅ 일치 (대표 1주기)
- `steps`는 **반복의 첫 1주기 대표 step**(전체 6개 아님). 샘플 s1 `durationMs:45000`(첫 admin dwell), `clickCount:1` = 첫 주기 값 ✅.
- `domainSequence`는 전체(6개), `steps`는 대표 주기(2개)로 입자가 다름.

## 3. L3 — MetricSet (전수 저장)

| 지표 | 제안 정의 | 샘플 검증 |
|---|---|---|
| domainVisits | 도메인별 `navigation_completed`+`tab_activated` 수 | admin4/sheets3/mail1 ✅ |
| pageRevisits | `safeUrl`별 arrival 수 (전수) | admin/cases/list=4 ✅ (sheets도 3 포함 저장) |
| repeatedClicks | `(domain,label,tag,role)` count, **≥2만** | sheets 셀입력=2 ✅ |
| timeByDomain | 도메인별 dwell 합 | sheets153k/admin113k/mail90k ✅ |
| domainConcentration | `timeByDomain ÷ totalActiveMs` | 0.43/0.317/0.253 ✅ |
| timeOfDayHist[24] | **KST(+09)** 시간대별 이벤트 수 | idx10=18 ✅ |
| totalActiveMs | Σ dwell | 356000 ✅ |
| toolSwitchCount | 연속 도메인 변경 수 | 7 ✅ |
| transitionMatrix | 연속 도메인 변경 `from→to` 집계 | 합7 ✅ |
| repeatedCycles | A↔B 상호전환 왕복; occurrences=왕복수 | occ3, total266k ✅ |
| topNgrams | **카테고리** 시퀀스 n-gram 빈도 | `[admin_internal,spreadsheet]`×3 ✅ |
| multitaskingIndex | `toolSwitchCount ÷ (이벤트 span 분)` | 7/6.43=1.09 ≈ 1.08 ✅ |
| **pingPongScore** | 왕복(상호 보유) 전환 / 전체 전환 | 5/7=0.71 ≈ 0.78 ⚠ |
| **switchingEntropy** | 전이분포 정규화 섀넌 엔트로피 | 내 계산 0.92 ≠ 0.34 ⚠ |
| **estimatedSavingMsPerDay** | repeatedCycles.totalMs × 일일반복계수 | 266k×? = 1080k ⚠ (계수≈4) |

⚠ 3개는 샘플 값과 안 맞음 → **너의 산식 확인 필요**:
- `pingPongScore`: 0.78 산출식이 "왕복전환/전체"가 아니라 다른 가중(예: 왕복 체류시간 비중 266k/356k=0.75)일 수 있음.
- `switchingEntropy`: 0.34는 낮은 값(=집중) → 어떤 분포/정규화 기준인지.
- `estimatedSaving`: 18분/일의 "일일 반복계수" 근거(관측 1세션 → 하루 환산 가정).

## 4. L4 — EfficiencyAgenda

### 역할 분담 (결정: 카드 전부 LLM)
- **코드(결정값)**: `evidence.metrics`·`sourceTaskIds`·`timeWindow`는 L3에서 그대로. 숫자 점수(`automation_score`,`priority_score`,`est_saving_min_day`)도 **결정적 산식 권장**.
- **LLM(structured output)**: `archetype` 판정, `title`, `one_liner`, `agent_spec_draft`(goal/inputsObserved/missingContext).
- ⚠ 확인: "카드 전부 LLM"에 **점수까지 LLM이 매기길** 원하는지, 아니면 점수는 산식·서술만 LLM인지. **제안: 점수=산식(재현성), 서술·archetype·missingContext=LLM.**

### archetype 판정 — **제안** (우선순위 순)
1. `data_transfer` — admin_internal/editor ↔ spreadsheet 왕복 사이클(repeatedCycles 존재). ← 샘플
2. `form_entry` — 한 도메인에서 input성(role=textbox/입력) repeatedClicks 높음
3. `monitoring` — 동일 safeUrl pageRevisits 높고 평균 dwell 짧음(폴링)
4. `research_collect` — search/research_source 다수 방문 → editor 유입
5. `quick_win_shortcut` — 위에 안 걸리는 높은 toolSwitch/탭 헤맴
- LLM에 지표 요약 + 이 판정 가이드를 주고 **structured output으로 archetype 확정** + 서술 생성.

### 점수 산식 — **제안**
- `automationScore` = w1·규칙성(signature 반복) + w2·occurrences정규화 + w3·archetype자동화적합도. (샘플 0.86 목표로 가중 튜닝)
- `priorityScore` = automationScore × impact(est_saving 정규화) × confidence.
- `confidence` = 관측량 기반(세션수·occurrences·days). 샘플은 1세션이라 낮아야 정상.

---

## 검토 요청 항목 (네가 정해줘야 확정)

1. ⚠ `pingPongScore` / `switchingEntropy` / `estimatedSaving`의 정확한 산식 (위 3개 미일치)
2. 점수(automation/priority/est_saving)를 **산식 vs LLM** 어느 쪽이 매길지
3. `domain_category_map` 매칭: 정확매칭만 vs suffix 패턴 허용
4. 세션 생성: 명시 API(`POST /sessions`) vs 첫 이벤트 lazy upsert
