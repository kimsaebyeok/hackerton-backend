# 백엔드 작업 순서 (AI 업무생산성 어드바이저)

> 담당: Backend API · 데이터 적재(raw/mart) · 가공 파이프라인(탐지 7종) · LLM 제안 · seed
> 스택: **Spring Boot 3.3.x / Java 21 / Gradle**. 설계 원본: 노션 SDS (백엔드/데이터/LLM)
> ⚠️ SDS §9 기술스택은 FastAPI로 적혀 있음 → 실제 구현은 Spring Boot. SDS는 추후 정정 필요(별도).

---

## 0. 현재 상태 & 확정 사항

- 현재 레포: 빈 IntelliJ Java 모듈 (`src/Main.java`, `.iml`만 존재 — 빌드툴 없음)
- **Phase 0에서 Spring Boot Gradle 프로젝트로 재초기화** 필요 (기존 `src/Main.java`, `hackerton-backend.iml` 제거)

### 스택 결정 (변경 원하면 알려줄 것)

| 항목 | 선택 | 비고 |
|---|---|---|
| 빌드 | Gradle (Groovy DSL) | Maven 원하면 교체 |
| Web | Spring Web (MVC) | |
| 영속 | Spring Data JPA + JdbcTemplate | 조회/적재는 JPA, 대량 insert·가공은 Jdbc |
| 마이그레이션 | Flyway | raw/mart 스키마 DDL 관리 |
| 검증 | Bean Validation (jakarta.validation) | DTO 검증 |
| API 문서 | springdoc-openapi | `/swagger-ui` → 계약 공유 |
| LLM | Spring AI - Vertex AI Gemini | structured output 지원. 직접 SDK 대안 가능 |
| DB | PostgreSQL 16 | docker compose |
| 테스트 | JUnit 5 + Testcontainers | 탐지기 단위테스트 + 통합 |
| 런타임 | Java 21 (record, pattern matching, virtual threads) | `spring.threads.virtual.enabled=true` 옵션 |

---

## 1. 패키지 구조 (레이어드 아키텍처)

레이어(controller / service / repository / domain / dto / config / exception) 단위로 구성한다.
파이프라인·LLM 등 하위 도메인 로직은 `service` 아래에 둔다.

```
com.lawcompany.advisor
├─ AdvisorApplication.java
├─ config/                 // ApiExceptionHandler, LlmProperties, (CORS/OpenAPI 등)
├─ controller/             // 웹 계층 (계약 A/B)
│  ├─ IngestController          // POST /events                    [구현됨]
│  ├─ AnalysisController        // POST /analysis/run              [예정]
│  └─ QueryController           // GET timeline/patterns/suggestions [예정]
├─ service/                // 비즈니스 계층
│  ├─ IngestService             // 검증 + attributes 추출           [구현됨]
│  ├─ AnalysisService           // pipeline → scoring → suggestion  [예정]
│  ├─ SuggestionService         // A군 LLM + B군 템플릿              [예정]
│  ├─ pipeline/                 // 전처리·재구성·탐지기 7종·스코어링   [예정]
│  ├─ llm/                      // LlmClient + OpenAiLlmClient       [구현됨]
│  └─ seed/                     // SeedGenerator (7종 시나리오)       [예정]
├─ repository/             // 영속 계층
│  ├─ EventIngestRepository     // raw.events 멱등 적재 (Jdbc)      [구현됨]
│  ├─ PatternRepository         // mart.detected_patterns          [예정]
│  └─ SuggestionRepository      // mart.llm_suggestions            [예정]
├─ domain/                 // 도메인/영속 모델
│  ├─ EventRow                  // 적재 행                          [구현됨]
│  ├─ DetectedPattern           // 패턴                            [예정]
│  └─ LlmSuggestion             // 제안                            [예정]
├─ dto/                    // 요청/응답 DTO
│  ├─ EventBatchRequest, EventDto, IngestResponse                  [구현됨]
│  └─ (조회 응답 DTO …)                                            [예정]
└─ exception/              // IngestValidationException, LlmException [구현됨]
```

---

## 2. 작업 순서 (의존성 기준)

각 Phase는 **완료 기준(DoD)** 을 통과해야 다음으로. 계약 A/B는 Phase 2·6에서 확정되며, 이게 Extension팀·대시보드팀의 블로커 해제 조건이므로 **최우선**.

### Phase 0 — 프로젝트 부트스트랩
- [ ] 기존 `src/Main.java`, `hackerton-backend.iml` 제거
- [ ] Spring Initializr 구조로 재생성 (Boot 3.3.x, Java 21, Gradle): `src/main/java`, `src/main/resources`, `src/test/java`
- [ ] 의존성: web, data-jpa, postgresql, flyway, validation, springdoc, spring-ai-vertex-ai-gemini, (test) testcontainers
- [ ] `application.yml` 프로파일: `local` / `test` / `seed`
- [ ] `docker-compose.yml`: postgres:16 (앱은 `./gradlew bootRun`)
- **DoD**: `./gradlew bootRun` 기동 + `/actuator/health` 200

### Phase 1 — DB 스키마 & 마이그레이션
- [ ] `V1__init.sql`: `CREATE SCHEMA raw; CREATE SCHEMA mart;` + 3개 테이블 + 인덱스 (SDS §4 그대로)
- [ ] JPA 엔티티: `EventEntity`(raw.events), `DetectedPattern`(mart), `SuggestionEntity`(mart) — `@Table(schema=...)`
- [ ] Flyway 부팅 시 자동 마이그레이션
- **DoD**: 부팅 시 3개 테이블·인덱스 생성 확인

### Phase 2 — Ingest API (계약 A) 🔑
- [ ] DTO record + Bean Validation, `event_type` enum(11종)
- [ ] `POST /api/v1/events`: 배치 검증 → JdbcTemplate `batchUpdate` `INSERT ... ON CONFLICT (event_id) DO NOTHING` (멱등)
- [ ] `X-User-Id` 헤더 처리
- [ ] springdoc `/swagger-ui` 노출
- **DoD**: curl 배치 적재 성공 + 동일 배치 재전송해도 중복 없음(멱등) + 스웨거 계약 공유

### Phase 3 — seed 데이터 🔑
- [ ] `SeedGenerator`: 탐지기 7종이 각각 최소 1회 발화하도록 이벤트 생성 (① 3노드 순환 ② 버튼 시퀀스 ③ copy 체인 ④ 폼 반복 ⑤ typed 진입 ⑥ 폴링 ⑦ 탭 thrashing)
- [ ] `@Profile("seed")` CommandLineRunner 또는 `POST /api/v1/dev/seed` 로 ingest 경로 재사용 적재
- **DoD**: seed 실행 → raw.events에 시나리오별 이벤트 적재, 시나리오당 카운트 확인
- ※ 이후 모든 가공/LLM 작업의 테스트·캘리브레이션·데모 데이터원

### Phase 4 — 가공 파이프라인 (탐지기 7종)
순서 주의: **`[raw]` 탐지기(②③⑤)는 visit 재구성 없이 동작** → 먼저 구현해 진도 확보.
- [ ] `pipeline/model`: AnalysisInput(이벤트 리스트), Visit, Activity 정의
- [ ] **②③⑤ 먼저** (raw 직접 소비): click_sequence, copy_chain, manual_entry
- [ ] Step 0 전처리 + Step 1 재구성(tab→page 맵, visit, 활성 dwell)
- [ ] **①④⑥⑦** (visit 소비): cycle, form_repetition, polling_revisit, tab_thrashing
- [ ] Step 3 스코어링(빈도×dwell×규칙성) + 도메인 카테고리 감점 + 군별 쿼터(A3·B2) 컷
- [ ] `Detector` 인터페이스 통일: `List<DetectedPattern> detect(AnalysisInput)` — **순수 함수, Spring 무의존** → JUnit 단독 테스트
- [ ] 탐지 결과 mart.detected_patterns 적재
- **DoD**: seed 입력으로 7종 전부 발화(유닛테스트 그린) + 임계치 캘리브레이션

### Phase 5 — 제안 생성 (LLM + 템플릿)
- [ ] **A군 → Gemini**: PromptBuilder(증거 동봉 + 패턴유형별 approach 카탈로그 + est_minutes 주입), structured output → record 매핑, 패턴별 개별 호출(최대 3, 병렬)
- [ ] **B군 → 룰 템플릿**(LLM 미호출): manual_entry/tab_thrashing/polling_revisit 처방 고정, `model='rule_template'`
- [ ] mart.llm_suggestions 적재, `prompt_version` 기록
- [ ] seed 기반 프롬프트 회귀 테스트
- **DoD**: 패턴→제안 적재(A는 LLM·B는 템플릿), seed 회귀 통과

### Phase 6 — 조회 API (계약 B) + 오케스트레이션 🔑
- [ ] `GET /timeline /patterns /suggestions` (빈 데이터 `[]`)
- [ ] `POST /analysis/run`: 가공→스코어링→제안 묶어 실행, 결과 카운트 반환
- [ ] CORS(대시보드 origin) 허용
- **DoD**: seed → analysis/run → 조회 API E2E 정상, 대시보드팀 연동

### 공통(가로지르는 작업)
- [ ] `@RestControllerAdvice` 전역 예외 처리(검증 400, 서버 500)
- [ ] Vertex 인증 정보·DB 접속을 env/프로파일로 분리(시크릿 커밋 금지)
- [ ] 요청 로깅(ingest 배치 크기, analysis 소요)

---

## 3. 병렬화 가능 구간

- Phase 2(ingest) 완료 직후 → Extension팀은 곧장 연동 가능 (이후 백엔드는 4·5 진행)
- Phase 3(seed) 완료 후 → Phase 4·5는 Extension 진도와 무관하게 끝까지 개발 가능
- Phase 4 내부: ②③⑤(raw)와 ①④⑥⑦(visit)은 model 정의 후 분리 작업 가능

## 4. 외부 의존 / 리스크

- **Vertex AI Gemini 자격증명**: GCP 프로젝트·서비스계정 키 필요 → Phase 5 전에 확보 (DE팀 기존 Vertex 사용분 재활용 가능 여부 확인)
- **Sheets copy/paste 감지**: Extension팀 첫날 검증 결과에 따라 탐지기 ③ 폴백 여부 결정 (백엔드는 폴백 로직만 대비)

---

## 5. 다음 액션

1. Phase 0 부트스트랩 (기존 모듈 정리 → Spring Boot Gradle 재생성) — 지금 바로 가능
2. Phase 1 스키마 + Phase 2 ingest 까지를 1차 목표로 (계약 A 확정·공유)
