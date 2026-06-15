package com.lawcompany.advisor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

@Repository
public class EfficiencyAgendaRepository {

    private static final String INSERT_SQL = """
            INSERT INTO efficiency_agenda
                (agenda_id, report_id, archetype, title, one_liner,
                 automation_score, est_saving_min_day, priority_score, evidence, agent_spec_draft)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
            """;

    private final JdbcTemplate jdbc;

    public EfficiencyAgendaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID agendaId, UUID reportId, String archetype, String title, String oneLiner,
                       Double automationScore, Integer estSavingMinDay, Double priorityScore,
                       String evidenceJson, String agentSpecJson) {
        jdbc.update(INSERT_SQL, (PreparedStatement ps) -> {
            ps.setObject(1, agendaId);
            ps.setObject(2, reportId);
            ps.setString(3, archetype);
            ps.setString(4, title);
            ps.setString(5, oneLiner);
            setDoubleOrNull(ps, 6, automationScore);
            setIntOrNull(ps, 7, estSavingMinDay);
            setDoubleOrNull(ps, 8, priorityScore);
            ps.setString(9, evidenceJson);          // NOT NULL → 호출부에서 최소 "{}" 보장
            ps.setString(10, agentSpecJson);
        });
    }

    private static void setDoubleOrNull(PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, Types.NUMERIC); else ps.setDouble(i, v);
    }

    private static void setIntOrNull(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }
}
