package com.lawcompany.advisor.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawcompany.advisor.dto.AgentDraftView;
import com.lawcompany.advisor.dto.EfficiencyAgendaView;
import com.lawcompany.advisor.dto.GeneratedQuestion;
import com.lawcompany.advisor.dto.QaPair;
import com.lawcompany.advisor.dto.QuestionsResponse;
import com.lawcompany.advisor.repository.AgentDraftRepository;
import com.lawcompany.advisor.repository.EfficiencyAgendaRepository;
import com.lawcompany.advisor.service.llm.LlmClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * 어드바이징 — 분석 테이블(efficiency_agenda)을 읽어
 *  (1) 추가 질문 생성, (2) 유저 답변 기반 agent_draft 생성.
 */
@Service
public class AdvisingService {

    private static final String QUESTION_SYSTEM = """
            너는 업무 자동화 어드바이저다. 아래 '자동화 추천 아젠다'와 근거 지표를 보고,
            이 업무를 실제로 자동화/에이전트화하려면 사용자에게 추가로 물어봐야 할 질문들을 생성하라.
            관측 데이터만으로는 알 수 없는 입력값·매핑·규칙·일정·예외처리 등을 질문화한다.
            반드시 JSON 객체로만 응답: {"questions":[{"field":"","question":"","why":""}]}. 한국어로 작성.
            """;

    private static final String DRAFT_SYSTEM = """
            너는 업무 자동화 어드바이저다. 아래 추천 아젠다와 사용자의 보정 답변(Q&A)을 바탕으로
            자동화 에이전트 초안을 작성하라. 반드시 JSON 객체로만 응답:
            {"specMarkdown":"","contextMarkdown":"","executionPrompt":""}.
            - specMarkdown: 사람이 읽는 에이전트 명세서(마크다운)
            - contextMarkdown: 업무 맥락 컨텍스트 파일(마크다운)
            - executionPrompt: AI에 그대로 복붙해 실행할 프롬프트
            한국어로 작성.
            """;

    private final EfficiencyAgendaRepository agendaRepository;
    private final AgentDraftRepository draftRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public AdvisingService(EfficiencyAgendaRepository agendaRepository,
                           AgentDraftRepository draftRepository,
                           LlmClient llmClient,
                           ObjectMapper objectMapper) {
        this.agendaRepository = agendaRepository;
        this.draftRepository = draftRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /** API #1 — 아젠다 기반 추가 질문 생성(저장하지 않고 반환). */
    public QuestionsResponse generateQuestions(UUID agendaId) {
        EfficiencyAgendaView agenda = loadAgenda(agendaId);
        QuestionList out = llmClient.completeAsJson(QUESTION_SYSTEM, agendaContext(agenda), QuestionList.class);
        List<GeneratedQuestion> questions = out.questions() == null ? List.of() : out.questions();
        return new QuestionsResponse(agendaId, questions);
    }

    /** API #2 — 질문/답변 쌍으로 agent_draft 생성·저장. */
    public AgentDraftView generateDraft(UUID agendaId, List<QaPair> answers) {
        EfficiencyAgendaView agenda = loadAgenda(agendaId);

        String userPrompt = agendaContext(agenda) + "\n\n[사용자 보정 답변]\n" + toJson(answers);
        DraftContent out = llmClient.completeAsJson(DRAFT_SYSTEM, userPrompt, DraftContent.class);

        UUID draftId = UUID.randomUUID();
        draftRepository.insert(draftId, agendaId, toJson(answers),
                out.specMarkdown(), out.contextMarkdown(), out.executionPrompt());

        return new AgentDraftView(draftId, agendaId,
                out.specMarkdown(), out.contextMarkdown(), out.executionPrompt());
    }

    private EfficiencyAgendaView loadAgenda(UUID agendaId) {
        return agendaRepository.findById(agendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "agenda not found: " + agendaId));
    }

    private String agendaContext(EfficiencyAgendaView a) {
        return """
                [추천 아젠다]
                archetype: %s
                title: %s
                oneLiner: %s
                automationScore: %s
                estSavingMinDay: %s
                priorityScore: %s
                evidence: %s
                agentSpecDraft: %s
                """.formatted(
                a.archetype(), a.title(), a.oneLiner(),
                a.automationScore(), a.estSavingMinDay(), a.priorityScore(),
                a.evidenceJson(), a.agentSpecDraftJson());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    // ── LLM 출력 매핑 ──
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QuestionList(List<GeneratedQuestion> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DraftContent(String specMarkdown, String contextMarkdown, String executionPrompt) {}
}
