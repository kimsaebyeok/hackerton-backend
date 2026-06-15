package com.lawcompany.advisor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * agent_draft 적재. 유저 보정 답변(Q&A) + LLM 산출(명세/컨텍스트/실행프롬프트)을 저장한다.
 */
@Repository
public class AgentDraftRepository {

    private static final String INSERT_SQL = """
            INSERT INTO agent_draft
                (draft_id, agenda_id, user_context, spec_markdown, context_markdown, execution_prompt)
            VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public AgentDraftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID draftId, UUID agendaId, String userContextJson,
                       String specMarkdown, String contextMarkdown, String executionPrompt) {
        jdbc.update(INSERT_SQL, ps -> {
            ps.setObject(1, draftId);
            ps.setObject(2, agendaId);
            ps.setString(3, userContextJson);
            ps.setString(4, specMarkdown);
            ps.setString(5, contextMarkdown);
            ps.setString(6, executionPrompt);
        });
    }
}
