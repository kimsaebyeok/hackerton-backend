package com.lawcompany.advisor.repository;

import com.lawcompany.advisor.dto.EfficiencyAgendaView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * efficiency_agenda 는 다른 애플리케이션이 채운다. 우리는 어드바이징을 위해 읽기만 한다.
 */
@Repository
public class EfficiencyAgendaRepository {

    private static final String SELECT_SQL = """
            SELECT agenda_id, report_id, archetype, title, one_liner,
                   automation_score, est_saving_min_day, priority_score,
                   evidence::text         AS evidence,
                   agent_spec_draft::text AS agent_spec_draft
            FROM efficiency_agenda
            WHERE agenda_id = ?
            """;

    private static final RowMapper<EfficiencyAgendaView> MAPPER = (rs, i) -> new EfficiencyAgendaView(
            rs.getObject("agenda_id", UUID.class),
            rs.getObject("report_id", UUID.class),
            rs.getString("archetype"),
            rs.getString("title"),
            rs.getString("one_liner"),
            toDouble(rs.getBigDecimal("automation_score")),
            (Integer) rs.getObject("est_saving_min_day"),
            toDouble(rs.getBigDecimal("priority_score")),
            rs.getString("evidence"),
            rs.getString("agent_spec_draft")
    );

    private final JdbcTemplate jdbc;

    public EfficiencyAgendaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EfficiencyAgendaView> findById(UUID agendaId) {
        return jdbc.query(SELECT_SQL, MAPPER, agendaId).stream().findFirst();
    }

    private static Double toDouble(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }
}
