package com.lawcompany.advisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawcompany.advisor.domain.EventRecord;
import com.lawcompany.advisor.dto.AgendaCard;
import com.lawcompany.advisor.repository.AnalysisReportRepository;
import com.lawcompany.advisor.repository.EfficiencyAgendaRepository;
import com.lawcompany.advisor.repository.WorkLogEventRepository;
import com.lawcompany.advisor.repository.WorkSessionRepository;
import com.lawcompany.advisor.service.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 세션 종료 후 분석: 지표 계산 → analysis_report 저장 → LLM 추천 카드 → efficiency_agenda 저장 → status=analyzed.
 * 카드(archetype/title/oneLiner/점수/agentSpecDraft)는 전부 LLM(structured output)이 생성.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private static final String SYSTEM_PROMPT = """
            너는 업무 자동화 어드바이저다. 아래 브라우저 사용 지표(JSON)를 보고
            가장 자동화/효율화 가치가 큰 업무 1건을 추천 카드로 만들어라.
            반드시 JSON 객체로만 응답하고, 키는 정확히 다음을 사용한다:
              archetype, title, oneLiner, automationScore, estSavingMinDay, priorityScore, evidence, agentSpecDraft
            - archetype 은 data_transfer | monitoring | form_entry | research_collect | quick_win_shortcut 중 하나
            - automationScore, priorityScore 는 0~1 실수, estSavingMinDay 는 정수(분/일)
            - evidence 는 {metrics:[{label,value,unit}], sourceNote} 형태의 객체
            - agentSpecDraft 는 {goal, inputsObserved, missingContext:[{field,question,why}]} 형태의 객체
            - 관측된 행동 메타데이터만 근거로 하고, 모르는 입력값은 missingContext 로 질문화하라
            - 모든 서술은 한국어로 작성한다
            """;

    private final WorkLogEventRepository eventRepository;
    private final AnalysisReportRepository reportRepository;
    private final EfficiencyAgendaRepository agendaRepository;
    private final WorkSessionRepository sessionRepository;
    private final MetricsCalculator metricsCalculator;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public AnalysisService(WorkLogEventRepository eventRepository,
                           AnalysisReportRepository reportRepository,
                           EfficiencyAgendaRepository agendaRepository,
                           WorkSessionRepository sessionRepository,
                           MetricsCalculator metricsCalculator,
                           LlmClient llmClient,
                           ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.reportRepository = reportRepository;
        this.agendaRepository = agendaRepository;
        this.sessionRepository = sessionRepository;
        this.metricsCalculator = metricsCalculator;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 지표 계산 → analysis_report 저장(항상) → status=analyzed → LLM 추천 카드(best-effort).
     * 지표 리포트는 LLM 성공 여부와 무관하게 항상 DB에 남는다(각 update 독립 커밋).
     * LLM 실패(잔액부족/오류)는 카드만 건너뛰고 리포트·status 는 유지.
     */
    public void analyze(UUID sessionId) {
        List<EventRecord> events = eventRepository.findBySession(sessionId);
        if (events.isEmpty()) {
            log.info("이벤트 없음 — 분석 건너뜀 session={}", sessionId);
            return;
        }

        Map<String, Object> metrics = metricsCalculator.compute(events);
        String metricsJson = writeJson(metrics);

        // 1) 지표 리포트는 항상 저장
        UUID reportId = UUID.randomUUID();
        reportRepository.insert(reportId, sessionId, metricsJson, null);
        sessionRepository.markAnalyzed(sessionId);
        log.info("분석 리포트 저장 session={} report={}", sessionId, reportId);

        // 2) LLM 추천 카드는 best-effort
        try {
            AgendaCard card = llmClient.completeAsJson(SYSTEM_PROMPT, metricsJson, AgendaCard.class);
            UUID agendaId = UUID.randomUUID();
            agendaRepository.insert(
                    agendaId, reportId,
                    card.archetype(), card.title(), card.oneLiner(),
                    card.automationScore(), card.estSavingMinDay(), card.priorityScore(),
                    jsonOrEmpty(card.evidence()), jsonOrEmpty(card.agentSpecDraft())
            );
            log.info("추천 카드 저장 session={} agenda={} archetype={}", sessionId, agendaId, card.archetype());
        } catch (Exception e) {
            log.warn("LLM 카드 생성 실패 — 분석 리포트는 저장됨, session={}, err={}", sessionId, e.toString());
        }
    }

    private String jsonOrEmpty(JsonNode node) {
        return (node == null || node.isNull()) ? "{}" : node.toString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("metrics 직렬화 실패", e);
        }
    }
}
